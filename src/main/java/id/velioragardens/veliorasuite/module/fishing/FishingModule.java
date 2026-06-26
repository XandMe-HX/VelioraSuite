package id.velioragardens.veliorasuite.module.fishing;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.api.VelioraModule;
import id.velioragardens.veliorasuite.command.DisabledCommand;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

import java.util.ArrayList;
import java.util.List;

public final class FishingModule implements VelioraModule {

    private final VelioraSuite plugin;
    private final List<Listener> listeners = new ArrayList<>();
    private FishingManager manager;
    private boolean enabled;

    public FishingModule(VelioraSuite plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "fishing";
    }

    @Override
    public void load() {
        plugin.saveResourceIfNotExists("modules/fishing.yml");
        manager = new FishingManager(plugin);
        manager.load();
    }

    @Override
    public void enable() {
        enabled = true;
        registerCommand();
        registerListeners();
    }

    @Override
    public void disable() {
        enabled = false;
        for (Listener listener : listeners) HandlerList.unregisterAll(listener);
        listeners.clear();
        if (manager != null) manager.shutdown();
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

    public FishingManager getFishingManager() {
        return manager;
    }

    private void registerCommand() {
        PluginCommand command = plugin.getCommand("fish");
        if (command == null) {
            plugin.getLogger().warning("Command /fish tidak ditemukan di plugin.yml.");
            return;
        }
        FishingCommand fishingCommand = new FishingCommand(manager);
        command.setExecutor(fishingCommand);
        command.setTabCompleter(fishingCommand);
    }

    private void registerListeners() {
        listeners.clear();
        listeners.add(manager.getMainGuiManager());
        listeners.add(manager.getMinigameManager());
        listeners.add(manager.getSellGuiManager());
        for (Listener listener : listeners) plugin.getServer().getPluginManager().registerEvents(listener, plugin);
    }

    private void registerDisabledCommand() {
        PluginCommand command = plugin.getCommand("fish");
        if (command == null) return;
        DisabledCommand disabledCommand = new DisabledCommand(plugin, "VelioraFishing");
        command.setExecutor(disabledCommand);
        command.setTabCompleter(disabledCommand);
    }
}
