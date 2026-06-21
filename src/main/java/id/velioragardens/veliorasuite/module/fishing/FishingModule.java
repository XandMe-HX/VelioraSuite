package id.velioragardens.veliorasuite.module.fishing;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.module.AbstractModule;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;

public final class FishingModule extends AbstractModule {
    private FishingManager fishingManager;
    private FishingListener listener;
    public FishingModule(VelioraSuite plugin) { super(plugin, "fishing", "fishing"); }
    @Override protected void onEnable() {
        this.fishingManager = new FishingManager(plugin, configFile);
        this.fishingManager.load();
        this.listener = new FishingListener(fishingManager);
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        PluginCommand command = plugin.getCommand("vf");
        if (command != null) { FishingCommand executor = new FishingCommand(fishingManager); command.setExecutor(executor); command.setTabCompleter(executor); }
        plugin.getLogger().info("VelioraFishing module started.");
    }
    @Override protected void onDisable() { if (listener != null) HandlerList.unregisterAll(listener); if (fishingManager != null) fishingManager.shutdown(); plugin.getLogger().info("VelioraFishing module stopped."); }
}
