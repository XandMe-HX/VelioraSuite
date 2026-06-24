package id.velioragardens.veliorasuite.core;

import id.velioragardens.veliorasuite.VelioraSuite;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.List;

public final class ConfigManager {

    private static final List<String> MODULE_CONFIGS = List.of(
            "guide",
            "loginsecurity",
            "team",
            "kits",
            "report",
            "announcement",
            "fishing",
            "clearlag",
            "skills",
            "quest",
            "trader",
            "boss",
            "security",
            "chat"
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

        reload();
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
}
