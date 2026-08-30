package id.velioragardens.veliorasuite.module.biome;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.api.VelioraModule;
import id.velioragardens.veliorasuite.command.DisabledCommand;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;

public final class BiomeModule implements VelioraModule {
    private final VelioraSuite plugin;
    private BiomeListener listener;
    private boolean enabled;

    public BiomeModule(VelioraSuite plugin) { this.plugin = plugin; }
    @Override public String getName() { return "biome"; }

    @Override public void load() {
        plugin.saveResourceIfNotExists("modules/biome.yml");
        listener = new BiomeListener(plugin);
        listener.load();
    }

    @Override public void enable() {
        enabled = true;
        PluginCommand command = plugin.getCommand("vbiome");
        if (command != null) { command.setExecutor(listener); command.setTabCompleter(listener); }
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        listener.start();
    }

    @Override public void disable() {
        enabled = false;
        if (listener != null) { listener.stop(); HandlerList.unregisterAll(listener); }
        PluginCommand command = plugin.getCommand("vbiome");
        if (command != null) command.setExecutor(new DisabledCommand(plugin, "Biome"));
    }

    @Override public void reload() { if (listener != null) listener.load(); }
    @Override public boolean isEnabled() { return enabled; }
}
