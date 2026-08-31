package id.velioragardens.veliorasuite.module.autotool;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.api.VelioraModule;
import id.velioragardens.veliorasuite.command.DisabledCommand;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;

/** Native Paper replacement for the legacy NMS-based AutoTool plugin. */
public final class AutoToolModule implements VelioraModule {
    private final VelioraSuite plugin;
    private AutoToolManager manager;
    private boolean enabled;

    public AutoToolModule(VelioraSuite plugin) { this.plugin = plugin; }
    @Override public String getName() { return "autotool"; }

    @Override public void load() {
        plugin.saveResourceIfNotExists("modules/autotool.yml");
        manager = new AutoToolManager(plugin);
        manager.load();
    }

    @Override public void enable() {
        enabled = manager.isModuleEnabled();
        if (!enabled) return;
        PluginCommand command = plugin.getCommand("autotool");
        if (command != null) { command.setExecutor(manager); command.setTabCompleter(manager); }
        plugin.getServer().getPluginManager().registerEvents(manager, plugin);
        manager.start();
    }

    @Override public void disable() {
        enabled = false;
        if (manager != null) { HandlerList.unregisterAll(manager); manager.shutdown(); }
        PluginCommand command = plugin.getCommand("autotool");
        if (command != null) command.setExecutor(new DisabledCommand(plugin, "AutoTool"));
    }

    @Override public void reload() { if (manager != null) manager.load(); }
    @Override public boolean isEnabled() { return enabled; }
}
