package id.velioragardens.veliorasuite.module.skills;

import id.velioragardens.veliorasuite.VelioraSuite;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.List;

public final class SkillsConfigManager {

    private final VelioraSuite plugin;
    private FileConfiguration config;

    public SkillsConfigManager(VelioraSuite plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveResourceIfNotExists("modules/skills.yml");
        File file = new File(plugin.getDataFolder(), "modules/skills.yml");
        this.config = YamlConfiguration.loadConfiguration(file);
    }

    public boolean isEnabled() { return bool("settings.enabled", true); }
    public String getPrefix() { return str("settings.prefix", "&8[&bVelioraSkills&8] "); }
    public int getDefaultMana() { return Math.max(0, integer("settings.mana.default-mana", 10)); }
    public int getDefaultMaxMana() { return Math.max(1, integer("settings.mana.default-max-mana", 10)); }
    public int getMinMana() { return Math.max(0, integer("settings.mana.min-mana", 0)); }
    public boolean isDailyResetEnabled() { return bool("settings.mana.daily-reset.enabled", true); }
    public String getResetTime() { return str("settings.mana.daily-reset.reset-time", "00:00"); }
    public boolean isResetOnJoinIfMissed() { return bool("settings.mana.daily-reset.reset-on-join-if-missed", true); }
    public boolean isQuestCostEnabled() { return bool("settings.mana.quest-cost.enabled", true); }
    public int getQuestCost1To4() { return Math.max(0, integer("settings.mana.quest-cost.tiers.level-1-4", 1)); }
    public int getQuestCost5To9() { return Math.max(0, integer("settings.mana.quest-cost.tiers.level-5-9", 2)); }
    public int getQuestCost10To14() { return Math.max(0, integer("settings.mana.quest-cost.tiers.level-10-14", 3)); }
    public int getQuestCost15Plus() { return Math.max(0, integer("settings.mana.quest-cost.tiers.level-15-plus", 4)); }

    public boolean isActionBarEnabled() { return bool("settings.actionbar.enabled", true); }
    public int getActionBarIntervalTicks() { return Math.max(1, integer("settings.actionbar.interval-ticks", 20)); }
    public String getActionBarFormat() { return str("settings.actionbar.format", "&c❤ %health% &8| &e⛃ &f%vault_eco_balance_formatted% &8| &a%player_ping%ms &8| &b☯ &f%veliorasuite_mana%/%veliorasuite_mana_max%"); }
    public List<String> getDisabledWorlds() { return config == null ? List.of() : config.getStringList("settings.actionbar.disabled-worlds"); }
    public boolean isPlaceholderApiEnabled() { return bool("settings.placeholderapi.enabled", true); }
    public boolean isRegisterPlaceholders() { return bool("settings.placeholderapi.register-placeholders", true); }
    public int getManaBarLength() { return Math.max(1, integer("settings.mana-bar.length", 10)); }
    public String getManaBarFilledColor() { return str("settings.mana-bar.filled-color", "&b"); }
    public String getManaBarEmptyColor() { return str("settings.mana-bar.empty-color", "&7"); }
    public String getManaBarSymbol() { return str("settings.mana-bar.symbol", "|"); }

    public String getUsePermission() { return str("permissions.use", "veliorasuite.skills.use"); }
    public String getAdminPermission() { return str("permissions.admin", "veliorasuite.skills.admin"); }
    public String getReloadPermission() { return str("permissions.reload", "veliorasuite.skills.reload"); }
    public String getManaAdminPermission() { return str("permissions.mana-admin", "veliorasuite.skills.mana.admin"); }
    public String getBypassPermission() { return str("permissions.bypass", "veliorasuite.skills.bypass"); }

    public boolean hasUse(CommandSender sender) { return sender.hasPermission(getUsePermission()) || hasAdmin(sender); }
    public boolean hasAdmin(CommandSender sender) { return sender.hasPermission(getAdminPermission()) || sender.isOp(); }
    public boolean hasReload(CommandSender sender) { return sender.hasPermission(getReloadPermission()) || hasAdmin(sender); }
    public boolean hasManaAdmin(CommandSender sender) { return sender.hasPermission(getManaAdminPermission()) || hasAdmin(sender); }

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
