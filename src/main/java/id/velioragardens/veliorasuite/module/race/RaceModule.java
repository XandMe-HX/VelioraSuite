package id.velioragardens.veliorasuite.module.race;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.api.VelioraModule;
import id.velioragardens.veliorasuite.command.DisabledCommand;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;

/** Phase 1 foundation only. Selection enforcement remains opt-in until the GUI ships. */
public final class RaceModule implements VelioraModule {
    private final VelioraSuite plugin;
    private RaceManager manager;
    private RaceListener listener;
    private RaceGui gui;
    private boolean enabled;

    public RaceModule(VelioraSuite plugin) { this.plugin = plugin; }
    @Override public String getName() { return "race"; }
    @Override public void load() {
        plugin.saveResourceIfNotExists("modules/race.yml");
        manager = new RaceManager(plugin);
        manager.load();
        gui = new RaceGui(plugin, manager);
        listener = new RaceListener(plugin, manager, gui);
    }
    @Override public void enable() {
        enabled = true;
        PluginCommand command = plugin.getCommand("race");
        if (command != null) { command.setExecutor(listener); command.setTabCompleter(listener); }
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        plugin.getServer().getPluginManager().registerEvents(gui, plugin);
    }
    @Override public void disable() {
        enabled = false;
        if (listener != null) HandlerList.unregisterAll(listener);
        if (gui != null) HandlerList.unregisterAll(gui);
        if (manager != null) manager.shutdown();
        PluginCommand command = plugin.getCommand("race");
        if (command != null) command.setExecutor(new DisabledCommand(plugin, "Race"));
    }
    @Override public void reload() { if (manager != null) manager.reloadConfig(); }
    @Override public boolean isEnabled() { return enabled; }
}
