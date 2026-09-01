package id.velioragardens.veliorasuite.module.mobpanic;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.api.VelioraModule;
import org.bukkit.event.HandlerList;

public final class MobPanicModule implements VelioraModule {
    private final VelioraSuite plugin;
    private MobPanicListener listener;
    private boolean enabled;

    public MobPanicModule(VelioraSuite plugin) { this.plugin = plugin; }
    @Override public String getName() { return "mobpanic"; }
    @Override public void load() { plugin.saveResourceIfNotExists("modules/mobpanic.yml"); listener = new MobPanicListener(plugin); listener.load(); }
    @Override public void enable() { enabled = listener != null && listener.enabled(); if (enabled) plugin.getServer().getPluginManager().registerEvents(listener, plugin); }
    @Override public void disable() { enabled = false; if (listener != null) HandlerList.unregisterAll(listener); }
    @Override public void reload() { if (listener != null) listener.load(); }
    @Override public boolean isEnabled() { return enabled; }
}
