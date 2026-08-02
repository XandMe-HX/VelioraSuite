package id.velioragardens.veliorasuite.module.security;

import id.velioragardens.veliorasuite.VelioraSuite;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.List;

public final class SecurityConfigManager {

    private final VelioraSuite plugin;
    private FileConfiguration config;

    public SecurityConfigManager(VelioraSuite plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveResourceIfNotExists("modules/security.yml");
        File file = new File(plugin.getDataFolder(), "modules/security.yml");
        this.config = YamlConfiguration.loadConfiguration(file);
    }

    public boolean isEnabled() { return bool("settings.enabled", true); }
    public String getPrefix() { return str("settings.prefix", "&8[&cVelioraSecurity&8] "); }

    public boolean isAlertsEnabled() { return bool("settings.alerts.enabled", true); }
    public int getAlertCooldownSeconds() { return Math.max(0, integer("settings.alerts.cooldown-seconds", 5)); }
    public int getMaxRecentAlerts() { return Math.max(1, integer("settings.alerts.max-recent-alerts", 20)); }

    public boolean isJoinProtectionEnabled() { return bool("settings.join-protection.enabled", true); }
    public int getMaxJoinsPerIp() { return Math.max(1, integer("settings.join-protection.max-joins-per-ip", 5)); }
    public int getJoinWindowSeconds() { return Math.max(1, integer("settings.join-protection.window-seconds", 30)); }
    public String getJoinKickMessage() { return str("settings.join-protection.kick-message", "&cTerlalu banyak join dari koneksi yang sama. Coba lagi nanti."); }

    public boolean isNameProtectionEnabled() { return bool("settings.name-protection.enabled", true); }
    public String getNameRegex() { return str("settings.name-protection.name-regex", "^[a-zA-Z0-9_]{3,16}$"); }
    public boolean isAllowBedrockPrefix() { return bool("settings.name-protection.allow-bedrock-prefix", true); }
    public List<String> getBedrockPrefixes() {
        List<String> list = config == null ? List.of() : config.getStringList("settings.name-protection.bedrock-prefixes");
        return list.isEmpty() ? List.of(".", "*") : list;
    }
    public String getNameKickMessage() { return str("settings.name-protection.kick-message", "&cNama player tidak valid."); }

    public boolean isCommandProtectionEnabled() { return bool("settings.command-protection.enabled", true); }
    public int getMaxCommandLength() { return Math.max(20, integer("settings.command-protection.max-command-length", 160)); }
    public boolean isBlockControlCharacters() { return bool("settings.command-protection.block-control-characters", true); }
    public List<String> getIgnoredCommands() {
        List<String> list = config == null ? List.of() : config.getStringList("settings.command-protection.ignored-commands");
        return list.isEmpty() ? List.of("/login", "/l", "/register", "/reg", "/r", "/changepass", "/logout") : list;
    }
    public List<String> getBlockedCommands() {
        List<String> list = config == null ? List.of() : config.getStringList("settings.command-protection.blocked-commands");
        return list.isEmpty() ? List.of("/pl", "/plugins", "/ver", "/version", "/about", "/icanhasbukkit", "/bukkit:plugins", "/bukkit:pl", "/bukkit:ver", "/bukkit:version", "/minecraft:me", "/minecraft:tell", "/minecraft:msg") : list;
    }

    public boolean isTabProtectionEnabled() { return bool("settings.tab-protection.enabled", true); }
    public boolean isHideBlockedCommands() { return bool("settings.tab-protection.hide-blocked-commands", true); }

    public boolean isIdentityProtectionEnabled() { return bool("settings.identity-protection.enabled", true); }
    public boolean isSameIpDifferentNamesEnabled() { return bool("settings.identity-protection.same-ip-different-names.enabled", true); }
    public int getDifferentNamesWindowSeconds() { return Math.max(1, integer("settings.identity-protection.same-ip-different-names.window-seconds", 120)); }
    public int getDifferentNamesAlertThreshold() { return Math.max(1, integer("settings.identity-protection.same-ip-different-names.max-unique-names-before-alert", 3)); }
    public int getDifferentNamesKickThreshold() { return Math.max(1, integer("settings.identity-protection.same-ip-different-names.max-unique-names-before-kick", 5)); }
    public int getDifferentNamesTemporaryBlockSeconds() { return Math.max(1, integer("settings.identity-protection.same-ip-different-names.temporary-block-seconds", 60)); }
    public boolean isDifferentNamesAlertAction() { return bool("settings.identity-protection.same-ip-different-names.action-alert", true); }
    public boolean isDifferentNamesKickAction() { return bool("settings.identity-protection.same-ip-different-names.action-kick", true); }
    public boolean isDifferentNamesBanAction() { return bool("settings.identity-protection.same-ip-different-names.action-ban", false); }

    public boolean isSameNameRejoinEnabled() { return bool("settings.identity-protection.same-name-same-ip-rejoin.enabled", true); }
    public int getRejoinWindowSeconds() { return Math.max(1, integer("settings.identity-protection.same-name-same-ip-rejoin.window-seconds", 120)); }
    public int getRejoinAlertThreshold() { return Math.max(1, integer("settings.identity-protection.same-name-same-ip-rejoin.max-rejoins-before-alert", 6)); }
    public int getRejoinDelayThreshold() { return Math.max(1, integer("settings.identity-protection.same-name-same-ip-rejoin.max-rejoins-before-delay", 10)); }
    public int getRejoinDelaySeconds() { return Math.max(1, integer("settings.identity-protection.same-name-same-ip-rejoin.delay-seconds", 10)); }
    public boolean isRejoinAlertAction() { return bool("settings.identity-protection.same-name-same-ip-rejoin.action-alert", true); }
    public boolean isRejoinKickAction() { return bool("settings.identity-protection.same-name-same-ip-rejoin.action-kick", false); }
    public boolean isRejoinBanAction() { return bool("settings.identity-protection.same-name-same-ip-rejoin.action-ban", false); }

    public boolean isRiskScoreEnabled() { return bool("settings.risk-score.enabled", true); }
    public int getRiskAlertThreshold() { return Math.max(1, integer("settings.risk-score.alert-threshold", 40)); }
    public int getRiskKickThreshold() { return Math.max(1, integer("settings.risk-score.kick-threshold", 70)); }
    public int getRiskTemporaryBlockThreshold() { return Math.max(1, integer("settings.risk-score.temporary-block-threshold", 90)); }
    public int getRiskTemporaryBlockSeconds() { return Math.max(1, integer("settings.risk-score.temporary-block-seconds", 60)); }
    public boolean isPermanentBanEnabled() { return bool("settings.risk-score.permanent-ban-enabled", false); }

    public boolean isAiDetectionEnabled() { return bool("settings.ai-detection.enabled", false); }
    public boolean isAiAdvisoryOnly() { return bool("settings.ai-detection.advisory-only", true); }
    public boolean isAiAutoBanAllowed() { return bool("settings.ai-detection.allow-auto-ban", false); }

    public boolean isSpawnerGuardEnabled() { return bool("settings.spawner-guard.enabled", true); }
    public int getSpawnerLimitPerPlayer() { return Math.max(1, integer("settings.spawner-guard.limit-per-player", 1)); }
    public boolean isSpawnerGuardConsumeBlockedItem() { return bool("settings.spawner-guard.consume-blocked-spawner", true); }
    public int getSpawnerGuardAlertCooldownSeconds() { return Math.max(0, integer("settings.spawner-guard.alert-cooldown-seconds", 10)); }

    public String getAdminPermission() { return str("permissions.admin", "veliorasuite.security.admin"); }
    public String getReloadPermission() { return str("permissions.reload", "veliorasuite.security.reload"); }
    public String getAlertsPermission() { return str("permissions.alerts", "veliorasuite.security.alerts"); }
    public String getBypassPermission() { return str("permissions.bypass", "veliorasuite.security.bypass"); }

    public boolean hasAdmin(CommandSender sender) { return sender.hasPermission(getAdminPermission()) || sender.isOp(); }
    public boolean hasReload(CommandSender sender) { return sender.hasPermission(getReloadPermission()) || hasAdmin(sender); }
    public boolean hasAlerts(CommandSender sender) { return sender.hasPermission(getAlertsPermission()) || hasAdmin(sender); }
    public boolean hasBypass(CommandSender sender) { return sender.hasPermission(getBypassPermission()) || hasAdmin(sender); }

    public String message(String path, String fallback) { return str("messages." + path, fallback).replace("%prefix%", getPrefix()); }
    public List<String> messageList(String path, List<String> fallback) {
        List<String> list = config == null ? List.of() : config.getStringList("messages." + path);
        return list.isEmpty() ? fallback : list;
    }
    public String color(String text) { return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text); }

    private String str(String path, String fallback) { return config == null || !config.contains(path) ? fallback : config.getString(path, fallback); }
    private boolean bool(String path, boolean fallback) { return config == null || !config.contains(path) ? fallback : config.getBoolean(path, fallback); }
    private int integer(String path, int fallback) { return config == null || !config.contains(path) ? fallback : config.getInt(path, fallback); }
}
