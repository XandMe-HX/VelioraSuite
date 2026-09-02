package id.velioragardens.veliorasuite.module.combattag;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.api.VelioraModule;
import org.bukkit.event.HandlerList;

public final class CombatTagModule implements VelioraModule {
    private final VelioraSuite plugin;
    private CombatTagListener listener;
    private boolean enabled;

    public CombatTagModule(VelioraSuite plugin) { this.plugin = plugin; }
    @Override public String getName() { return "combattag"; }
    @Override public void load() { plugin.saveResourceIfNotExists("modules/combat-tag.yml"); listener = new CombatTagListener(plugin); listener.load(); }
    @Override public void enable() { enabled = true; plugin.getServer().getPluginManager().registerEvents(listener, plugin); listener.start(); }
    @Override public void disable() { enabled = false; if (listener != null) { listener.stop(); HandlerList.unregisterAll(listener); } }
    @Override public void reload() { if (listener != null) listener.load(); }
    @Override public boolean isEnabled() { return enabled; }
}
