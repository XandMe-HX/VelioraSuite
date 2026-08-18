package id.velioragardens.veliorasuite.module.notifications;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.api.VelioraModule;
import org.bukkit.event.HandlerList;

public final class NotificationModule implements VelioraModule {
    private final VelioraSuite plugin;
    private NotificationListener listener;
    private boolean enabled;

    public NotificationModule(VelioraSuite plugin) { this.plugin = plugin; }
    @Override public String getName() { return "notifications"; }

    @Override public void load() {
        plugin.saveResourceIfNotExists("modules/notifications.yml");
        listener = new NotificationListener(plugin);
        listener.load();
    }

    @Override public void enable() {
        enabled = true;
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
    }

    @Override public void disable() {
        enabled = false;
        if (listener != null) HandlerList.unregisterAll(listener);
    }

    @Override public void reload() { if (listener != null) listener.load(); }
    @Override public boolean isEnabled() { return enabled; }
}
