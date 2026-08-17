package id.velioragardens.veliorasuite.core;

import id.velioragardens.veliorasuite.VelioraSuite;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ConfigManager {

    private static final List<String> MODULE_CONFIGS = List.of(
            "guide",
            "menu",
            "loginsecurity",
            "team",
            "kits",
            "report",
            "announcement",
            "fishing",
            "skills",
            "quest",
            "boss",
            "security",
            "chat",
            "warp",
            "trader",
            "pets"
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

        for (String moduleName : MODULE_CONFIGS) {
            plugin.saveResourceIfNotExists("modules/" + moduleName + ".yml");
        }

        applyBrandThemeV1();
        reload();
    }

    private void applyBrandThemeV1() {
        if (plugin.getConfig().getInt("settings.brand-version", 0) >= 1) return;
        String[][] modules = {
                {"adminmonitor", "MONITOR"}, {"announcement", "NEWS"}, {"boss", "BOSS"},
                {"chat", "CHAT"}, {"fishing", "FISHING"}, {"kits", "KITS"},
                {"loginsecurity", "LOGIN"}, {"menu", "MENU"}, {"pets", "PETS"},
                {"quest", "QUEST"}, {"report", "REPORT"}, {"security", "SECURITY"},
                {"skills", "SKILLS"}, {"team", "TEAM"}, {"trader", "TRADER"}, {"warp", "WARP"}
        };
        for (String[] module : modules) {
            File file = new File(plugin.getDataFolder(), "modules/" + module[0] + ".yml");
            if (!file.exists()) continue;
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            yaml.set("settings.prefix", "&6&l[ &e&lVELIORA &a&l" + module[1] + " &6&l] &r");
            try { yaml.save(file); }
            catch (IOException exception) { plugin.getLogger().warning("Gagal menerapkan tema ke " + module[0] + ": " + exception.getMessage()); }
        }
        plugin.getConfig().set("settings.prefix", "&6&l[ &e&lVELIORA &a&lSUITE &6&l] &r");
        plugin.getConfig().set("settings.brand-version", 1);
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
