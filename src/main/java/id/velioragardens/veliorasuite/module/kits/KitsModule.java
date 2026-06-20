package id.velioragardens.veliorasuite.module.kits;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.module.AbstractModule;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;

public final class KitsModule extends AbstractModule {
    private KitManager kitManager;
    private KitListener listener;
    public KitsModule(VelioraSuite plugin) { super(plugin, "kits", "kits"); }
    @Override protected void onEnable() {
        this.kitManager = new KitManager(plugin, configFile);
        this.kitManager.load();
        this.listener = new KitListener(kitManager);
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        KitCommand playerCommand = new KitCommand(kitManager);
        register("kit", playerCommand);
        KitAdminCommand adminCommand = new KitAdminCommand(kitManager);
        register("vkit", adminCommand);
        register("vkits", adminCommand);
        plugin.getLogger().info("VelioraKits module started.");
    }
    @Override protected void onDisable() { if (listener != null) HandlerList.unregisterAll(listener); if (kitManager != null) kitManager.save(); plugin.getLogger().info("VelioraKits module stopped."); }
    private void register(String name, Object executor) { PluginCommand command = plugin.getCommand(name); if (command == null) { plugin.getLogger().warning("Command /" + name + " tidak ditemukan di plugin.yml."); return; } if (executor instanceof org.bukkit.command.CommandExecutor ce) command.setExecutor(ce); if (executor instanceof org.bukkit.command.TabCompleter tc) command.setTabCompleter(tc); }
}
