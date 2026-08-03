package id.velioragardens.veliorasuite.module.adminmonitor;

import id.velioragardens.veliorasuite.VelioraSuite;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
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
import java.util.regex.Pattern;

public final class AdminMonitorManager {
    private static final Pattern DAILY_LOG = Pattern.compile("adminmonitor-\\d{4}-\\d{2}-\\d{2}\\.yml");
    private final VelioraSuite plugin;
    private final Map<UUID, Long> sessions = new HashMap<>();
    private FileConfiguration config;
    private File logsFolder;
    private ZoneId zoneId;

    public AdminMonitorManager(VelioraSuite plugin) { this.plugin = plugin; }

    public void load() {
        config = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "modules/adminmonitor.yml"));
        logsFolder = new File(plugin.getDataFolder(), "logs");
        if (!logsFolder.exists() && !logsFolder.mkdirs()) plugin.getLogger().warning("Gagal membuat folder log AdminMonitor.");
        try { zoneId = ZoneId.of(str("settings.timezone", "Asia/Jakarta")); }
        catch (Exception ignored) { zoneId = ZoneId.systemDefault(); }
        pruneLogs();
    }

    public boolean isEnabledInConfig() { return bool("settings.enabled", true); }
    public boolean isStaff(Player player) { return player.hasPermission(str("settings.staff-permission", "veliorasuite.staff")); }
    public boolean canView(CommandSender sender) { return sender.isOp() || sender.hasPermission(str("settings.admin-permission", "veliorasuite.adminmonitor.admin")) || sender.hasPermission("veliorasuite.adminmonitor.view"); }
    public boolean canReload(CommandSender sender) { return sender.isOp() || sender.hasPermission(str("settings.admin-permission", "veliorasuite.adminmonitor.admin")) || sender.hasPermission("veliorasuite.adminmonitor.reload"); }

    public void beginExistingSessions() {
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) if (isStaff(player)) sessions.putIfAbsent(player.getUniqueId(), now);
    }

    public void login(Player player) { if (track("login")) { sessions.put(player.getUniqueId(), System.currentTimeMillis()); record(player, "LOGIN", "masuk server", player.getLocation()); } }
    public void logout(Player player, String reason) {
        if (!track("logout")) return;
        Long started = sessions.remove(player.getUniqueId());
        String duration = started == null ? "durasi tidak diketahui" : "online " + formatDuration(System.currentTimeMillis() - started);
        record(player, reason, "keluar server (" + duration + ")", player.getLocation());
    }
    public void command(Player player, String command) { if (track("commands")) { String safe = sanitizeCommand(command); record(player, isModeration(root(safe)) ? "MODERATION" : "COMMAND", safe, player.getLocation()); } }
    public void worldChange(Player player, String from, String to) { if (track("world-change")) record(player, "WORLD", from + " -> " + to, player.getLocation()); }
    public void teleport(Player player, String cause, Location from, Location to) { if (track("teleport") && to != null && !sameBlock(from, to)) record(player, "TELEPORT", cause + ": " + formatLocation(from) + " -> " + formatLocation(to), to); }
    public void gameMode(Player player, String mode) { if (track("gamemode")) record(player, "GAMEMODE", "ubah ke " + mode, player.getLocation()); }
    public void flight(Player player, boolean enabled) { if (track("flight")) record(player, "FLY", enabled ? "fly aktif" : "fly nonaktif", player.getLocation()); }
    public void blockPlace(Player player, String material, Location location) { if (track("block-place")) record(player, "BLOCK_PLACE", material, location); }
    public void blockBreak(Player player, String material, Location location) { if (track("block-break")) record(player, "BLOCK_BREAK", material, location); }
    public void interact(Player player, String action, String target, Location location) { if (track("interact")) record(player, "INTERACT", action + " | " + target, location); }
    public void interactEntity(Player player, String entity) { if (track("entity-interact")) record(player, "ENTITY_INTERACT", entity, player.getLocation()); }
    public void attackEntity(Player player, String entity) { if (track("entity-attack")) record(player, "ENTITY_ATTACK", entity, player.getLocation()); }
    public void inventory(Player player, String inventory, String item) { if (track("inventory")) record(player, "INVENTORY", inventory + " | " + item, player.getLocation()); }
    public void itemPickup(Player player, String item) { if (track("item-pickup")) record(player, "ITEM_PICKUP", item, player.getLocation()); }
    public void itemDrop(Player player, String item) { if (track("item-drop")) record(player, "ITEM_DROP", item, player.getLocation()); }
    public void consume(Player player, String item) { if (track("consume")) record(player, "CONSUME", item, player.getLocation()); }
    public void bucket(Player player, String action, String item) { if (track("bucket")) record(player, action, item, player.getLocation()); }
    public void chat(Player player, String message) { if (track("chat")) record(player, "CHAT", message, player.getLocation()); }
    public void chatAsync(Player player, String message) { Bukkit.getScheduler().runTask(plugin, () -> chat(player, message)); }

    private void record(Player player, String type, String detail, Location location) {
        long now = System.currentTimeMillis();
        LocalDate date = Instant.ofEpochMilli(now).atZone(zoneId).toLocalDate();
        File file = dailyFile(date);
        YamlConfiguration log = YamlConfiguration.loadConfiguration(file);
        List<Map<?, ?>> entries = new ArrayList<>(log.getMapList("entries"));
        Map<String, Object> entry = new HashMap<>();
        entry.put("time", now);
        entry.put("date", date.toString());
        entry.put("clock", timeFormatter().format(Instant.ofEpochMilli(now)));
        entry.put("player", player.getName());
        entry.put("uuid", player.getUniqueId().toString());
        entry.put("type", type);
        entry.put("detail", detail);
        if (location != null) {
            entry.put("world", location.getWorld() == null ? "unknown" : location.getWorld().getName());
            entry.put("x", location.getBlockX());
            entry.put("y", location.getBlockY());
            entry.put("z", location.getBlockZ());
        }
        entries.add(entry);
        log.set("entries", entries);
        try { log.save(file); }
        catch (IOException exception) { plugin.getLogger().warning("Gagal menyimpan log AdminMonitor: " + exception.getMessage()); return; }
        notifyIfEnabled(player, type, detail);
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
    public void sendLog(CommandSender sender, String name, LocalDate date) { sendEntries(sender, entries(date).stream().filter(e -> name.equalsIgnoreCase(String.valueOf(e.get("player")))).toList(), "Aktivitas " + name + " pada " + date, 30); }
    public void sendToday(CommandSender sender, String name) {
        LocalDate today = LocalDate.now(zoneId);
        List<Map<?, ?>> result = entries(today);
        if (name != null) result = result.stream().filter(e -> name.equalsIgnoreCase(String.valueOf(e.get("player")))).toList();
        sendEntries(sender, result, name == null ? "Aktivitas staff hari ini" : "Aktivitas " + name + " hari ini", 50);
    }
    public void sendDate(CommandSender sender, LocalDate date) { sendEntries(sender, entries(date), "Aktivitas staff " + date, 50); }
    private void sendEntries(CommandSender sender, List<Map<?, ?>> result, String title, int limit) {
        result = result.stream().sorted(Comparator.comparingLong(e -> -number(e.get("time")))).limit(limit).toList();
        sender.sendMessage(color(prefix() + "&b" + title + "&b:"));
        if (result.isEmpty()) { sender.sendMessage(color("&8- &7Belum ada aktivitas tercatat.")); return; }
        for (Map<?, ?> e : result) sender.sendMessage(color("&8- &7" + e.get("clock") + " &8| &f" + e.get("player") + " &8| &b" + e.get("type") + " &8| &7" + e.get("detail") + locationSuffix(e)));
    }
    public void sendHelp(CommandSender sender) {
        sender.sendMessage(color("&8&m--------------------------------"));
        sender.sendMessage(color("&b&lAdminMonitor"));
        sender.sendMessage(color("&f/adminmonitor online &8- &7staff yang sedang online"));
        sender.sendMessage(color("&f/adminmonitor log <staff> [tanggal] &8- &7log staff, tanggal YYYY-MM-DD"));
        sender.sendMessage(color("&f/adminmonitor today [staff] &8- &7log hari ini"));
        sender.sendMessage(color("&f/adminmonitor date <tanggal> &8- &7semua log pada tanggal"));
        sender.sendMessage(color("&f/adminmonitor reload &8- &7reload konfigurasi"));
        sender.sendMessage(color("&8&m--------------------------------"));
    }
    public void sendNoPermission(CommandSender sender) { sender.sendMessage(color(str("messages.no-permission", "%prefix% &cKamu tidak punya izin.").replace("%prefix%", prefix()))); }
    public void sendReloadSuccess(CommandSender sender) { sender.sendMessage(color(str("messages.reload-success", "%prefix% &aAdminMonitor berhasil direload.").replace("%prefix%", prefix()))); }
    public void sendInvalidDate(CommandSender sender) { sender.sendMessage(color(prefix() + "&cFormat tanggal harus YYYY-MM-DD.")); }
    public LocalDate parseDate(String value) { try { return LocalDate.parse(value); } catch (Exception ignored) { return null; } }
    public LocalDate currentDate() { return LocalDate.now(zoneId); }
    public void shutdown() { for (Player player : Bukkit.getOnlinePlayers()) if (isStaff(player)) logout(player, "SERVER_STOP"); }

    private boolean track(String key) { return bool("settings.track." + key, true); }
    private void notifyIfEnabled(Player player, String type, String detail) {
        if (!config.getStringList("settings.notify-events").stream().anyMatch(event -> event.equalsIgnoreCase(type))) return;
        String message = prefix() + "&f" + player.getName() + " &8• &b" + type + " &8• &7" + detail;
        if (bool("settings.notify.console", false)) plugin.getLogger().info(ChatColor.stripColor(color(message)));
        if (bool("settings.notify.owner-chat", false)) Bukkit.getOnlinePlayers().stream().filter(p -> p.hasPermission(str("settings.notify-permission", "veliorasuite.adminmonitor.notify")) || p.isOp()).forEach(p -> p.sendMessage(color(message)));
    }
    private List<Map<?, ?>> entries(LocalDate date) { return new ArrayList<>(YamlConfiguration.loadConfiguration(dailyFile(date)).getMapList("entries")); }
    private File dailyFile(LocalDate date) { return new File(logsFolder, "adminmonitor-" + date + ".yml"); }
    private void pruneLogs() {
        LocalDate cutoff = LocalDate.now(zoneId).minusDays(Math.max(1, integer("settings.keep-days", 30)) - 1L);
        File[] files = logsFolder.listFiles(file -> DAILY_LOG.matcher(file.getName()).matches());
        if (files == null) return;
        for (File file : files) {
            String datePart = file.getName().substring("adminmonitor-".length(), "adminmonitor-".length() + 10);
            try { if (LocalDate.parse(datePart).isBefore(cutoff) && !file.delete()) plugin.getLogger().warning("Gagal menghapus log AdminMonitor lama: " + file.getName()); }
            catch (Exception ignored) { }
        }
    }
    private String sanitizeCommand(String command) { return config.getStringList("settings.sensitive-commands").stream().anyMatch(value -> value.equalsIgnoreCase(root(command))) ? root(command) + " <disamarkan>" : command; }
    private String root(String command) { String trimmed = command == null ? "" : command.trim(); return trimmed.isEmpty() ? "/" : trimmed.split(" ", 2)[0].toLowerCase(Locale.ROOT); }
    private boolean isModeration(String root) { return List.of("/kick", "/ban", "/tempban", "/mute", "/tempmute", "/warn", "/pardon", "/unban").contains(root); }
    private boolean sameBlock(Location one, Location two) { return one != null && two != null && one.getWorld() != null && one.getWorld().equals(two.getWorld()) && one.getBlockX() == two.getBlockX() && one.getBlockY() == two.getBlockY() && one.getBlockZ() == two.getBlockZ(); }
    private String formatLocation(Location location) { return location == null ? "unknown" : (location.getWorld() == null ? "unknown" : location.getWorld().getName()) + " (" + location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ() + ")"; }
    private String locationSuffix(Map<?, ?> entry) { return entry.containsKey("world") ? " &8[&7" + entry.get("world") + " " + entry.get("x") + "," + entry.get("y") + "," + entry.get("z") + "&8]" : ""; }
    private DateTimeFormatter timeFormatter() { return DateTimeFormatter.ofPattern("HH:mm:ss").withZone(zoneId); }
    private String prefix() { return str("settings.prefix", "&8[&bAdminMonitor&8] "); }
    private String color(String value) { return ChatColor.translateAlternateColorCodes('&', value); }
    private String str(String path, String fallback) { return config.contains(path) ? config.getString(path, fallback) : fallback; }
    private boolean bool(String path, boolean fallback) { return config.contains(path) ? config.getBoolean(path, fallback) : fallback; }
    private int integer(String path, int fallback) { return config.contains(path) ? config.getInt(path, fallback) : fallback; }
    private long number(Object value) { return value instanceof Number n ? n.longValue() : 0L; }
    private String formatDuration(long millis) { long minutes = Math.max(0, millis / 60_000); return (minutes / 60) + " jam " + (minutes % 60) + " menit"; }
}
