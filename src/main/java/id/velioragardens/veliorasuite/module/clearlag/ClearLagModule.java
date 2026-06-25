package id.velioragardens.veliorasuite.module.clearlag;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.api.VelioraModule;
import id.velioragardens.veliorasuite.command.DisabledCommand;
import org.bukkit.command.PluginCommand;

public final class ClearLagModule implements VelioraModule {

    private final VelioraSuite plugin;
    private ClearLagManager manager;
    private boolean enabled;

    public ClearLagModule(VelioraSuite plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "clearlag";
    }

    @Override
    public void load() {
        plugin.saveResourceIfNotExists("modules/clearlag.yml");
        manager = new ClearLagManager(plugin);
        manager.load();
    }

    @Override
    public void enable() {
        enabled = true;
        registerCommand();
        manager.enable();
    }

    @Override
    public void disable() {
        enabled = false;
        if (manager != null) manager.shutdown();
        registerDisabledCommand();
    }

    @Override
    public void reload() {
        if (manager != null) {
            manager.reload();
        } else {
            load();
        }
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    private void registerCommand() {
        PluginCommand command = plugin.getCommand("vclearlag");
        if (command == null) {
            plugin.getLogger().warning("Command /vclearlag tidak ditemukan di plugin.yml.");
            return;
        }
        ClearLagCommand clearLagCommand = new ClearLagCommand(manager);
        command.setExecutor(clearLagCommand);
        command.setTabCompleter(clearLagCommand);
    }

    private void registerDisabledCommand() {
        PluginCommand command = plugin.getCommand("vclearlag");
        if (command == null) return;
        DisabledCommand disabledCommand = new DisabledCommand(plugin, "VelioraClearLag");
        command.setExecutor(disabledCommand);
        command.setTabCompleter(disabledCommand);
    }
}
