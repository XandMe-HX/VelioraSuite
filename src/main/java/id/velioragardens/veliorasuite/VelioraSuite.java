package id.velioragardens.veliorasuite;

import id.velioragardens.veliorasuite.command.VelioraCommand;
import id.velioragardens.veliorasuite.module.announcement.AnnouncementCommand;
import id.velioragardens.veliorasuite.module.announcement.AnnouncementManager;
import id.velioragardens.veliorasuite.module.guide.GuideCommand;
import id.velioragardens.veliorasuite.module.guide.GuideManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class VelioraSuite extends JavaPlugin {

    private static VelioraSuite instance;

    private GuideManager guideManager;
    private AnnouncementManager announcementManager;

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

        loadGuideModule();
        loadAnnouncementModule();
        registerCommands();

        getLogger().info("VelioraSuite core enabled.");
    }

    @Override
    public void onDisable() {
        if (announcementManager != null) {
            announcementManager.shutdown();
        }

        getLogger().info("VelioraSuite core disabled.");
    }

    public void reloadSuite() {
        reloadConfig();

        loadGuideModule();
        loadAnnouncementModule();
        registerCommands();

        getLogger().info("VelioraSuite core reloaded.");
    }

    private void loadGuideModule() {
        if (!getConfig().getBoolean("modules.guide", false)) {
            guideManager = null;
            return;
        }

        saveResourceIfNotExists("modules/guide.yml");

        guideManager = new GuideManager(this);
        guideManager.load();
    }

    private void loadAnnouncementModule() {
        if (announcementManager != null) {
            announcementManager.shutdown();
            announcementManager = null;
        }

        if (!getConfig().getBoolean("modules.announcement", false)) {
            return;
        }

        saveResourceIfNotExists("modules/announcement.yml");

        announcementManager = new AnnouncementManager(this);
        announcementManager.load();
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

        registerGuideCommands();
        registerAnnouncementCommand();
    }

    private void registerGuideCommands() {
        if (guideManager == null) {
            return;
        }

        registerGuideCommand("velioraguide", "guide");
        registerGuideCommand("veliorarules", "rules");
        registerGuideCommand("velioraproduct", "product");
    }

    private void registerGuideCommand(String commandName, String sectionName) {
        PluginCommand command = getCommand(commandName);

        if (command == null) {
            getLogger().warning("Command /" + commandName + " tidak ditemukan di plugin.yml.");
            return;
        }

        GuideCommand guideCommand = new GuideCommand(guideManager, sectionName);
        command.setExecutor(guideCommand);
        command.setTabCompleter(guideCommand);
    }

    private void registerAnnouncementCommand() {
        if (announcementManager == null) {
            return;
        }

        PluginCommand command = getCommand("velioraannouncement");

        if (command == null) {
            getLogger().warning("Command /velioraannouncement tidak ditemukan di plugin.yml.");
            return;
        }

        AnnouncementCommand announcementCommand = new AnnouncementCommand(announcementManager);
        command.setExecutor(announcementCommand);
        command.setTabCompleter(announcementCommand);
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
