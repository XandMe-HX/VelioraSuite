package id.velioragardens.veliorasuite.module.security;

import id.velioragardens.veliorasuite.module.security.model.SecurityDecision;
import org.bukkit.entity.Player;

import java.net.InetSocketAddress;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class SecurityJoinProtectionManager {

    private final SecurityConfigManager configManager;
    private final SecurityRiskManager riskManager;
    private final Map<String, Queue<Long>> joinsByIp = new HashMap<>();
    private final Map<String, Map<String, Long>> namesByIp = new HashMap<>();
    private final Map<String, Queue<Long>> rejoinsByIdentity = new HashMap<>();
    private final Map<String, Long> temporaryHolds = new HashMap<>();

    public SecurityJoinProtectionManager(SecurityConfigManager configManager, SecurityRiskManager riskManager) {
        this.configManager = configManager;
        this.riskManager = riskManager;
    }

    public SecurityDecision check(Player player) {
        if (!configManager.isEnabled() || player == null || configManager.hasBypass(player)) return SecurityDecision.allow();
        String ipHash = ipHash(player);
        long now = System.currentTimeMillis();

        if (isTemporarilyHeld(ipHash, now)) {
            return new SecurityDecision(true, true, "JOIN_RISK", player.getName(), configManager.getRiskTemporaryBlockThreshold(), "IP hash masih dalam cooldown", "TEMPORARY_DENY", "suspicious-join-kick", "%prefix% &cTerlalu banyak akun berbeda dari koneksi yang sama. Coba lagi sebentar.");
        }

        boolean invalidName = configManager.isNameProtectionEnabled() && !isValidName(player.getName());
        if (invalidName) {
            int risk = riskManager.scoreJoinRisk(player, 0, 0, 0, true);
            return new SecurityDecision(true, true, "INVALID_NAME", player.getName(), risk, "Nama player tidak sesuai aturan", "KICK_TEMPORARY", "name-kick", configManager.getNameKickMessage());
        }

        int joinCount = recordJoin(ipHash, now);
        int uniqueNames = recordName(ipHash, player.getName(), now);
        int rejoinCount = recordRejoin(ipHash, player.getName(), now);
        int risk = riskManager.scoreJoinRisk(player, uniqueNames, rejoinCount, joinCount, false);
        String action = riskManager.actionForRisk(risk);

        if (configManager.isIdentityProtectionEnabled() && configManager.isSameIpDifferentNamesEnabled()) {
            if (uniqueNames >= configManager.getDifferentNamesKickThreshold() && risk >= configManager.getRiskKickThreshold() && configManager.isDifferentNamesKickAction()) {
                temporaryHolds.put(ipHash, now + (configManager.getDifferentNamesTemporaryBlockSeconds() * 1000L));
                return new SecurityDecision(true, true, "JOIN_RISK", player.getName(), risk, "Banyak akun berbeda dari IP hash yang sama", action, "suspicious-join-kick", "%prefix% &cTerlalu banyak akun berbeda dari koneksi yang sama. Coba lagi sebentar.");
            }
            if (uniqueNames >= configManager.getDifferentNamesAlertThreshold() && configManager.isDifferentNamesAlertAction()) {
                return new SecurityDecision(false, true, "JOIN_RISK", player.getName(), risk, "Banyak akun berbeda dari IP hash yang sama", "ALERT_ONLY", "", "");
            }
        }

        if (configManager.isIdentityProtectionEnabled() && configManager.isSameNameRejoinEnabled()) {
            if (rejoinCount >= configManager.getRejoinDelayThreshold() && configManager.isRejoinKickAction()) {
                return new SecurityDecision(true, true, "REJOIN_SPAM", player.getName(), Math.max(risk, 15), "Player sering reconnect, kemungkinan koneksi lag", "LIGHT_DELAY", "rejoin-delay-kick", "%prefix% &eKoneksi kamu terlalu sering reconnect. Tunggu sebentar lalu coba masuk lagi.");
            }
            if (rejoinCount >= configManager.getRejoinAlertThreshold() && configManager.isRejoinAlertAction()) {
                return new SecurityDecision(false, true, "REJOIN_SPAM", player.getName(), Math.max(risk, 15), "Player sering reconnect, kemungkinan koneksi lag", "ALERT_ONLY", "", "");
            }
        }

        if (configManager.isJoinProtectionEnabled() && joinCount > configManager.getMaxJoinsPerIp() && risk >= configManager.getRiskKickThreshold()) {
            return new SecurityDecision(true, true, "JOIN_SPAM", player.getName(), risk, "Terlalu banyak join dari IP hash yang sama", action, "join-kick", configManager.getJoinKickMessage());
        }

        return SecurityDecision.allow();
    }

    public void clear() {
        joinsByIp.clear();
        namesByIp.clear();
        rejoinsByIdentity.clear();
        temporaryHolds.clear();
    }

    private boolean isValidName(String originalName) {
        String name = stripBedrockPrefix(originalName == null ? "" : originalName);
        try {
            return Pattern.compile(configManager.getNameRegex()).matcher(name).matches();
        } catch (PatternSyntaxException ignored) {
            return Pattern.compile("^[a-zA-Z0-9_]{3,16}$").matcher(name).matches();
        }
    }

    private String stripBedrockPrefix(String name) {
        if (!configManager.isAllowBedrockPrefix()) return name;
        for (String prefix : configManager.getBedrockPrefixes()) {
            if (prefix != null && !prefix.isBlank() && name.startsWith(prefix)) return name.substring(prefix.length());
        }
        return name;
    }

    private int recordJoin(String ipHash, long now) {
        Queue<Long> queue = joinsByIp.computeIfAbsent(ipHash, ignored -> new ArrayDeque<>());
        purge(queue, now, configManager.getJoinWindowSeconds());
        queue.add(now);
        return queue.size();
    }

    private int recordName(String ipHash, String name, long now) {
        Map<String, Long> names = namesByIp.computeIfAbsent(ipHash, ignored -> new HashMap<>());
        long min = now - (configManager.getDifferentNamesWindowSeconds() * 1000L);
        names.entrySet().removeIf(entry -> entry.getValue() < min);
        names.put(name.toLowerCase(Locale.ROOT), now);
        return names.size();
    }

    private int recordRejoin(String ipHash, String name, long now) {
        String key = ipHash + ":" + name.toLowerCase(Locale.ROOT);
        Queue<Long> queue = rejoinsByIdentity.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        purge(queue, now, configManager.getRejoinWindowSeconds());
        queue.add(now);
        return queue.size();
    }

    private void purge(Queue<Long> queue, long now, int windowSeconds) {
        long min = now - (windowSeconds * 1000L);
        while (!queue.isEmpty() && queue.peek() < min) queue.poll();
    }

    private boolean isTemporarilyHeld(String ipHash, long now) {
        Long until = temporaryHolds.get(ipHash);
        if (until == null) return false;
        if (until <= now) {
            temporaryHolds.remove(ipHash);
            return false;
        }
        return true;
    }

    private String ipHash(Player player) {
        InetSocketAddress address = player.getAddress();
        String ip = address == null || address.getAddress() == null ? "unknown" : address.getAddress().getHostAddress();
        return riskManager.hashIp(ip);
    }
}
