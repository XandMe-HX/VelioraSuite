package id.velioragardens.veliorasuite.module.security;

import id.velioragardens.veliorasuite.VelioraSuite;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.ConfigurationSection;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

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
        try (InputStream input = plugin.getResource("modules/security.yml")) {
            if (input != null) {
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(input, StandardCharsets.UTF_8));
                config.setDefaults(defaults);
                config.options().copyDefaults(true);
                config.save(file);
            }
        } catch (IOException exception) {
            plugin.getLogger().warning("VelioraSecurity: gagal menggabungkan default baru: " + exception.getMessage());
        }
        migrateCombatGuardV3(file);
        migrateXrayEnforcementV2(file);
        migrateBedrockPrefixV3(file);
        migrateNetworkGuardV1(file);
    }

    private void migrateCombatGuardV3(File file) {
        if (config.getInt("settings.combat-guard.config-version", 0) >= 3) return;
        config.set("settings.combat-guard.config-version", 3);
        config.set("settings.combat-guard.java-max-reach", 3.65D);
        config.set("settings.combat-guard.java-hard-reach-extra", 0.55D);
        config.set("settings.combat-guard.bedrock-max-reach", 4.50D);
        config.set("settings.combat-guard.bedrock-hard-reach-extra", 0.70D);
        config.set("settings.combat-guard.minimum-facing-dot", -0.10D);
        config.set("settings.combat-guard.bedrock-minimum-facing-dot", -0.25D);
        config.set("settings.combat-guard.bedrock-strong-signals-required", 2);
        config.set("settings.combat-guard.minimum-tps-for-geometry", 18.0D);
        config.set("settings.combat-guard.multi-target-count", 4);
        config.set("settings.combat-guard.maximum-cps", 25);
        config.set("settings.combat-guard.minimum-event-score", 12);
        config.set("settings.combat-guard.score-decay-per-second", 7.0D);
        config.set("settings.combat-guard.stage-1-score", 110);
        config.set("settings.combat-guard.stage-2-score", 220);
        config.set("settings.combat-guard.stage-3-score", 310);
        try {
            config.save(file);
            plugin.getLogger().info("CombatGuard v3: toleransi Java/Bedrock dan sweeping PvP diterapkan.");
        } catch (IOException exception) {
            plugin.getLogger().warning("CombatGuard: gagal menyimpan migrasi v2: " + exception.getMessage());
        }
    }

    private void migrateXrayEnforcementV2(File file) {
        if (config.getInt("settings.xray-enforcement.config-version", 0) >= 2) return;
        config.set("settings.xray-enforcement.config-version", 2);
        config.set("settings.xray-enforcement.strike-cooldown-minutes", 2);
        config.set("settings.xray-enforcement.first-ban-days", 5);
        try { config.save(file); }
        catch (IOException exception) { plugin.getLogger().warning("VelioraOreWatch: gagal menyimpan migrasi v2: " + exception.getMessage()); }
    }

    private void migrateBedrockPrefixV3(File file) {
        if (config.getInt("settings.name-protection.config-version", 0) >= 3) return;
        config.set("settings.name-protection.config-version", 3);
        config.set("settings.name-protection.bedrock-prefixes", List.of("_"));
        config.set("settings.name-protection.reserve-bedrock-prefix-for-floodgate", true);
        try { config.save(file); }
        catch (IOException exception) { plugin.getLogger().warning("VelioraSecurity: gagal menyimpan prefix Bedrock _: " + exception.getMessage()); }
    }

    private void migrateNetworkGuardV1(File file) {
        if (config.getInt("settings.network-guard.config-version", 0) >= 1) return;
        config.set("settings.network-guard.config-version", 1);
        config.set("settings.join-protection.max-joins-per-ip", 30);
        config.set("settings.identity-protection.same-ip-different-names.enabled", false);
        config.set("settings.network-guard.economy.block-same-network-payments", false);
        try {
            config.save(file);
            plugin.getLogger().info("VelioraGuard Network: jaringan bersama kini hanya diaudit, bukan dihukum.");
        } catch (IOException exception) {
            plugin.getLogger().warning("VelioraGuard Network: gagal menyimpan migrasi: " + exception.getMessage());
        }
    }

    public FileConfiguration config() { return config; }

    public boolean isEnabled() { return bool("settings.enabled", true); }
    public String getPrefix() { return str("settings.prefix", "&8[&cVelioraSecurity&8] "); }

    public boolean isAlertsEnabled() { return bool("settings.alerts.enabled", true); }
    public int getAlertCooldownSeconds() { return Math.max(0, integer("settings.alerts.cooldown-seconds", 5)); }
    public int getMaxRecentAlerts() { return Math.max(1, integer("settings.alerts.max-recent-alerts", 20)); }

    public boolean isJoinProtectionEnabled() { return bool("settings.join-protection.enabled", true); }
    public int getMaxJoinsPerIp() { return Math.max(1, integer("settings.join-protection.max-joins-per-ip", 5)); }
    public int getJoinWindowSeconds() { return Math.max(1, integer("settings.join-protection.window-seconds", 30)); }
    public String getJoinKickMessage() { return str("settings.join-protection.kick-message", "&cTerlalu banyak join dari koneksi yang sama. Coba lagi nanti."); }

    public boolean isNetworkGuardEnabled() { return bool("settings.network-guard.enabled", true); }
    public int getNetworkGuardWindowSeconds() { return Math.max(10, integer("settings.network-guard.connection-burst.window-seconds", 60)); }
    public int getNetworkGuardJavaJoinLimit() { return Math.max(2, integer("settings.network-guard.connection-burst.java-max-joins", 12)); }
    public int getNetworkGuardBedrockJoinLimit() { return Math.max(2, integer("settings.network-guard.connection-burst.bedrock-max-joins", 20)); }
    public int getNetworkGuardAccountWindowSeconds() { return Math.max(60, integer("settings.network-guard.new-account-burst.window-seconds", 600)); }
    public int getNetworkGuardJavaNewAccountLimit() { return Math.max(2, integer("settings.network-guard.new-account-burst.java-max-unique-names", 8)); }
    public int getNetworkGuardBedrockNewAccountLimit() { return Math.max(2, integer("settings.network-guard.new-account-burst.bedrock-max-unique-names", 14)); }
    public String getNetworkGuardBurstKickMessage() { return str("settings.network-guard.connection-burst.kick-message", "&cTerlalu banyak koneksi dari jaringan ini. Tunggu sebentar lalu coba lagi."); }
    public boolean isNetworkGuardEconomySameNetworkBlockEnabled() { return bool("settings.network-guard.economy.block-same-network-payments", false); }

    public boolean isNameProtectionEnabled() { return bool("settings.name-protection.enabled", true); }
    public String getNameRegex() { return str("settings.name-protection.name-regex", "^[a-zA-Z0-9_]{3,16}$"); }
    public boolean isAllowBedrockPrefix() { return bool("settings.name-protection.allow-bedrock-prefix", true); }
    public List<String> getBedrockPrefixes() {
        List<String> list = config == null ? List.of() : config.getStringList("settings.name-protection.bedrock-prefixes");
        return list.isEmpty() ? List.of("_") : list;
    }
    public boolean isReserveBedrockPrefix() { return bool("settings.name-protection.reserve-bedrock-prefix-for-floodgate", true); }
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

    public boolean isXrayEnforcementEnabled() { return bool("settings.xray-enforcement.enabled", true); }
    public int getXrayStrikeCooldownMinutes() { return Math.max(1, integer("settings.xray-enforcement.strike-cooldown-minutes", 10)); }
    public int getXrayVisualAlertLimit() { return Math.max(1, integer("settings.xray-enforcement.visual-alert-limit", 3)); }
    public int getXrayBlindnessSeconds() { return Math.max(1, integer("settings.xray-enforcement.first-warning-blindness-seconds", 10)); }
    public int getXrayFirstBanDays() { return Math.max(1, integer("settings.xray-enforcement.first-ban-days", 3)); }
    public int getXrayRepeatBanDays() { return Math.max(getXrayFirstBanDays(), integer("settings.xray-enforcement.repeat-ban-days", 15)); }
    public String getXrayAppealContact() { return str("settings.xray-enforcement.appeal-contact", "WhatsApp Owner"); }
    // Kept for compatibility with the old owner-confirm commands.
    public int getXrayBanDays() { return getXrayRepeatBanDays(); }
    public int getXrayConfirmationMinutes() { return Math.max(5, integer("settings.xray-enforcement.confirmation-expiry-minutes", 60)); }

    public boolean isAntiDupeEnabled() { return bool("settings.anti-dupe.enabled", true); }
    public boolean isAntiDupeKickEnabled() { return bool("settings.anti-dupe.kick-on-quarantine", true); }
    public int getAntiDupeScanCooldownTicks() { return Math.max(1, integer("settings.anti-dupe.scan-cooldown-ticks", 10)); }
    public Map<Material, Integer> getAntiDupeInventoryLimits() {
        Map<Material, Integer> limits = new LinkedHashMap<>();
        ConfigurationSection section = config == null ? null : config.getConfigurationSection("settings.anti-dupe.inventory-limits");
        if (section == null) {
            limits.put(Material.SPAWNER, 8);
            limits.put(Material.DIAMOND, 512);
            limits.put(Material.DIAMOND_BLOCK, 64);
            limits.put(Material.DIAMOND_ORE, 128);
            limits.put(Material.DEEPSLATE_DIAMOND_ORE, 128);
            limits.put(Material.ANCIENT_DEBRIS, 256);
            limits.put(Material.NETHERITE_INGOT, 128);
            limits.put(Material.NETHERITE_BLOCK, 16);
            return limits;
        }
        for (String key : section.getKeys(false)) {
            Material material = Material.matchMaterial(key);
            int limit = section.getInt(key, 0);
            if (material != null && limit > 0) limits.put(material, limit);
        }
        return limits;
    }

    public String getAdminPermission() { return str("permissions.admin", "veliorasuite.security.admin"); }
    public String getReloadPermission() { return str("permissions.reload", "veliorasuite.security.reload"); }
    public String getAlertsPermission() { return str("permissions.alerts", "veliorasuite.security.alerts"); }
    public String getBypassPermission() { return str("permissions.bypass", "veliorasuite.security.bypass"); }
    public String getOwnerPermission() { return str("permissions.owner", "veliorasuite.security.owner"); }

    public boolean hasAdmin(CommandSender sender) { return sender.hasPermission(getAdminPermission()) || sender.isOp(); }
    public boolean hasReload(CommandSender sender) { return sender.hasPermission(getReloadPermission()) || hasAdmin(sender); }
    public boolean hasAlerts(CommandSender sender) { return sender.hasPermission(getAlertsPermission()) || hasAdmin(sender); }
    public boolean hasBypass(CommandSender sender) { return sender.hasPermission(getBypassPermission()) || hasAdmin(sender); }
    public boolean hasOwner(CommandSender sender) { return sender.hasPermission(getOwnerPermission()) || sender.isOp(); }

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
