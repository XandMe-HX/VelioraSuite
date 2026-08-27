package id.velioragardens.veliorasuite.module.security;

import id.velioragardens.veliorasuite.module.security.model.SecurityDecision;
import org.bukkit.entity.Player;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;

/**
 * Lightweight, privacy-first connection anomaly guard. A shared network alone is
 * never proof of abuse: schools, families, mobile carriers and public Wi-Fi can
 * legitimately place many players behind one address.
 */
public final class VelioraGuardNetworkManager {
    private final SecurityConfigManager config;
    private final Map<String, Queue<Long>> joinsByNetwork = new HashMap<>();
    private final Map<String, Map<String, Long>> newNamesByNetwork = new HashMap<>();

    public VelioraGuardNetworkManager(SecurityConfigManager config) {
        this.config = config;
    }

    public SecurityDecision check(Player player) {
        if (player == null || !config.isNetworkGuardEnabled() || config.hasBypass(player)) return SecurityDecision.allow();
        long now = System.currentTimeMillis();
        boolean bedrock = isFloodgatePlayer(player);
        String network = networkHash(player);
        int joinCount = recordJoin(network, now);
        int newNames = recordName(network, player.getName(), now);
        int joinLimit = bedrock ? config.getNetworkGuardBedrockJoinLimit() : config.getNetworkGuardJavaJoinLimit();
        int accountLimit = bedrock ? config.getNetworkGuardBedrockNewAccountLimit() : config.getNetworkGuardJavaNewAccountLimit();

        if (joinCount > joinLimit) {
            return new SecurityDecision(true, true, "NETWORK_BURST", player.getName(), 80,
                    "Lonjakan koneksi singkat dari jaringan yang sama", "KICK_TEMPORARY", "network-burst-kick",
                    config.getNetworkGuardBurstKickMessage());
        }
        if (newNames > accountLimit) {
            return new SecurityDecision(false, true, "NETWORK_NEW_ACCOUNTS", player.getName(), 45,
                    "Banyak akun baru dari jaringan yang sama", "AUDIT_ONLY", "", "");
        }
        return SecurityDecision.allow();
    }

    public void clear() {
        joinsByNetwork.clear();
        newNamesByNetwork.clear();
    }

    private int recordJoin(String network, long now) {
        Queue<Long> queue = joinsByNetwork.computeIfAbsent(network, key -> new ArrayDeque<>());
        purge(queue, now, config.getNetworkGuardWindowSeconds());
        queue.add(now);
        return queue.size();
    }

    private int recordName(String network, String name, long now) {
        Map<String, Long> names = newNamesByNetwork.computeIfAbsent(network, key -> new HashMap<>());
        long minimum = now - config.getNetworkGuardAccountWindowSeconds() * 1000L;
        names.entrySet().removeIf(entry -> entry.getValue() < minimum);
        names.put(name.toLowerCase(Locale.ROOT), now);
        return names.size();
    }

    private static void purge(Queue<Long> queue, long now, int seconds) {
        long minimum = now - seconds * 1000L;
        while (!queue.isEmpty() && queue.peek() < minimum) queue.poll();
    }

    private String networkHash(Player player) {
        InetSocketAddress address = player.getAddress();
        String ip = address == null || address.getAddress() == null ? "unknown" : address.getAddress().getHostAddress();
        String masked = mask(ip);
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest((masked + ":VelioraNetwork").getBytes(StandardCharsets.UTF_8));
            return String.format("net-%02x%02x%02x%02x", bytes[0], bytes[1], bytes[2], bytes[3]);
        } catch (Exception ignored) {
            return "net-" + Integer.toHexString(masked.hashCode());
        }
    }

    private static String mask(String ip) {
        if (ip == null || ip.isBlank() || "unknown".equals(ip)) return "unknown";
        String[] v4 = ip.split("\\.");
        if (v4.length == 4) return v4[0] + "." + v4[1] + "." + v4[2] + ".0/24";
        String[] v6 = ip.split(":");
        return String.join(":", java.util.Arrays.copyOf(v6, Math.min(4, v6.length))) + "::/64";
    }

    private boolean isFloodgatePlayer(Player player) {
        try {
            Class<?> type = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Object api = type.getMethod("getInstance").invoke(null);
            Object result = type.getMethod("isFloodgatePlayer", java.util.UUID.class).invoke(api, player.getUniqueId());
            return result instanceof Boolean value && value;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }
}
