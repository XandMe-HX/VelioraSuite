package id.velioragardens.veliorasuite.module.announcement;

import id.velioragardens.veliorasuite.VelioraSuite;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

public final class AnnouncementManager {

    private final VelioraSuite plugin;
    private final List<String> activeIds = new ArrayList<>();

    private FileConfiguration config;
    private BukkitTask task;
    private int currentIndex = 0;

    public AnnouncementManager(VelioraSuite plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "modules/announcement.yml");

        if (!file.exists()) {
            plugin.saveResource("modules/announcement.yml", false);
        }

        this.config = YamlConfiguration.loadConfiguration(file);
        reloadActiveIds();
        startTask();

        plugin.getLogger().info("VelioraAnnouncement loaded with " + activeIds.size() + " active announcement(s).");
    }

    public void reload() {
        stopTask();
        load();
    }

    public void shutdown() {
        stopTask();
        activeIds.clear();
    }

    public boolean isEnabled() {
        return config != null && config.getBoolean("enabled", true);
    }

    public int getIntervalSeconds() {
        if (config == null) return 300;
        return Math.max(10, config.getInt("settings.interval-seconds", 300));
    }

    public String getMode() {
        if (config == null) return "RANDOM";
        return config.getString("settings.mode", "RANDOM").toUpperCase(Locale.ROOT);
    }

    public int getActiveCount() {
        return activeIds.size();
    }

    public List<String> getActiveIds() {
        return Collections.unmodifiableList(activeIds);
    }

    public void sendNext() {
        if (!isEnabled()) return;

        if (activeIds.isEmpty()) {
            return;
        }

        if (!config.getBoolean("settings.send-on-empty-server", false) && Bukkit.getOnlinePlayers().isEmpty()) {
            return;
        }

        String id;
        String mode = getMode();

        if (mode.equals("SEQUENTIAL")) {
            if (currentIndex >= activeIds.size()) {
                currentIndex = 0;
            }

            id = activeIds.get(currentIndex);
            currentIndex++;
        } else {
            id = activeIds.get(ThreadLocalRandom.current().nextInt(activeIds.size()));
        }

        broadcast(id);
    }

    public boolean sendById(String id) {
        if (id == null || id.isBlank()) return false;

        String normalizedId = id.toLowerCase(Locale.ROOT);

        if (!activeIds.contains(normalizedId)) {
            return false;
        }

        broadcast(normalizedId);
        return true;
    }

    public void sendStatus(CommandSender sender) {
        for (String line : config.getStringList("messages.status")) {
            sender.sendMessage(color(line
                    .replace("%enabled%", String.valueOf(isEnabled()))
                    .replace("%auto_start%", String.valueOf(config.getBoolean("settings.auto-start", true)))
                    .replace("%mode%", getMode())
                    .replace("%interval%", String.valueOf(getIntervalSeconds()))
                    .replace("%total%", String.valueOf(getActiveCount()))));
        }
    }

    public void sendReloadSuccess(CommandSender sender) {
        send(sender, getMessage("reload-success"));
    }

    public void sendNoPermission(CommandSender sender) {
        send(sender, getMessage("no-permission"));
    }

    public void sendNotFound(CommandSender sender, String id) {
        send(sender, getMessage("not-found").replace("%id%", id));
    }

    public void sendNoAnnouncements(CommandSender sender) {
        send(sender, getMessage("no-announcements"));
    }

    public void sendManualSuccess(CommandSender sender, String id) {
        send(sender, getMessage("send-success").replace("%id%", id));
    }

    private void reloadActiveIds() {
        activeIds.clear();

        ConfigurationSection section = config.getConfigurationSection("announcements");
        if (section == null) {
            return;
        }

        for (String id : section.getKeys(false)) {
            if (!section.getBoolean(id + ".enabled", true)) {
                continue;
            }

            List<String> lines = section.getStringList(id + ".lines");
            if (lines.isEmpty()) {
                continue;
            }

            activeIds.add(id.toLowerCase(Locale.ROOT));
        }
    }

    private void startTask() {
        stopTask();

        if (!isEnabled() || !config.getBoolean("settings.auto-start", true)) {
            return;
        }

        long initialDelay = Math.max(0, config.getInt("settings.initial-delay-seconds", 60)) * 20L;
        long interval = getIntervalSeconds() * 20L;

        task = Bukkit.getScheduler().runTaskTimer(plugin, this::sendNext, initialDelay, interval);
    }

    private void stopTask() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void broadcast(String id) {
        String path = "announcements." + id;
        List<String> lines = config.getStringList(path + ".lines");

        if (lines.isEmpty()) {
            return;
        }

        boolean usePermission = config.getBoolean("settings.use-per-announcement-permission", false);
        String permission = config.getString(path + ".permission", "");

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!canReceive(player)) {
                continue;
            }

            if (usePermission && permission != null && !permission.isBlank() && !player.hasPermission(permission)) {
                continue;
            }

            for (String line : lines) {
                player.sendMessage(color(line));
            }

            playSound(player);
        }

        if (config.getBoolean("settings.send-to-console", false)) {
            for (String line : lines) {
                Bukkit.getConsoleSender().sendMessage(color(line));
            }
        }
    }

    private boolean canReceive(Player player) {
        if (!config.getBoolean("settings.worlds.enabled", false)) {
            return true;
        }

        String worldName = player.getWorld().getName();
        List<String> whitelist = config.getStringList("settings.worlds.whitelist");
        List<String> blacklist = config.getStringList("settings.worlds.blacklist");

        if (blacklist.contains(worldName)) {
            return false;
        }

        return whitelist.isEmpty() || whitelist.contains(worldName);
    }

    private void playSound(Player player) {
        if (!config.getBoolean("settings.sound.enabled", false)) {
            return;
        }

        String soundName = config.getString("settings.sound.name", "ENTITY_EXPERIENCE_ORB_PICKUP");

        try {
            Sound sound = Sound.valueOf(soundName.toUpperCase(Locale.ROOT));
            float volume = (float) config.getDouble("settings.sound.volume", 1.0);
            float pitch = (float) config.getDouble("settings.sound.pitch", 1.0);
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (IllegalArgumentException ignored) {
        }
    }

    private String getMessage(String path) {
        String prefix = config.getString("messages.prefix", "&8【&aVelioraAnnouncement&8】");
        return config.getString("messages." + path, "%prefix% &cMessage not found: " + path)
                .replace("%prefix%", prefix);
    }

    private void send(CommandSender sender, String message) {
        sender.sendMessage(color(message));
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }
}
