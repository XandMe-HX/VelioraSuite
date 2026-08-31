package id.velioragardens.veliorasuite.module.deathlaugh;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.api.VelioraModule;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import java.io.File;

/** Bounded death sound; deliberately no repeating task, entities, or network broadcast spam. */
public final class DeathLaughModule implements VelioraModule, Listener {
    private final VelioraSuite plugin; private YamlConfiguration config; private Sound sound; private boolean enabled;
    public DeathLaughModule(VelioraSuite plugin) { this.plugin = plugin; }
    @Override public String getName() { return "deathlaugh"; }
    @Override public void load() {
        plugin.saveResourceIfNotExists("modules/deathlaugh.yml");
        config = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "modules/deathlaugh.yml"));
        try { sound = Sound.valueOf(config.getString("settings.sound", "ENTITY_RAVAGER_CELEBRATE").toUpperCase()); }
        catch (IllegalArgumentException ignored) { sound = Sound.ENTITY_RAVAGER_CELEBRATE; }
    }
    @Override public void enable() { enabled = config.getBoolean("settings.enabled", true); if (enabled) plugin.getServer().getPluginManager().registerEvents(this, plugin); }
    @Override public void disable() { enabled = false; HandlerList.unregisterAll(this); }
    @Override public void reload() { load(); }
    @Override public boolean isEnabled() { return enabled; }
    @EventHandler(ignoreCancelled = true) public void onDeath(PlayerDeathEvent event) {
        if (!enabled || sound == null) return;
        Player dead = event.getEntity(); double radius = Math.max(0D, config.getDouble("settings.radius", 64D)); double radiusSquared = radius * radius;
        boolean sameWorld = config.getBoolean("settings.same-world-only", true);
        float volume = (float) Math.max(0D, Math.min(4D, config.getDouble("settings.volume", .8D)));
        float pitch = (float) Math.max(.5D, Math.min(2D, config.getDouble("settings.pitch", 1D)));
        for (Player listener : plugin.getServer().getOnlinePlayers()) {
            if (sameWorld && listener.getWorld() != dead.getWorld()) continue;
            if (listener.getWorld() == dead.getWorld() && radius > 0D && listener.getLocation().distanceSquared(dead.getLocation()) > radiusSquared) continue;
            listener.playSound(listener.getLocation(), sound, volume, pitch);
        }
    }
}
