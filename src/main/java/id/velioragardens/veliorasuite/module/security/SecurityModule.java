package id.velioragardens.veliorasuite.module.security;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.api.VelioraModule;
import id.velioragardens.veliorasuite.command.DisabledCommand;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;

public final class SecurityModule implements VelioraModule {

    private final VelioraSuite plugin;
    private SecurityManager manager;
    private SecurityListener listener;
    private boolean enabled;

    public SecurityModule(VelioraSuite plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "security";
    }

    @Override
    public void load() {
        plugin.saveResourceIfNotExists("modules/security.yml");
        manager = new SecurityManager(plugin);
        manager.load();
    }

    @Override
    public void enable() {
        enabled = true;
        registerCommand();
        listener = new SecurityListener(manager);
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
    }

    @Override
    public void disable() {
        enabled = false;
        if (listener != null) {
            HandlerList.unregisterAll(listener);
            listener = null;
        }
        registerDisabledCommand();
    }

    @Override
    public void reload() {
        if (manager != null) manager.reload();
        else load();
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    private void registerCommand() {
        PluginCommand command = plugin.getCommand("vsecurity");
        if (command == null) {
            plugin.getLogger().warning("Command /vsecurity tidak ditemukan di plugin.yml.");
            return;
        }
        SecurityCommand securityCommand = new SecurityCommand(manager);
        command.setExecutor(securityCommand);
        command.setTabCompleter(securityCommand);
    }

    private void registerDisabledCommand() {
        PluginCommand command = plugin.getCommand("vsecurity");
        if (command == null) return;
        DisabledCommand disabledCommand = new DisabledCommand(plugin, "VelioraSecurity");
        command.setExecutor(disabledCommand);
        command.setTabCompleter(disabledCommand);
    }
}
