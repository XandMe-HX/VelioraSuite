package id.velioragardens.veliorasuite.module.report;

import id.velioragardens.veliorasuite.VelioraSuite;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.List;
import java.util.Locale;

public final class ReportConfigManager {

    private final VelioraSuite plugin;
    private FileConfiguration config;

    public ReportConfigManager(VelioraSuite plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveResourceIfNotExists("modules/report.yml");
        File file = new File(plugin.getDataFolder(), "modules/report.yml");
        this.config = YamlConfiguration.loadConfiguration(file);
    }

    public boolean isEnabled() {
        return getBoolean("settings.enabled", true);
    }

    public String getPrefix() {
        return getString("settings.prefix", "&8[&cVelioraReport&8] ");
    }

    public int getMinReasonLength() {
        return Math.max(1, getInt("settings.min-reason-length", 5));
    }

    public int getMaxReasonLength() {
        return Math.max(getMinReasonLength(), getInt("settings.max-reason-length", 120));
    }

    public long getCooldownMillis() {
        return parseDuration(getString("settings.cooldown", "60s"), 60_000L);
    }

    public boolean isBlockSelfReport() {
        return getBoolean("settings.block-self-report", true);
    }

    public boolean isNotifyStaff() {
        return getBoolean("settings.notify-staff", true);
    }

    public boolean isSaveLocation() {
        return getBoolean("settings.save-location", true);
    }

    public String getUsePermission() {
        return getString("permissions.use", "veliorasuite.report.use");
    }

    public String getStaffPermission() {
        return getString("permissions.staff", "veliorasuite.report.staff");
    }

    public String getAdminPermission() {
        return getString("permissions.admin", "veliorasuite.report.admin");
    }

    public String getReloadPermission() {
        return getString("permissions.reload", "veliorasuite.report.reload");
    }

    public String getBypassCooldownPermission() {
        return getString("permissions.bypass-cooldown", "veliorasuite.report.bypasscooldown");
    }

    public String getMessage(String path, String fallback) {
        return getString("messages." + path, fallback).replace("%prefix%", getPrefix());
    }

    public List<String> getMessageList(String path, List<String> fallback) {
        List<String> list = config == null ? List.of() : config.getStringList("messages." + path);
        return list.isEmpty() ? fallback : list;
    }

    public List<String> getFormatList(String path, List<String> fallback) {
        List<String> list = config == null ? List.of() : config.getStringList("formats." + path);
        return list.isEmpty() ? fallback : list;
    }

    public String getFormat(String path, String fallback) {
        return getString("formats." + path, fallback);
    }

    public String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }

    private long parseDuration(String input, long fallback) {
        if (input == null || input.isBlank()) {
            return fallback;
        }

        String value = input.trim().toLowerCase(Locale.ROOT);
        long multiplier = 1000L;

        try {
            if (value.endsWith("s")) {
                value = value.substring(0, value.length() - 1);
            } else if (value.endsWith("m")) {
                value = value.substring(0, value.length() - 1);
                multiplier = 60_000L;
            } else if (value.endsWith("h")) {
                value = value.substring(0, value.length() - 1);
                multiplier = 3_600_000L;
            } else if (value.endsWith("d")) {
                value = value.substring(0, value.length() - 1);
                multiplier = 86_400_000L;
            }

            return Math.max(0L, Long.parseLong(value) * multiplier);
        } catch (NumberFormatException exception) {
            plugin.getLogger().warning("VelioraReport: cooldown tidak valid: " + input + ". Fallback ke 60s.");
            return fallback;
        }
    }

    private String getString(String path, String fallback) {
        if (config == null || !config.contains(path)) {
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
}
