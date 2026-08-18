package id.velioragardens.veliorasuite;

import id.velioragardens.veliorasuite.command.VelioraCommand;
import id.velioragardens.veliorasuite.core.ConfigManager;
import id.velioragardens.veliorasuite.core.HookManager;
import id.velioragardens.veliorasuite.core.MessageManager;
import id.velioragardens.veliorasuite.core.ModuleManager;
import id.velioragardens.veliorasuite.module.announcement.AnnouncementModule;
import id.velioragardens.veliorasuite.module.adminmonitor.AdminMonitorModule;
import id.velioragardens.veliorasuite.module.adventure.AdventureModule;
import id.velioragardens.veliorasuite.module.boss.BossModule;
import id.velioragardens.veliorasuite.module.chat.ChatModule;
import id.velioragardens.veliorasuite.module.fishing.FishingModule;
import id.velioragardens.veliorasuite.module.guide.GuideModule;
import id.velioragardens.veliorasuite.module.kits.KitsModule;
import id.velioragardens.veliorasuite.module.loginsecurity.LoginSecurityModule;
import id.velioragardens.veliorasuite.module.menu.MenuModule;
import id.velioragardens.veliorasuite.module.notifications.NotificationModule;
import id.velioragardens.veliorasuite.module.pets.PetsModule;
import id.velioragardens.veliorasuite.module.quest.QuestModule;
import id.velioragardens.veliorasuite.module.report.ReportModule;
import id.velioragardens.veliorasuite.module.security.SecurityModule;
import id.velioragardens.veliorasuite.module.skills.SkillsModule;
import id.velioragardens.veliorasuite.module.team.TeamModule;
import id.velioragardens.veliorasuite.module.trader.TraderModule;
import id.velioragardens.veliorasuite.module.warp.WarpModule;
import id.velioragardens.veliorasuite.placeholder.VelioraPlaceholderExpansion;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class VelioraSuite extends JavaPlugin {

    private static VelioraSuite instance;

    private ConfigManager configManager;
    private MessageManager messageManager;
    private HookManager hookManager;
    private ModuleManager moduleManager;

    public static VelioraSuite getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;

        this.configManager = new ConfigManager(this);
        this.configManager.load();

        this.messageManager = new MessageManager(this);
        this.messageManager.load();

        this.hookManager = new HookManager(this);
        this.hookManager.loadHooks();

        this.moduleManager = new ModuleManager(this);
        registerModules();
        registerCoreCommand();
        this.moduleManager.enableAll();
        registerPlaceholderExpansion();

        getLogger().info("VelioraSuite clean core enabled.");
    }

    @Override
    public void onDisable() {
        if (moduleManager != null) {
            moduleManager.disableAll();
        }

        getLogger().info("VelioraSuite clean core disabled.");
    }

    public void reloadSuite() {
        configManager.reload();
        messageManager.reload();
        hookManager.loadHooks();
        moduleManager.reloadAll();

        getLogger().info("VelioraSuite clean core reloaded.");
    }

    private void registerModules() {
        moduleManager.register(new AdminMonitorModule(this));
        moduleManager.register(new GuideModule(this));
        moduleManager.register(new MenuModule(this));
        moduleManager.register(new AnnouncementModule(this));
        moduleManager.register(new LoginSecurityModule(this));
        moduleManager.register(new TeamModule(this));
        moduleManager.register(new KitsModule(this));
        moduleManager.register(new ReportModule(this));
        moduleManager.register(new ChatModule(this));
        moduleManager.register(new NotificationModule(this));
        moduleManager.register(new WarpModule(this));
        moduleManager.register(new SecurityModule(this));
        moduleManager.register(new FishingModule(this));
        moduleManager.register(new SkillsModule(this));
        moduleManager.register(new QuestModule(this));
        moduleManager.register(new TraderModule(this));
        moduleManager.register(new BossModule(this));
        moduleManager.register(new PetsModule(this));
        moduleManager.register(new AdventureModule(this));
    }

    private void registerPlaceholderExpansion() {
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") == null) {
            getLogger().info("PlaceholderAPI tidak ditemukan; placeholder VelioraSuite tidak didaftarkan.");
            return;
        }
        new VelioraPlaceholderExpansion(this).register();
        getLogger().info("PlaceholderAPI VelioraSuite terdaftar, termasuk Level, Mana, Team, Playtime, dan Rank Petualang.");
    }

    private void registerCoreCommand() {
        PluginCommand command = getCommand("veliorasuite");

        if (command == null) {
            getLogger().warning("Command /veliorasuite tidak ditemukan di plugin.yml.");
            return;
        }

        VelioraCommand velioraCommand = new VelioraCommand(this);
        command.setExecutor(velioraCommand);
        command.setTabCompleter(velioraCommand);
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }

    public HookManager getHookManager() {
        return hookManager;
    }

    public ModuleManager getModuleManager() {
        return moduleManager;
    }

    public void saveResourceIfNotExists(String path) {
        File file = new File(getDataFolder(), path);

        if (!file.exists()) {
            saveResource(path, false);
        }
    }

    public void createFolder(String path) {
        File folder = new File(getDataFolder(), path);

        if (!folder.exists()) {
            folder.mkdirs();
        }
    }
}
