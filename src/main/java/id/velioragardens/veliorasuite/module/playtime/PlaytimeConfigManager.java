package id.velioragardens.veliorasuite.module.playtime;

import id.velioragardens.veliorasuite.VelioraSuite;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.List;

public final class PlaytimeConfigManager {

    private final VelioraSuite plugin;
    private FileConfiguration config;

    public PlaytimeConfigManager(VelioraSuite plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveResourceIfNotExists("modules/playtime.yml");
        File file = new File(plugin.getDataFolder(), "modules/playtime.yml");
        this.config = YamlConfiguration.loadConfiguration(file);
    }

    public boolean isEnabled() { return bool("settings.enabled", true); }
    public int reconnectGraceMinutes() { return Math.max(1, integer("settings.reconnect-grace-minutes", 10)); }
    public int topSize() { return Math.max(3, Math.min(20, integer("settings.top-size", 10))); }
    public String prefix() { return str("messages.prefix", "&8[&bVelioraPlaytime&8] "); }
    public String usePermission() { return str("permissions.use", "veliorasuite.playtime.use"); }
    public String topPermission() { return str("permissions.top", "veliorasuite.playtime.top"); }
    public String reloadPermission() { return str("permissions.reload", "veliorasuite.playtime.reload"); }

    public String message(String key, String fallback) {
        return str("messages." + key, fallback).replace("%prefix%", prefix());
    }

    public List<String> messageList(String key, List<String> fallback) {
        if (config == null) return fallback;
        List<String> list = config.getStringList("messages." + key);
        return list.isEmpty() ? fallback : list;
    }

    public String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }

    private String str(String path, String fallback) {
        return config == null || !config.contains(path) ? fallback : config.getString(path, fallback);
    }

    private boolean bool(String path, boolean fallback) {
        return config == null || !config.contains(path) ? fallback : config.getBoolean(path, fallback);
    }

    private int integer(String path, int fallback) {
        return config == null || !config.contains(path) ? fallback : config.getInt(path, fallback);
    }
}
