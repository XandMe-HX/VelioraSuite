package id.velioragardens.veliorasuite.module.gacha;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.api.VelioraModule;
import id.velioragardens.veliorasuite.command.DisabledCommand;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;

public final class GachaModule implements VelioraModule {
    private final VelioraSuite plugin;
    private GachaManager manager;
    private boolean enabled;

    public GachaModule(VelioraSuite plugin) { this.plugin = plugin; }
    @Override public String getName() { return "gacha"; }

    @Override public void load() {
        plugin.saveResourceIfNotExists("modules/gacha.yml");
        manager = new GachaManager(plugin);
        manager.load();
    }

    @Override public void enable() {
        enabled = manager != null && manager.enabled();
        PluginCommand command = plugin.getCommand("keyshop");
        if (!enabled) {
            if (command != null) command.setExecutor(new DisabledCommand(plugin, "Gacha"));
            return;
        }
        if (command != null) { command.setExecutor(manager); command.setTabCompleter(manager); }
        if (manager != null) plugin.getServer().getPluginManager().registerEvents(manager, plugin);
    }

    @Override public void disable() {
        enabled = false;
        if (manager != null) HandlerList.unregisterAll(manager);
        PluginCommand command = plugin.getCommand("keyshop");
        if (command != null) command.setExecutor(new DisabledCommand(plugin, "Gacha"));
    }

    @Override public void reload() { if (manager != null) manager.load(); }
    @Override public boolean isEnabled() { return enabled; }
}
