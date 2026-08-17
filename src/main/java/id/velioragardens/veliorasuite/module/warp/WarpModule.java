package id.velioragardens.veliorasuite.module.warp;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.api.VelioraModule;
import id.velioragardens.veliorasuite.command.DisabledCommand;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;

public final class WarpModule implements VelioraModule {
    private final VelioraSuite plugin;
    private WarpManager manager;
    private WarpListener listener;
    private boolean enabled;

    public WarpModule(VelioraSuite plugin) {
        this.plugin = plugin;
    }

    @Override public String getName() { return "warp"; }

    @Override
    public void load() {
        plugin.saveResourceIfNotExists("modules/warp.yml");
        manager = new WarpManager(plugin);
        manager.load();
    }

    @Override
    public void enable() {
        enabled = true;
        WarpCommand executor = new WarpCommand(manager);
        register("vgwarp", executor);
        register("lobby", executor);
        register("dungeon", executor);
        register("pvp", executor);
        register("guild", executor);
        listener = new WarpListener(manager);
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
    }

    @Override
    public void disable() {
        enabled = false;
        if (listener != null) HandlerList.unregisterAll(listener);
        listener = null;
        if (manager != null) manager.save();
        disableCommand("vgwarp");
        disableCommand("lobby");
        disableCommand("dungeon");
        disableCommand("pvp");
        disableCommand("guild");
    }

    public WarpManager getManager() { return manager; }

    @Override public void reload() { if (manager != null) manager.load(); else load(); }
    @Override public boolean isEnabled() { return enabled; }

    private void register(String name, WarpCommand executor) {
        PluginCommand command = plugin.getCommand(name);
        if (command == null) {
            plugin.getLogger().warning("Command /" + name + " tidak ditemukan di plugin.yml.");
            return;
        }
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    private void disableCommand(String name) {
        PluginCommand command = plugin.getCommand(name);
        if (command == null) return;
        DisabledCommand disabled = new DisabledCommand(plugin, "VelioraWarp");
        command.setExecutor(disabled);
        command.setTabCompleter(disabled);
    }
}
