package id.velioragardens.veliorasuite;

import id.velioragardens.veliorasuite.command.VelioraCommand;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class VelioraSuite extends JavaPlugin {

    private static VelioraSuite instance;

    public static VelioraSuite getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        saveResourceIfNotExists("messages.yml");
        saveResourceIfNotExists("database/schema.sql");

        createFolder("modules");
        createFolder("database");

        registerCommands();

        getLogger().info("VelioraSuite core enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("VelioraSuite core disabled.");
    }

    public void reloadSuite() {
        reloadConfig();
        getLogger().info("VelioraSuite core reloaded.");
    }

    private void registerCommands() {
        PluginCommand command = getCommand("veliorasuite");

        if (command == null) {
            getLogger().warning("Command /veliorasuite tidak ditemukan di plugin.yml.");
            return;
        }

        VelioraCommand velioraCommand = new VelioraCommand(this);
        command.setExecutor(velioraCommand);
        command.setTabCompleter(velioraCommand);
    }

    private void saveResourceIfNotExists(String path) {
        File file = new File(getDataFolder(), path);

        if (!file.exists()) {
            saveResource(path, false);
        }
    }

    private void createFolder(String folderName) {
        File folder = new File(getDataFolder(), folderName);

        if (!folder.exists()) {
            boolean created = folder.mkdirs();

            if (!created) {
                getLogger().warning("Gagal membuat folder: " + folderName);
            }
        }
    }
}
