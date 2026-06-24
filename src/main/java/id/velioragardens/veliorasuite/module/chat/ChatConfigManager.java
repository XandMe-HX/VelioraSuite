package id.velioragardens.veliorasuite.module.chat;

import id.velioragardens.veliorasuite.VelioraSuite;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.List;
import java.util.Locale;

public final class ChatConfigManager {

    private final VelioraSuite plugin;
    private FileConfiguration config;

    public ChatConfigManager(VelioraSuite plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveResourceIfNotExists("modules/chat.yml");
        File file = new File(plugin.getDataFolder(), "modules/chat.yml");
        this.config = YamlConfiguration.loadConfiguration(file);
    }

    public boolean isEnabled() { return getBoolean("settings.enabled", true); }
    public String getPrefix() { return getString("settings.prefix", "&8[&bVelioraChat&8] "); }
    public boolean isFormatterEnabled() { return getBoolean("settings.formatter-enabled", true); }
    public boolean isEssentialsMode() { return getBoolean("settings.essentials-mode", false); }
    public boolean isUsePlaceholderApi() { return getBoolean("settings.use-placeholderapi", true); }
    public boolean isTeamTagPlaceholderEnabled() { return getBoolean("settings.team-tag-placeholder-enabled", true); }
    public boolean isProtectionEnabled() { return getBoolean("settings.protection-enabled", true); }
    public boolean isCooldownEnabled() { return getBoolean("settings.cooldown.enabled", true); }
    public int getCooldownSeconds() { return Math.max(0, getInt("settings.cooldown.seconds", 2)); }
    public boolean isAntiRepeatEnabled() { return getBoolean("settings.anti-repeat.enabled", true); }
    public int getMaxRepeat() { return Math.max(1, getInt("settings.anti-repeat.max-repeat", 2)); }
    public boolean isAntiCapsEnabled() { return getBoolean("settings.anti-caps.enabled", true); }
    public int getMaxCapsPercent() { return Math.max(1, Math.min(100, getInt("settings.anti-caps.max-caps-percent", 70))); }
    public int getCapsMinLength() { return Math.max(1, getInt("settings.anti-caps.min-length", 8)); }
    public String getCapsAction() { return getString("settings.anti-caps.action", "LOWERCASE").toUpperCase(Locale.ROOT); }
    public boolean isWordFilterEnabled() { return getBoolean("settings.word-filter.enabled", true); }
    public String getWordFilterAction() { return getString("settings.word-filter.action", "REPLACE").toUpperCase(Locale.ROOT); }
    public String getReplacement() { return getString("settings.word-filter.replacement", "***"); }
    public List<String> getBlockedWords() { return config == null ? List.of() : config.getStringList("settings.word-filter.blocked-words"); }

    public String getUsePermission() { return getString("permissions.use", "veliorasuite.chat.use"); }
    public String getAdminPermission() { return getString("permissions.admin", "veliorasuite.chat.admin"); }
    public String getReloadPermission() { return getString("permissions.reload", "veliorasuite.chat.reload"); }
    public String getBypassCooldownPermission() { return getString("permissions.bypass-cooldown", "veliorasuite.chat.bypasscooldown"); }
    public String getBypassFilterPermission() { return getString("permissions.bypass-filter", "veliorasuite.chat.bypassfilter"); }

    public String getPublicChatFormat() { return getString("formats.public-chat", "%veliorateam_tag%&7%luckperms_prefix%&f%player%&7: &f%message%"); }
    public String getTeamTagEmpty() { return getString("formats.team-tag-empty", ""); }
    public String getLuckPermsPrefixEmpty() { return getString("formats.luckperms-prefix-empty", ""); }

    public String getMessage(String path, String fallback) {
        return getString("messages." + path, fallback).replace("%prefix%", getPrefix());
    }

    public List<String> getMessageList(String path, List<String> fallback) {
        List<String> list = config == null ? List.of() : config.getStringList("messages." + path);
        return list.isEmpty() ? fallback : list;
    }

    public String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }

    private String getString(String path, String fallback) {
        if (config == null || !config.contains(path)) return fallback;
        return config.getString(path, fallback);
    }

    private boolean getBoolean(String path, boolean fallback) {
        if (config == null || !config.contains(path)) return fallback;
        return config.getBoolean(path, fallback);
    }

    private int getInt(String path, int fallback) {
        if (config == null || !config.contains(path)) return fallback;
        return config.getInt(path, fallback);
    }
}
