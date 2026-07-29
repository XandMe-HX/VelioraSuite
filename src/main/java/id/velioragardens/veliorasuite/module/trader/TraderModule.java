package id.velioragardens.veliorasuite.module.trader;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.api.VelioraModule;
import id.velioragardens.veliorasuite.command.DisabledCommand;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

import java.util.ArrayList;
import java.util.List;

public final class TraderModule implements VelioraModule {

    private final VelioraSuite plugin;
    private final List<Listener> listeners = new ArrayList<>();
    private TraderManager manager;
    private boolean enabled;

    public TraderModule(VelioraSuite plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() { return "trader"; }

    @Override
    public void load() {
        plugin.saveResourceIfNotExists("modules/trader.yml");
        manager = new TraderManager(plugin);
        manager.load();
    }

    @Override
    public void enable() {
        enabled = true;
        registerCommand();
        registerListeners();
        manager.enable();
    }

    @Override
    public void disable() {
        enabled = false;
        for (Listener listener : listeners) HandlerList.unregisterAll(listener);
        listeners.clear();
        if (manager != null) manager.disable();
        registerDisabledCommand();
    }

    @Override
    public void reload() {
        if (manager != null) manager.reload();
        else load();
    }

    @Override
    public boolean isEnabled() { return enabled; }

    public TraderManager getTraderManager() { return manager; }

    private void registerCommand() {
        PluginCommand command = plugin.getCommand("trader");
        if (command == null) {
            plugin.getLogger().warning("Command /trader tidak ditemukan di plugin.yml.");
            return;
        }
        TraderCommand traderCommand = new TraderCommand(manager, manager.getConfigManager());
        command.setExecutor(traderCommand);
        command.setTabCompleter(traderCommand);
    }

    private void registerListeners() {
        listeners.clear();
        listeners.add(manager.getNpcManager());
        listeners.add(manager.getGuiManager());
        listeners.add(new TraderRepairBlocker(manager.getConfigManager(), manager.getItemFactory()));
        listeners.add(new TraderCombatListener(manager.getItemFactory()));
        for (Listener listener : listeners) plugin.getServer().getPluginManager().registerEvents(listener, plugin);
    }

    private void registerDisabledCommand() {
        PluginCommand command = plugin.getCommand("trader");
        if (command == null) return;
        DisabledCommand disabledCommand = new DisabledCommand(plugin, "VelioraTrader");
        command.setExecutor(disabledCommand);
        command.setTabCompleter(disabledCommand);
    }
}
