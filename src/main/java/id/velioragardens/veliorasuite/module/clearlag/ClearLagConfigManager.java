package id.velioragardens.veliorasuite.module.clearlag;

import id.velioragardens.veliorasuite.VelioraSuite;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class ClearLagConfigManager {

    private final VelioraSuite plugin;
    private FileConfiguration config;

    public ClearLagConfigManager(VelioraSuite plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveResourceIfNotExists("modules/clearlag.yml");
        File file = new File(plugin.getDataFolder(), "modules/clearlag.yml");
        this.config = YamlConfiguration.loadConfiguration(file);
    }

    public boolean isEnabled() { return getBoolean("settings.enabled", true); }
    public String getPrefix() { return getString("settings.prefix", "&8[&aVelioraClearLag&8] "); }
    public boolean isAutoClearEnabled() { return getBoolean("settings.auto-clear.enabled", true); }
    public int getAutoClearIntervalSeconds() { return Math.max(10, getInt("settings.auto-clear.interval-seconds", 300)); }
    public List<Integer> getWarningSeconds() {
        List<Integer> warnings = config == null ? List.of() : config.getIntegerList("settings.auto-clear.warning-seconds");
        return warnings.isEmpty() ? List.of(60, 30, 10, 5, 4, 3, 2, 1) : warnings;
    }

    public boolean isItemCleanerEnabled() { return getBoolean("settings.item-cleaner.enabled", true); }
    public boolean isRemoveDroppedItems() { return getBoolean("settings.item-cleaner.remove-dropped-items", true); }
    public boolean isIgnoreNamedItems() { return getBoolean("settings.item-cleaner.ignore-named-items", true); }
    public boolean isIgnoreLoreItems() { return getBoolean("settings.item-cleaner.ignore-lore-items", true); }
    public boolean isIgnoreEnchantedItems() { return getBoolean("settings.item-cleaner.ignore-enchanted-items", true); }
    public boolean isIgnorePluginMetadataItems() { return getBoolean("settings.item-cleaner.ignore-plugin-metadata-items", true); }

    public Set<Material> getIgnoredMaterials() {
        List<String> names = config == null ? List.of() : config.getStringList("settings.item-cleaner.ignored-materials");
        if (names.isEmpty()) names = defaultIgnoredMaterials();
        Set<Material> materials = new LinkedHashSet<>();
        for (String name : names) {
            Material material = Material.matchMaterial(name == null ? "" : name.trim().toUpperCase(Locale.ROOT));
            if (material != null) materials.add(material);
        }
        return materials;
    }

    public boolean isMobCleanerEnabled() { return getBoolean("settings.mob-cleaner.enabled", true); }
    public boolean isRemoveHostileMobs() { return getBoolean("settings.mob-cleaner.remove-hostile-mobs", true); }
    public boolean isRemovePassiveMobs() { return getBoolean("settings.mob-cleaner.remove-passive-mobs", false); }
    public boolean isIgnoreNamedMobs() { return getBoolean("settings.mob-cleaner.ignore-named-mobs", true); }
    public boolean isIgnoreTamedAnimals() { return getBoolean("settings.mob-cleaner.ignore-tamed-animals", true); }

    public Set<EntityType> getIgnoredEntityTypes() {
        List<String> names = config == null ? List.of() : config.getStringList("settings.mob-cleaner.ignored-entity-types");
        if (names.isEmpty()) names = List.of("PLAYER", "VILLAGER", "ARMOR_STAND", "WOLF", "CAT", "HORSE", "DONKEY", "MULE", "LLAMA", "PARROT");
        Set<EntityType> types = new LinkedHashSet<>();
        for (String name : names) {
            try { types.add(EntityType.valueOf(name.trim().toUpperCase(Locale.ROOT))); } catch (Exception ignored) { }
        }
        return types;
    }

    public boolean isProjectileCleanerEnabled() { return getBoolean("settings.projectile-cleaner.enabled", true); }
    public boolean isProjectileAutoClearEnabled() { return getBoolean("settings.projectile-cleaner.auto-clear", false); }
    public Set<EntityType> getIgnoredProjectileTypes() {
        List<String> names = config == null ? List.of() : config.getStringList("settings.projectile-cleaner.ignored-projectiles");
        if (names.isEmpty()) names = List.of("TRIDENT");
        Set<EntityType> types = new LinkedHashSet<>();
        for (String name : names) {
            try { types.add(EntityType.valueOf(name.trim().toUpperCase(Locale.ROOT))); } catch (Exception ignored) { }
        }
        return types;
    }

    public String getAdminPermission() { return getString("permissions.admin", "veliorasuite.clearlag.admin"); }
    public String getReloadPermission() { return getString("permissions.reload", "veliorasuite.clearlag.reload"); }
    public String getClearPermission() { return getString("permissions.clear", "veliorasuite.clearlag.clear"); }
    public String getStatusPermission() { return getString("permissions.status", "veliorasuite.clearlag.status"); }
    public String getBypassPermission() { return getString("permissions.bypass", "veliorasuite.clearlag.bypass"); }

    public boolean hasAdminPermission(CommandSender sender) { return sender.hasPermission(getAdminPermission()) || sender.isOp(); }
    public boolean hasReloadPermission(CommandSender sender) { return sender.hasPermission(getReloadPermission()) || hasAdminPermission(sender); }
    public boolean hasClearPermission(CommandSender sender) { return sender.hasPermission(getClearPermission()) || hasAdminPermission(sender); }
    public boolean hasStatusPermission(CommandSender sender) { return sender.hasPermission(getStatusPermission()) || hasAdminPermission(sender); }

    public String getMessage(String path, String fallback) { return getString("messages." + path, fallback).replace("%prefix%", getPrefix()); }
    public List<String> getMessageList(String path, List<String> fallback) {
        List<String> list = config == null ? List.of() : config.getStringList("messages." + path);
        return list.isEmpty() ? fallback : list;
    }
    public String color(String text) { return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text); }

    private List<String> defaultIgnoredMaterials() {
        return List.of("DIAMOND", "EMERALD", "NETHERITE_INGOT", "NETHERITE_SCRAP", "SHULKER_BOX", "WHITE_SHULKER_BOX", "BLACK_SHULKER_BOX", "BLUE_SHULKER_BOX", "RED_SHULKER_BOX", "GREEN_SHULKER_BOX", "YELLOW_SHULKER_BOX", "PURPLE_SHULKER_BOX", "PINK_SHULKER_BOX", "ORANGE_SHULKER_BOX", "CYAN_SHULKER_BOX", "LIGHT_BLUE_SHULKER_BOX", "LIME_SHULKER_BOX", "MAGENTA_SHULKER_BOX", "BROWN_SHULKER_BOX", "GRAY_SHULKER_BOX", "LIGHT_GRAY_SHULKER_BOX");
    }
    private String getString(String path, String fallback) { return config == null || !config.contains(path) ? fallback : config.getString(path, fallback); }
    private boolean getBoolean(String path, boolean fallback) { return config == null || !config.contains(path) ? fallback : config.getBoolean(path, fallback); }
    private int getInt(String path, int fallback) { return config == null || !config.contains(path) ? fallback : config.getInt(path, fallback); }
}
