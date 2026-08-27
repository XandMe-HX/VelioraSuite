package id.velioragardens.veliorasuite.module.security;

import id.velioragardens.veliorasuite.VelioraSuite;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class SecurityAltGuard {
    private static final int WARN_LIMIT = 2;
    private static final int ACCOUNT_BLOCK_LIMIT = 3;
    private static final int HARD_ALERT_LIMIT = 4;
    private static final int IP_LOCK_LIMIT = 5;

    private final VelioraSuite plugin;
    private final SecurityConfigManager config;
    private final File file;
    private final Map<UUID, AltAccount> accounts = new HashMap<>();
    private final Set<UUID> trusted = new HashSet<>();
    private final List<String> alerts = new ArrayList<>();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");

    public SecurityAltGuard(VelioraSuite plugin, SecurityConfigManager config) {
        this.plugin = plugin;
        this.config = config;
        this.file = new File(plugin.getDataFolder(), "data/security-altguard.yml");
    }

    public void load() {
        accounts.clear();
        trusted.clear();
        if (!file.exists()) {
            save();
            return;
        }
        FileConfiguration data = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection acc = data.getConfigurationSection("accounts");
        if (acc != null) {
            for (String key : acc.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    AltAccount account = new AltAccount(uuid);
                    account.name = acc.getString(key + ".name", "unknown");
                    account.ipHash = acc.getString(key + ".ip-hash", "unknown");
                    account.ipMasked = acc.getString(key + ".ip-masked", "unknown");
                    account.firstSeen = acc.getLong(key + ".first-seen", 0L);
                    account.lastSeen = acc.getLong(key + ".last-seen", 0L);
                    account.loginCount = acc.getInt(key + ".login-count", 0);
                    account.payIn = acc.getDouble(key + ".pay-in", 0.0D);
                    account.payOut = acc.getDouble(key + ".pay-out", 0.0D);
                    account.blockedPay = acc.getInt(key + ".blocked-pay", 0);
                    accounts.put(uuid, account);
                } catch (Exception ignored) {
                }
            }
        }
        for (String raw : data.getStringList("trusted")) {
            try { trusted.add(UUID.fromString(raw)); } catch (Exception ignored) { }
        }
        alerts.addAll(data.getStringList("alerts"));
        while (alerts.size() > 50) alerts.remove(0);
    }

    public void save() {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            FileConfiguration data = new YamlConfiguration();
            for (AltAccount account : accounts.values()) {
                String path = "accounts." + account.uuid;
                data.set(path + ".name", account.name);
                data.set(path + ".ip-hash", account.ipHash);
                data.set(path + ".ip-masked", account.ipMasked);
                data.set(path + ".first-seen", account.firstSeen);
                data.set(path + ".last-seen", account.lastSeen);
                data.set(path + ".login-count", account.loginCount);
                data.set(path + ".pay-in", account.payIn);
                data.set(path + ".pay-out", account.payOut);
                data.set(path + ".blocked-pay", account.blockedPay);
            }
            data.set("trusted", trusted.stream().map(UUID::toString).toList());
            data.set("alerts", alerts);
            data.save(file);
        } catch (IOException exception) {
            plugin.getLogger().warning("VelioraAltGuard gagal menyimpan data: " + exception.getMessage());
        }
    }

    public String onJoin(Player player) {
        if (player == null || config.hasBypass(player)) return null;
        String rawIp = rawIp(player);
        String ipHash = hashIp(rawIp);
        long now = System.currentTimeMillis();
        AltAccount account = accounts.computeIfAbsent(player.getUniqueId(), AltAccount::new);
        account.name = player.getName();
        account.ipHash = ipHash;
        account.ipMasked = maskIp(rawIp);
        if (account.firstSeen <= 0L) account.firstSeen = now;
        account.lastSeen = now;
        account.loginCount++;
        save();

        List<AltAccount> group = groupByHash(ipHash);
        int total = group.size();
        AltAccount main = mainAccount(group);
        // Shared IP is normal on public Wi-Fi, schools and mobile carriers.
        // Do not broadcast it on every join; staff can inspect it with /valt.
        // Satu IP saja bukan bukti cukup untuk AutoBan. Jaringan rumah, sekolah,
        // warnet, CGNAT, atau VPN dapat dipakai banyak pemain yang berbeda.
        // AltGuard hanya memberi alert dan membatasi transfer ekonomi untuk direview admin.
        return null;
    }

    public void onQuit(Player player) {
        if (player == null) return;
        AltAccount account = accounts.get(player.getUniqueId());
        if (account != null) {
            account.lastSeen = System.currentTimeMillis();
            save();
        }
    }

    public boolean onPayCommand(Player player, String commandLine) {
        if (player == null || commandLine == null || config.hasBypass(player)) return false;
        String lower = commandLine.toLowerCase(Locale.ROOT).trim();
        if (!lower.startsWith("/pay ") && !lower.startsWith("/essentials:pay ")) return false;
        String[] parts = commandLine.trim().split("\\s+");
        if (parts.length < 3) return false;
        AltAccount sender = accounts.get(player.getUniqueId());
        if (sender == null) return false;
        AltAccount target = findByName(parts[1]);
        double amount = parseAmount(parts[2]);
        List<AltAccount> group = groupByHash(sender.ipHash);
        boolean sameIpTarget = config.isNetworkGuardEconomySameNetworkBlockEnabled()
                && target != null && sender.ipHash.equals(target.ipHash) && !sender.uuid.equals(target.uuid);
        boolean frozenGroup = config.isNetworkGuardEconomySameNetworkBlockEnabled() && group.size() >= ACCOUNT_BLOCK_LIMIT;
        if (sameIpTarget || frozenGroup) {
            sender.blockedPay++;
            sender.payOut += Math.max(0.0D, amount);
            if (target != null) target.payIn += Math.max(0.0D, amount);
            AltAccount main = mainAccount(group);
            alertPay(player.getName(), target == null ? parts[1] : target.name, amount, group.size(), main, sender.ipHash);
            save();
            player.sendMessage(config.color("&8[&cVelioraAltGuard&8] &cTransfer diblokir. Dilarang memakai akun tambahan untuk memperkaya akun utama."));
            return true;
        }
        sender.payOut += Math.max(0.0D, amount);
        if (target != null) target.payIn += Math.max(0.0D, amount);
        save();
        return false;
    }

    public void sendHelp(CommandSender sender) {
        if (!config.hasAdmin(sender)) { noPermission(sender); return; }
        sender.sendMessage(color("&8&m--------------------------------"));
        sender.sendMessage(color("&c&lVelioraAltGuard"));
        sender.sendMessage(color("&f/valt check <player> &7- Cek akun satu IP."));
        sender.sendMessage(color("&f/valt list &7- List IP dengan 2+ akun."));
        sender.sendMessage(color("&f/valt alerts &7- Alert terbaru AltGuard."));
        sender.sendMessage(color("&f/valt trust <player> &7- Whitelist akun rumah/keluarga."));
        sender.sendMessage(color("&f/valt untrust <player> &7- Hapus whitelist."));
        sender.sendMessage(color("&7Rule: &f1 akun normal, 2+ akun alert dan review admin. Tidak ada AutoBan hanya dari IP."));
        sender.sendMessage(color("&8&m--------------------------------"));
    }

    public void sendCheck(CommandSender sender, String name) {
        if (!config.hasAdmin(sender)) { noPermission(sender); return; }
        AltAccount account = findByName(name);
        if (account == null) {
            Player online = Bukkit.getPlayerExact(name);
            if (online != null) account = accounts.get(online.getUniqueId());
        }
        if (account == null) {
            sender.sendMessage(color("&8[&cVelioraAltGuard&8] &cData player tidak ditemukan."));
            return;
        }
        List<AltAccount> group = groupByHash(account.ipHash);
        AltAccount main = mainAccount(group);
        sendGroupReport(sender, account.ipHash, group.size(), main, group, actionFor(group.size()));
    }

    public void sendList(CommandSender sender) {
        if (!config.hasAdmin(sender)) { noPermission(sender); return; }
        Map<String, List<AltAccount>> groups = new HashMap<>();
        for (AltAccount account : accounts.values()) {
            if (account.ipHash == null || account.ipHash.isBlank()) continue;
            groups.computeIfAbsent(account.ipHash, ignored -> new ArrayList<>()).add(account);
        }
        sender.sendMessage(color("&8&m--------------------------------"));
        sender.sendMessage(color("&c&lVelioraAltGuard List"));
        int count = 0;
        for (Map.Entry<String, List<AltAccount>> entry : groups.entrySet()) {
            List<AltAccount> group = entry.getValue();
            if (group.size() < 2) continue;
            AltAccount main = mainAccount(group);
            sender.sendMessage(color("&8- &7IP Hash: &f" + entry.getKey() + " &8| &7Total: &f" + group.size() + " &8| &7Main: &f" + (main == null ? "unknown" : main.name) + " &8| &7Action: &f" + actionFor(group.size())));
            count++;
        }
        if (count == 0) sender.sendMessage(color("&7Belum ada IP dengan 2+ akun."));
        sender.sendMessage(color("&8&m--------------------------------"));
    }

    public void sendAlerts(CommandSender sender) {
        if (!config.hasAlerts(sender)) { noPermission(sender); return; }
        sender.sendMessage(color("&8&m--------------------------------"));
        sender.sendMessage(color("&c&lVelioraAltGuard Alerts"));
        if (alerts.isEmpty()) sender.sendMessage(color("&7Belum ada alert."));
        alerts.stream().skip(Math.max(0, alerts.size() - 10)).forEach(line -> sender.sendMessage(color(line)));
        sender.sendMessage(color("&8&m--------------------------------"));
    }

    public void trust(CommandSender sender, String name, boolean value) {
        if (!config.hasAdmin(sender)) { noPermission(sender); return; }
        AltAccount account = findByName(name);
        Player online = Bukkit.getPlayerExact(name);
        UUID uuid = account != null ? account.uuid : online != null ? online.getUniqueId() : null;
        if (uuid == null) {
            sender.sendMessage(color("&8[&cVelioraAltGuard&8] &cPlayer tidak ditemukan."));
            return;
        }
        if (value) trusted.add(uuid); else trusted.remove(uuid);
        save();
        sender.sendMessage(color("&8[&cVelioraAltGuard&8] &aTrust &f" + name + " &a= &f" + value));
    }

    private void sendGroupReport(CommandSender sender, String ipHash, int total, AltAccount main, List<AltAccount> group, String action) {
        sender.sendMessage(color("&8&m--------------------------------"));
        sender.sendMessage(color("&c&lVelioraAltGuard"));
        sender.sendMessage(color("&cIP mencurigakan terdeteksi."));
        sender.sendMessage(color(""));
        sender.sendMessage(color("&7IP Hash: &f" + ipHash));
        sender.sendMessage(color("&7Total akun: &f" + total));
        sender.sendMessage(color("&7Akun utama: &f" + (main == null ? "unknown" : main.name)));
        sender.sendMessage(color("&7Akun lain:"));
        for (AltAccount account : sortedByLoginCount(group)) {
            if (main != null && main.uuid.equals(account.uuid)) continue;
            sender.sendMessage(color("&8- &f" + account.name + " &7UUID &f" + account.uuid + " &8| &7Login &f" + account.loginCount + " &8| &7/pay blocked &f" + account.blockedPay));
        }
        sender.sendMessage(color(""));
        sender.sendMessage(color("&7Action: &f" + action));
        sender.sendMessage(color("&8&m--------------------------------"));
    }

    private void alertGroup(String ipHash, int total, AltAccount main, List<AltAccount> group, String action) {
        String line = "&8[&cVelioraAltGuard&8] &cIP mencurigakan &8| &7IP Hash: &f" + ipHash + " &8| &7Total akun: &f" + total + " &8| &7Akun utama: &f" + (main == null ? "unknown" : main.name) + " &8| &7Action: &f" + action;
        pushAlert(line);
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!config.hasAlerts(online) && !config.hasAdmin(online)) continue;
            sendGroupReport(online, ipHash, total, main, group, action);
        }
    }

    private void alertPay(String from, String to, double amount, int total, AltAccount main, String ipHash) {
        String line = "&8[&cVelioraAltGuard&8] &c/pay mencurigakan &8| &f" + from + " &7-> &f" + to + " &8| &7Nominal: &f" + amount + " &8| &7Total akun IP: &f" + total + " &8| &7Main: &f" + (main == null ? "unknown" : main.name);
        pushAlert(line);
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!config.hasAlerts(online) && !config.hasAdmin(online)) continue;
            online.sendMessage(color("&8&m--------------------------------"));
            online.sendMessage(color("&c&lVelioraAltGuard EcoGuard"));
            online.sendMessage(color("&7IP Hash: &f" + ipHash));
            online.sendMessage(color("&7Transfer: &f" + from + " &7-> &f" + to));
            online.sendMessage(color("&7Nominal: &f" + amount));
            online.sendMessage(color("&7Reason: &f/pay antar akun satu IP / grup ekonomi dibekukan."));
            online.sendMessage(color("&7Action: &fBlock /pay + Review Required"));
            online.sendMessage(color("&8&m--------------------------------"));
        }
    }

    private void blockAccount(Player player, int total, AltAccount main, List<AltAccount> group) {
        String reason = "Akun tambahan terdeteksi oleh VelioraAltGuard. Konfirmasi ke Owner jika ini kesalahan.";
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ban " + player.getName() + " " + reason);
        pushAlert("&8[&cVelioraAltGuard&8] &cAkun diblokir: &f" + player.getName() + " &7Total akun IP: &f" + total + " &7Main: &f" + (main == null ? "unknown" : main.name));
    }

    private void lockIp(String rawIp, int total, AltAccount main, List<AltAccount> group) {
        if (rawIp == null || rawIp.isBlank() || rawIp.equals("unknown")) return;
        String reason = "Terlalu banyak akun dari satu koneksi. Konfirmasi ke Owner Veliora Gardens.";
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ban-ip " + rawIp + " " + reason);
        pushAlert("&8[&cVelioraAltGuard&8] &4IP LOCK &8| &7IP Hash: &f" + hashIp(rawIp) + " &8| &7Total akun: &f" + total + " &8| &7Main: &f" + (main == null ? "unknown" : main.name));
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (rawIp.equals(rawIp(online))) online.kickPlayer("§cIP dikunci oleh VelioraAltGuard. Konfirmasi ke Owner jika ini kesalahan.");
        }
    }

    private void pushAlert(String line) {
        alerts.add("&7" + dateFormat.format(System.currentTimeMillis()) + " &r" + line);
        while (alerts.size() > 50) alerts.remove(0);
        save();
    }

    private List<AltAccount> groupByHash(String ipHash) {
        if (ipHash == null || ipHash.isBlank()) return List.of();
        List<AltAccount> group = new ArrayList<>();
        for (AltAccount account : accounts.values()) {
            if (ipHash.equals(account.ipHash) && !trusted.contains(account.uuid)) group.add(account);
        }
        return group;
    }

    private AltAccount mainAccount(List<AltAccount> group) {
        return group.stream().max(Comparator.comparingInt(account -> account.loginCount)).orElse(null);
    }

    private List<AltAccount> sortedByLoginCount(List<AltAccount> group) {
        return group.stream().sorted(Comparator.comparingInt((AltAccount account) -> account.loginCount).reversed()).toList();
    }

    private AltAccount findByName(String name) {
        if (name == null) return null;
        for (AltAccount account : accounts.values()) if (account.name.equalsIgnoreCase(name)) return account;
        OfflinePlayer offline = Bukkit.getOfflinePlayerIfCached(name);
        return offline == null ? null : accounts.get(offline.getUniqueId());
    }

    private String rawIp(Player player) {
        try {
            if (player.getAddress() == null || player.getAddress().getAddress() == null) return "unknown";
            return player.getAddress().getAddress().getHostAddress();
        } catch (Exception ignored) {
            return "unknown";
        }
    }

    private String maskIp(String raw) {
        if (raw == null || raw.isBlank() || raw.equals("unknown")) return "unknown";
        if (raw.contains(".")) {
            String[] parts = raw.split("\\.");
            if (parts.length == 4) return parts[0] + "." + parts[1] + ".xxx.xxx";
        }
        if (raw.contains(":")) return raw.substring(0, Math.min(raw.length(), 8)) + ":xxxx";
        return "masked";
    }

    private String hashIp(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((raw + ":VelioraGardens").getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder("ip-");
            for (int i = 0; i < 4; i++) builder.append(String.format("%02x", bytes[i]));
            return builder.toString();
        } catch (Exception exception) {
            return "ip-" + Integer.toHexString(String.valueOf(raw).hashCode());
        }
    }

    private String actionFor(int total) {
        if (total >= IP_LOCK_LIMIT) return "Critical Alert + Owner Review Required";
        if (total >= HARD_ALERT_LIMIT) return "Hard Alert + Owner Review Required";
        if (total >= ACCOUNT_BLOCK_LIMIT) return "Economy Transfer Frozen + Admin Review";
        if (total >= WARN_LIMIT) return "Alert Admin + No More Account";
        return "Normal";
    }

    private double parseAmount(String raw) {
        try { return Double.parseDouble(raw.replace(",", "").replace("_", "")); } catch (Exception ignored) { return 0.0D; }
    }

    private String color(String text) { return config.color(text); }
    private void noPermission(CommandSender sender) { sender.sendMessage(config.color(config.message("no-permission", "%prefix% &cKamu tidak punya izin."))); }

    private static final class AltAccount {
        private final UUID uuid;
        private String name = "unknown";
        private String ipHash = "unknown";
        private String ipMasked = "unknown";
        private long firstSeen;
        private long lastSeen;
        private int loginCount;
        private double payIn;
        private double payOut;
        private int blockedPay;

        private AltAccount(UUID uuid) { this.uuid = uuid; }
    }
}
