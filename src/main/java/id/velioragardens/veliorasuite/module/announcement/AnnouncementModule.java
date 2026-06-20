package id.velioragardens.veliorasuite.module.announcement;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.module.AbstractModule;
import org.bukkit.command.PluginCommand;

public final class AnnouncementModule extends AbstractModule {
    private AnnouncementManager announcementManager;
    public AnnouncementModule(VelioraSuite plugin) { super(plugin, "announcement", "announcement"); }
    @Override protected void onEnable() {
        this.announcementManager = new AnnouncementManager(plugin, configFile);
        this.announcementManager.start();
        PluginCommand command = plugin.getCommand("vannounce");
        if (command != null) { AnnouncementCommand executor = new AnnouncementCommand(announcementManager); command.setExecutor(executor); command.setTabCompleter(executor); }
        plugin.getLogger().info("VelioraAnnouncement module started.");
    }
    @Override protected void onDisable() { if (announcementManager != null) announcementManager.stop(); plugin.getLogger().info("VelioraAnnouncement module stopped."); }
}
