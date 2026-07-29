package id.velioragardens.veliorasuite.module.announcement;

import id.velioragardens.veliorasuite.VelioraSuite;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class AnnouncementConfigManager {

    private final VelioraSuite plugin;
    private FileConfiguration config;

    public AnnouncementConfigManager(VelioraSuite plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveResourceIfNotExists("modules/announcement.yml");
        File file = new File(plugin.getDataFolder(), "modules/announcement.yml");
        this.config = YamlConfiguration.loadConfiguration(file);
    }

    public boolean isEnabled() {
        return getBoolean("settings.enabled", getBoolean("enabled", true));
    }

    public boolean isAutoStart() {
        return getBoolean("settings.auto-start", true);
    }

    public int getInitialDelaySeconds() {
        return Math.max(0, getInt("settings.initial-delay-seconds", 60));
    }

    public int getIntervalSeconds() {
        int seconds = getInt("settings.interval-seconds", 300);

        if (seconds < 10) {
            plugin.getLogger().warning("VelioraAnnouncement: interval-seconds terlalu kecil. Fallback ke 10 detik.");
            return 10;
        }

        return seconds;
    }

    public String getMode() {
        String mode = getString("settings.mode", "SEQUENTIAL").toUpperCase(Locale.ROOT);

        if (!mode.equals("SEQUENTIAL") && !mode.equals("RANDOM")) {
            plugin.getLogger().warning("VelioraAnnouncement: mode tidak valid: " + mode + ". Fallback ke SEQUENTIAL.");
            return "SEQUENTIAL";
        }

        return mode;
    }

    public boolean isRandomAvoidRepeat() {
        return getBoolean("settings.random-avoid-repeat", true);
    }

    public String getPrefix() {
        return getString("settings.prefix", "&8[&bVeliora&8] ");
    }

    public String getAdminPermission() {
        return getString("permissions.admin", "veliorasuite.announcement.admin");
    }

    public String getStatusPermission() {
        return getString("permissions.status", "veliorasuite.announcement.status");
    }

    public String getReloadPermission() {
        return getString("permissions.reload", "veliorasuite.announcement.reload");
    }

    public String getSendPermission() {
        return getString("permissions.send", "veliorasuite.announcement.send");
    }

    public boolean isSoundEnabled() {
        return getBoolean("sound.enabled", getBoolean("settings.sound.enabled", false));
    }

    public String getSoundName() {
        return getString("sound.name", getString("settings.sound.name", "ENTITY_EXPERIENCE_ORB_PICKUP"));
    }

    public float getSoundVolume() {
        return (float) getDouble("sound.volume", getDouble("settings.sound.volume", 1.0));
    }

    public float getSoundPitch() {
        return (float) getDouble("sound.pitch", getDouble("settings.sound.pitch", 1.0));
    }

    public Sound getSound() {
        String soundName = getSoundName();

        try {
            return Sound.valueOf(soundName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().warning("VelioraAnnouncement: sound tidak valid: " + soundName + ". Sound dilewati.");
            return null;
        }
    }

    public List<AnnouncementMessage> getAnnouncements() {
        ConfigurationSection section = config.getConfigurationSection("announcements");

        if (section == null) {
            plugin.getLogger().warning("VelioraAnnouncement: section announcements tidak ditemukan.");
            return Collections.emptyList();
        }

        List<AnnouncementMessage> announcements = new ArrayList<>();

        for (String id : section.getKeys(false)) {
            boolean enabled = section.getBoolean(id + ".enabled", true);
            String permission = section.getString(id + ".permission", "");
            List<String> worlds = section.getStringList(id + ".worlds");
            List<String> lines = section.getStringList(id + ".lines");

            AnnouncementMessage announcement = new AnnouncementMessage(id.toLowerCase(Locale.ROOT), enabled, permission, worlds, lines);

            if (!enabled) {
                continue;
            }

            if (lines.isEmpty()) {
                plugin.getLogger().warning("VelioraAnnouncement: announcement '" + id + "' aktif tapi tidak punya lines.");
                continue;
            }

            announcements.add(announcement);
        }

        return announcements;
    }

    public String getMessage(String path, String fallback) {
        return getString("messages." + path, fallback);
    }

    public List<String> getMessageList(String path, List<String> fallback) {
        List<String> list = config.getStringList("messages." + path);
        return list.isEmpty() ? fallback : list;
    }

    private String getString(String path, String fallback) {
        if (config == null) {
            return fallback;
        }

        return config.getString(path, fallback);
    }

    private boolean getBoolean(String path, boolean fallback) {
        if (config == null || !config.contains(path)) {
            return fallback;
        }

        return config.getBoolean(path, fallback);
    }

    private int getInt(String path, int fallback) {
        if (config == null || !config.contains(path)) {
            return fallback;
        }

        return config.getInt(path, fallback);
    }

    private double getDouble(String path, double fallback) {
        if (config == null || !config.contains(path)) {
            return fallback;
        }

        return config.getDouble(path, fallback);
    }
}
