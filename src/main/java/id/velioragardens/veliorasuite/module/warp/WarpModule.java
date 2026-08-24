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
    private TeleportSafetyListener safetyListener;
    private AfkManager afkManager;
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
        register("fishing", executor);
        listener = new WarpListener(manager);
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        safetyListener = new TeleportSafetyListener(plugin, manager);
        plugin.getServer().getPluginManager().registerEvents(safetyListener, plugin);
        safetyListener.start();
        afkManager = new AfkManager(plugin, manager);
        PluginCommand afkCommand = plugin.getCommand("afk");
        if (afkCommand != null) afkCommand.setExecutor(afkManager);
        plugin.getServer().getPluginManager().registerEvents(afkManager, plugin);
        afkManager.start();
    }

    @Override
    public void disable() {
        enabled = false;
        if (listener != null) HandlerList.unregisterAll(listener);
        if (safetyListener != null) {
            HandlerList.unregisterAll(safetyListener);
            safetyListener.stop();
        }
        if (afkManager != null) {
            HandlerList.unregisterAll(afkManager);
            afkManager.stop();
        }
        listener = null;
        safetyListener = null;
        afkManager = null;
        if (manager != null) manager.save();
        disableCommand("vgwarp");
        disableCommand("lobby");
        disableCommand("dungeon");
        disableCommand("pvp");
        disableCommand("guild");
        disableCommand("fishing");
        disableCommand("afk");
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
