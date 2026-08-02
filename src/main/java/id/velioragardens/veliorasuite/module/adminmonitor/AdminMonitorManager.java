package id.velioragardens.veliorasuite.module.adminmonitor;

import id.velioragardens.veliorasuite.VelioraSuite;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class AdminMonitorManager {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());
    private final VelioraSuite plugin;
    private final Map<UUID, Long> sessions = new HashMap<>();
    private FileConfiguration config;
    private File logFile;
    private YamlConfiguration logs;

    public AdminMonitorManager(VelioraSuite plugin) { this.plugin = plugin; }

    public void load() {
        config = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "modules/adminmonitor.yml"));
        File folder = new File(plugin.getDataFolder(), "logs");
        if (!folder.exists()) folder.mkdirs();
        logFile = new File(folder, "admin-monitor.yml");
        logs = YamlConfiguration.loadConfiguration(logFile);
        pruneLogs();
    }

    public boolean isEnabledInConfig() { return bool("settings.enabled", true); }
    public boolean isStaff(Player player) { return player.hasPermission(str("settings.staff-permission", "veliorasuite.staff")); }
    public boolean canAdmin(CommandSender sender) { return sender.isOp() || sender.hasPermission(str("settings.admin-permission", "veliorasuite.adminmonitor.admin")); }

    public void beginExistingSessions() {
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) if (isStaff(player)) sessions.putIfAbsent(player.getUniqueId(), now);
    }

    public void login(Player player) {
        if (!isStaff(player) || !bool("settings.track.login", true)) return;
        sessions.put(player.getUniqueId(), System.currentTimeMillis());
        record(player, "LOGIN", "masuk server");
    }

    public void logout(Player player, String reason) {
        if (!isStaff(player) || !bool("settings.track.logout", true)) return;
        long started = sessions.remove(player.getUniqueId());
        String duration = started <= 0 ? "durasi tidak diketahui" : "online " + formatDuration(System.currentTimeMillis() - started);
        record(player, reason, "keluar server (" + duration + ")");
    }

    public void command(Player player, String command) {
        if (!isStaff(player) || !bool("settings.track.commands", true)) return;
        String safe = sanitizeCommand(command);
        String root = safe.split(" ", 2)[0].toLowerCase(Locale.ROOT);
        String type = isModeration(root) ? "MODERATION" : "COMMAND";
        record(player, type, safe);
    }
    public void worldChange(Player player, String from, String to) { if (isStaff(player) && bool("settings.track.world-change", true)) record(player, "WORLD", from + " -> " + to); }
    public void teleport(Player player, String cause, String from, String to) { if (isStaff(player) && bool("settings.track.teleport", true) && !from.equals(to)) record(player, "TELEPORT", cause + ": " + from + " -> " + to); }
    public void gameMode(Player player, String mode) { if (isStaff(player) && bool("settings.track.gamemode", true)) record(player, "GAMEMODE", "ubah ke " + mode); }
    public void flight(Player player, boolean enabled) { if (isStaff(player) && bool("settings.track.flight", true)) record(player, "FLY", enabled ? "fly aktif" : "fly nonaktif"); }

    private void record(Player player, String type, String detail) {
        long now = System.currentTimeMillis();
        List<Map<?, ?>> entries = new ArrayList<>(logs.getMapList("entries"));
        Map<String, Object> entry = new HashMap<>();
        entry.put("time", now);
        entry.put("date", LocalDate.now().toString());
        entry.put("player", player.getName());
        entry.put("uuid", player.getUniqueId().toString());
        entry.put("type", type);
        entry.put("detail", detail);
        entries.add(entry);
        logs.set("entries", entries);
        saveLogs();
        String message = prefix() + "&f" + player.getName() + " &8• &b" + type + " &8• &7" + detail;
        if (bool("settings.notify.console", true)) plugin.getLogger().info(ChatColor.stripColor(color(message)));
        if (bool("settings.notify.owner-chat", true)) {
            String permission = str("settings.notify-permission", "veliorasuite.adminmonitor.notify");
            Bukkit.getOnlinePlayers().stream().filter(p -> p.hasPermission(permission) || p.isOp()).forEach(p -> p.sendMessage(color(message)));
        }
        sendDiscord(message);
    }

    public void sendOnline(CommandSender sender) {
        sender.sendMessage(color(prefix() + "&bStaff online:"));
        boolean found = false;
        for (Player player : Bukkit.getOnlinePlayers()) if (isStaff(player)) {
            long started = sessions.getOrDefault(player.getUniqueId(), System.currentTimeMillis());
            sender.sendMessage(color("&8- &f" + player.getName() + " &7(" + formatDuration(System.currentTimeMillis() - started) + ") &8[&a" + player.getWorld().getName() + "&8]"));
            found = true;
        }
        if (!found) sender.sendMessage(color("&8- &7Tidak ada staff yang sedang online."));
    }

    public void sendLog(CommandSender sender, String name) {
        List<Map<?, ?>> result = entries().stream().filter(e -> name.equalsIgnoreCase(String.valueOf(e.get("player")))).sorted(Comparator.comparingLong(e -> -number(e.get("time")))).limit(12).toList();
        if (result.isEmpty()) { sender.sendMessage(color(str("messages.player-not-found", "%prefix% &cStaff &f%player% &ctidak ditemukan di log.").replace("%prefix%", prefix()).replace("%player%", name))); return; }
        sender.sendMessage(color(prefix() + "&bAktivitas terakhir &f" + name + "&b:"));
        for (Map<?, ?> e : result) sender.sendMessage(color("&8- &7" + TIME.format(Instant.ofEpochMilli(number(e.get("time")))) + " &8| &b" + e.get("type") + " &8| &f" + e.get("detail")));
    }

    public void sendToday(CommandSender sender) {
        String today = LocalDate.now().toString();
        List<Map<?, ?>> result = entries().stream().filter(e -> today.equals(String.valueOf(e.get("date")))).sorted(Comparator.comparingLong(e -> -number(e.get("time")))).limit(30).toList();
        sender.sendMessage(color(prefix() + "&bAktivitas staff hari ini:"));
        if (result.isEmpty()) { sender.sendMessage(color("&8- &7Belum ada aktivitas tercatat.")); return; }
        for (Map<?, ?> e : result) sender.sendMessage(color("&8- &7" + TIME.format(Instant.ofEpochMilli(number(e.get("time")))) + " &8| &f" + e.get("player") + " &8| &b" + e.get("type") + " &8| &7" + e.get("detail")));
    }

    public void sendHelp(CommandSender sender) {
        sender.sendMessage(color("&8&m--------------------------------"));
        sender.sendMessage(color("&b&lAdminMonitor"));
        sender.sendMessage(color("&f/adminmonitor online &8- &7staff yang sedang online"));
        sender.sendMessage(color("&f/adminmonitor log <staff> &8- &7aktivitas terakhir staff"));
        sender.sendMessage(color("&f/adminmonitor today &8- &7aktivitas staff hari ini"));
        sender.sendMessage(color("&f/adminmonitor reload &8- &7reload konfigurasi"));
        sender.sendMessage(color("&8&m--------------------------------"));
    }
    public void sendNoPermission(CommandSender sender) { sender.sendMessage(color(str("messages.no-permission", "%prefix% &cKamu tidak punya izin.").replace("%prefix%", prefix()))); }
    public void sendReloadSuccess(CommandSender sender) { sender.sendMessage(color(str("messages.reload-success", "%prefix% &aAdminMonitor berhasil direload.").replace("%prefix%", prefix()))); }

    public void shutdown() { for (Player player : Bukkit.getOnlinePlayers()) if (isStaff(player)) logout(player, "SERVER_STOP"); }
    private List<Map<?, ?>> entries() { return new ArrayList<>(logs.getMapList("entries")); }
    private void saveLogs() { try { logs.save(logFile); } catch (IOException exception) { plugin.getLogger().warning("Gagal menyimpan admin-monitor.yml: " + exception.getMessage()); } }
    private void pruneLogs() {
        long minimum = System.currentTimeMillis() - (Math.max(1, integer("settings.keep-days", 30)) * 86_400_000L);
        List<Map<?, ?>> kept = entries().stream().filter(e -> number(e.get("time")) >= minimum).toList();
        logs.set("entries", kept); saveLogs();
    }
    private void sendDiscord(String message) {
        if (!bool("settings.notify.discord-webhook", false)) return;
        String url = str("settings.notify.discord-webhook-url", "").trim();
        if (url.isEmpty()) return;
        String json = "{\"content\":\"" + ChatColor.stripColor(color(message)).replace("\\", "\\\\").replace("\"", "\\\"") + "\"}";
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try { HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(url)).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(json)).build(), HttpResponse.BodyHandlers.discarding()); }
            catch (Exception ignored) { }
        });
    }
    private String sanitizeCommand(String command) { String root = command.trim().split(" ", 2)[0].toLowerCase(Locale.ROOT); return config.getStringList("settings.sensitive-commands").stream().anyMatch(value -> value.equalsIgnoreCase(root)) ? root + " <disamarkan>" : command; }
    private boolean isModeration(String root) { return List.of("/kick", "/ban", "/tempban", "/mute", "/tempmute", "/warn", "/pardon", "/unban").contains(root); }
    private String prefix() { return str("settings.prefix", "&8[&bAdminMonitor&8] "); }
    private String color(String value) { return ChatColor.translateAlternateColorCodes('&', value); }
    private String str(String path, String fallback) { return config.contains(path) ? config.getString(path, fallback) : fallback; }
    private boolean bool(String path, boolean fallback) { return config.contains(path) ? config.getBoolean(path, fallback) : fallback; }
    private int integer(String path, int fallback) { return config.contains(path) ? config.getInt(path, fallback) : fallback; }
    private long number(Object value) { return value instanceof Number n ? n.longValue() : 0L; }
    private String formatDuration(long millis) { long minutes = Math.max(0, millis / 60_000); return (minutes / 60) + " jam " + (minutes % 60) + " menit"; }
}
