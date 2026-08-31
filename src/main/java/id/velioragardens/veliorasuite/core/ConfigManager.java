package id.velioragardens.veliorasuite.core;

import id.velioragardens.veliorasuite.VelioraSuite;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ConfigManager {

    private static final List<String> MODULE_CONFIGS = List.of(
            "adminmonitor",
            "autotool",
            "deathlaugh",
            "guide",
            "menu",
            "loginsecurity",
            "team",
            "kits",
            "report",
            "race",
            "announcement",
            "biome",
            "fishing",
            "boss",
            "security",
            "chat",
            "warp",
            "trader",
            "pets",
            "adventure",
            "notifications"
    );

    private final VelioraSuite plugin;
    private FileConfiguration modulesConfig;

    public ConfigManager(VelioraSuite plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.createFolder("modules");
        plugin.createFolder("database");
        plugin.createFolder("data");

        plugin.saveDefaultConfig();
        plugin.saveResourceIfNotExists("messages.yml");
        plugin.saveResourceIfNotExists("modules.yml");
        plugin.saveResourceIfNotExists("database/schema.sql");
        mergeModuleDefaults();

        for (String moduleName : MODULE_CONFIGS) {
            plugin.saveResourceIfNotExists("modules/" + moduleName + ".yml");
        }

        applyBrandThemeV1();
        reload();
    }

    private void mergeModuleDefaults() {
        File modulesFile = new File(plugin.getDataFolder(), "modules.yml");
        YamlConfiguration installed = YamlConfiguration.loadConfiguration(modulesFile);
        try (var stream = plugin.getResource("modules.yml")) {
            if (stream == null) return;
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8));
            if (!defaults.isConfigurationSection("modules")) return;
            boolean changed = false;
            for (String key : defaults.getConfigurationSection("modules").getKeys(false)) {
                String path = "modules." + key;
                if (installed.contains(path)) continue;
                installed.set(path, defaults.getBoolean(path));
                changed = true;
            }
            if (changed) installed.save(modulesFile);
        } catch (IOException exception) {
            plugin.getLogger().warning("Gagal menambahkan default module baru: " + exception.getMessage());
        }
    }

    private void applyBrandThemeV1() {
        if (plugin.getConfig().getInt("settings.brand-version", 0) >= 2) return;
        String[][] modules = {
                {"adminmonitor", "MONITOR"}, {"announcement", "NEWS"}, {"boss", "BOSS"},
                {"chat", "CHAT"}, {"fishing", "FISHING"}, {"kits", "KITS"},
                {"loginsecurity", "LOGIN"}, {"menu", "MENU"}, {"pets", "PETS"},
                {"report", "REPORT"}, {"security", "SECURITY"}, {"team", "TEAM"},
                {"trader", "TRADER"}, {"warp", "WARP"}
        };
        for (String[] module : modules) {
            File file = new File(plugin.getDataFolder(), "modules/" + module[0] + ".yml");
            if (!file.exists()) continue;
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            yaml.set("settings.prefix", "&7[&eVELIORA &a" + module[1] + "&7] &r");
            try { yaml.save(file); }
            catch (IOException exception) { plugin.getLogger().warning("Gagal menerapkan tema ke " + module[0] + ": " + exception.getMessage()); }
        }
        plugin.getConfig().set("settings.prefix", "&7[&eVELIORA &aSUITE&7] &r");
        plugin.getConfig().set("settings.brand-version", 2);
        plugin.saveConfig();
    }

    public void reload() {
        plugin.reloadConfig();

        File modulesFile = new File(plugin.getDataFolder(), "modules.yml");
        this.modulesConfig = YamlConfiguration.loadConfiguration(modulesFile);
    }

    public FileConfiguration getModulesConfig() {
        return modulesConfig;
    }

    public boolean isModuleEnabled(String moduleName) {
        if (moduleName.equalsIgnoreCase("core")) {
            return true;
        }

        return modulesConfig != null && modulesConfig.getBoolean("modules." + moduleName.toLowerCase(), false);
    }

    /**
     * Returns the module names from the same file used by {@link #isModuleEnabled(String)}.
     * Keeping this here prevents commands from accidentally reading the root config.yml.
     */
    public Set<String> getConfiguredModuleNames() {
        if (modulesConfig == null || !modulesConfig.isConfigurationSection("modules")) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(new LinkedHashSet<>(modulesConfig.getConfigurationSection("modules").getKeys(false)));
    }
}
