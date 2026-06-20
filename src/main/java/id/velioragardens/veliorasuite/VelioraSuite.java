package id.velioragardens.veliorasuite;

import id.velioragardens.veliorasuite.command.VelioraCommand;
import id.velioragardens.veliorasuite.config.ConfigManager;
import id.velioragardens.veliorasuite.database.DatabaseManager;
import id.velioragardens.veliorasuite.hook.HookManager;
import id.velioragardens.veliorasuite.module.ModuleManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class VelioraSuite extends JavaPlugin {

    private static VelioraSuite instance;

    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private HookManager hookManager;
    private ModuleManager moduleManager;

    public static VelioraSuite getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultFiles();

        this.configManager = new ConfigManager(this);
        this.configManager.loadAll();

        this.databaseManager = new DatabaseManager(this);
        this.databaseManager.connect();

        this.hookManager = new HookManager(this);
        this.hookManager.loadHooks();

        registerMainCommand();

        this.moduleManager = new ModuleManager(this);
        this.moduleManager.loadModules();

        getLogger().info("VelioraSuite enabled.");
    }

    @Override
    public void onDisable() {
        if (moduleManager != null) {
            moduleManager.unloadModules();
        }

        if (databaseManager != null) {
            databaseManager.disconnect();
        }

        getLogger().info("VelioraSuite disabled.");
    }

    public void reloadSuite() {
        reloadConfig();

        if (configManager != null) {
            configManager.reloadAll();
        }

        if (hookManager != null) {
            hookManager.loadHooks();
        }

        if (moduleManager != null) {
            moduleManager.reloadModules();
        }
    }

    private void registerMainCommand() {
        PluginCommand command = getCommand("veliorasuite");

        if (command == null) {
            getLogger().warning("Command /veliorasuite tidak ditemukan di plugin.yml.");
            return;
        }

        command.setExecutor(new VelioraCommand(this));
    }

    private void saveDefaultFiles() {
        saveDefaultConfig();

        List<String> resources = List.of(
                "messages.yml",
                "modules/anti.yml",
                "modules/clearlag.yml",
                "modules/quest.yml",
                "modules/skills.yml",
                "modules/trader.yml",
                "modules/fishing.yml",
                "modules/boss.yml",
                "modules/rewards.yml",
                "modules/chat.yml",
                "modules/team.yml",
                "modules/guide.yml",
                "modules/security.yml",
                "modules/login.yml",
                "modules/report.yml",
                "modules/announcement.yml",
                "modules/kits.yml",
                "database/schema.sql"
        );

        for (String resource : resources) {
            saveResourceIfNotExists(resource);
        }
    }

    public void saveResourceIfNotExists(String path) {
        if (!getDataFolder().toPath().resolve(path).toFile().exists()) {
            saveResource(path, false);
        }
    }

    public ConfigManager getConfigManager() { return configManager; }
    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public HookManager getHookManager() { return hookManager; }
    public ModuleManager getModuleManager() { return moduleManager; }
}
