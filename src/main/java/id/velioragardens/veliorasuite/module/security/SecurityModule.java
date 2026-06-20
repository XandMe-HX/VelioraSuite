package id.velioragardens.veliorasuite.module.security;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.module.AbstractModule;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;

public final class SecurityModule extends AbstractModule {

    private SecurityListener listener;

    public SecurityModule(VelioraSuite plugin) {
        super(plugin, "security", "security");
    }

    @Override
    protected void onEnable() {
        this.listener = new SecurityListener(configFile);
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        PluginCommand command = plugin.getCommand("vsecurity");
        if (command != null) {
            SecurityCommand executor = new SecurityCommand(configFile, listener);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }
        plugin.getLogger().info("VelioraSecurity module started.");
    }

    @Override
    protected void onDisable() {
        if (listener != null) HandlerList.unregisterAll(listener);
        plugin.getLogger().info("VelioraSecurity module stopped.");
    }
}
