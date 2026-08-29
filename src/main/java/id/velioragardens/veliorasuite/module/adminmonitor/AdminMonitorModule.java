package id.velioragardens.veliorasuite.module.adminmonitor;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.api.VelioraModule;
import id.velioragardens.veliorasuite.command.DisabledCommand;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;

public final class AdminMonitorModule implements VelioraModule {
    private final VelioraSuite plugin;
    private AdminMonitorManager manager;
    private AdminMonitorListener listener;
    private AdminManagerGui gui;
    private boolean enabled;

    public AdminMonitorModule(VelioraSuite plugin) { this.plugin = plugin; }

    @Override public String getName() { return "adminmonitor"; }

    @Override public void load() {
        plugin.saveResourceIfNotExists("modules/adminmonitor.yml");
        manager = new AdminMonitorManager(plugin);
        manager.load();
    }

    @Override public void enable() {
        enabled = manager.isEnabledInConfig();
        if (!enabled) return;
        PluginCommand command = plugin.getCommand("adminmonitor");
        if (command != null) {
            AdminMonitorCommand executor = new AdminMonitorCommand(manager);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }
        listener = new AdminMonitorListener(manager);
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        gui = new AdminManagerGui(plugin, manager);
        plugin.getServer().getPluginManager().registerEvents(gui, plugin);
        PluginCommand managerCommand = plugin.getCommand("adminmanager");
        if (managerCommand != null) {
            managerCommand.setExecutor((sender, managerCommandLabel, label, args) -> {
                if (!(sender instanceof org.bukkit.entity.Player player)) {
                    sender.sendMessage("Command ini hanya dapat dibuka di dalam game.");
                } else if (!manager.canView(sender)) {
                    manager.sendNoPermission(sender);
                } else {
                    gui.openPlayers(player, 0);
                }
                return true;
            });
        }
        manager.beginExistingSessions();
    }

    @Override public void disable() {
        enabled = false;
        if (listener != null) HandlerList.unregisterAll(listener);
        if (gui != null) HandlerList.unregisterAll(gui);
        if (manager != null) manager.shutdown();
        PluginCommand command = plugin.getCommand("adminmonitor");
        if (command != null) {
            DisabledCommand disabled = new DisabledCommand(plugin, "AdminMonitor");
            command.setExecutor(disabled);
            command.setTabCompleter(disabled);
        }
    }

    @Override public void reload() { if (manager != null) manager.load(); }
    @Override public boolean isEnabled() { return enabled; }
}
