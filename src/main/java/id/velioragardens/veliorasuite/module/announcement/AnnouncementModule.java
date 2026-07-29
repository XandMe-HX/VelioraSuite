package id.velioragardens.veliorasuite.module.announcement;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.api.VelioraModule;
import id.velioragardens.veliorasuite.command.DisabledCommand;
import org.bukkit.command.PluginCommand;

public final class AnnouncementModule implements VelioraModule {

    private final VelioraSuite plugin;
    private AnnouncementManager announcementManager;
    private boolean enabled;

    public AnnouncementModule(VelioraSuite plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "announcement";
    }

    @Override
    public void load() {
        plugin.saveResourceIfNotExists("modules/announcement.yml");
        announcementManager = new AnnouncementManager(plugin);
        announcementManager.load();
    }

    @Override
    public void enable() {
        enabled = true;
        registerCommand();

        if (announcementManager != null) {
            announcementManager.start();
        }
    }

    @Override
    public void disable() {
        enabled = false;

        if (announcementManager != null) {
            announcementManager.shutdown();
        }

        registerDisabledCommand();
    }

    @Override
    public void reload() {
        if (announcementManager != null) {
            announcementManager.reload();
        } else {
            load();

            if (enabled && announcementManager != null) {
                announcementManager.start();
            }
        }
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    private void registerCommand() {
        PluginCommand command = plugin.getCommand("velioraannouncement");

        if (command == null) {
            plugin.getLogger().warning("Command /velioraannouncement tidak ditemukan di plugin.yml.");
            return;
        }

        AnnouncementCommand announcementCommand = new AnnouncementCommand(announcementManager);
        command.setExecutor(announcementCommand);
        command.setTabCompleter(announcementCommand);
    }

    private void registerDisabledCommand() {
        PluginCommand command = plugin.getCommand("velioraannouncement");

        if (command == null) {
            return;
        }

        DisabledCommand disabledCommand = new DisabledCommand(plugin, "VelioraAnnouncement");
        command.setExecutor(disabledCommand);
        command.setTabCompleter(disabledCommand);
    }
}
