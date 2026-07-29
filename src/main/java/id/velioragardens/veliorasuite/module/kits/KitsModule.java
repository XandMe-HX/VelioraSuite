package id.velioragardens.veliorasuite.module.kits;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.api.VelioraModule;
import id.velioragardens.veliorasuite.command.DisabledCommand;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;

public final class KitsModule implements VelioraModule {

    private final VelioraSuite plugin;
    private KitsManager kitsManager;
    private KitsListener kitsListener;
    private boolean enabled;

    public KitsModule(VelioraSuite plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "kits";
    }

    @Override
    public void load() {
        plugin.saveResourceIfNotExists("modules/kits.yml");
        kitsManager = new KitsManager(plugin);
        kitsManager.load();
    }

    @Override
    public void enable() {
        enabled = true;
        registerCommand();
        registerListener();
    }

    @Override
    public void disable() {
        enabled = false;

        if (kitsListener != null) {
            HandlerList.unregisterAll(kitsListener);
            kitsListener = null;
        }

        registerDisabledCommand();
    }

    @Override
    public void reload() {
        if (kitsManager != null) {
            kitsManager.reload();
        } else {
            load();
        }
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    private void registerCommand() {
        PluginCommand command = plugin.getCommand("kits");

        if (command == null) {
            plugin.getLogger().warning("Command /kits tidak ditemukan di plugin.yml.");
            return;
        }

        KitsCommand kitsCommand = new KitsCommand(kitsManager);
        command.setExecutor(kitsCommand);
        command.setTabCompleter(kitsCommand);
    }

    private void registerListener() {
        kitsListener = new KitsListener(plugin, kitsManager);
        plugin.getServer().getPluginManager().registerEvents(kitsListener, plugin);
    }

    private void registerDisabledCommand() {
        PluginCommand command = plugin.getCommand("kits");

        if (command == null) {
            return;
        }

        DisabledCommand disabledCommand = new DisabledCommand(plugin, "VelioraKits");
        command.setExecutor(disabledCommand);
        command.setTabCompleter(disabledCommand);
    }
}
