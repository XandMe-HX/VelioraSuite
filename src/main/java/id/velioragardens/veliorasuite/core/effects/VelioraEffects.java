package id.velioragardens.veliorasuite.core.effects;

import id.velioragardens.veliorasuite.VelioraSuite;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Low-lag particle/sound delivery: only nearby clients receive an effect. */
public final class VelioraEffects {
    private final VelioraSuite plugin;
    private final Map<UUID, ParticleBudget> budgets = new HashMap<>();
    private boolean enabled;
    private boolean lowLag;
    private int maxPerPlayerSecond;
    private int maxBurst;
    private double viewDistance;

    public VelioraEffects(VelioraSuite plugin) { this.plugin = plugin; reload(); }

    public void reload() {
        enabled = plugin.getConfig().getBoolean("effects.enabled", true);
        lowLag = plugin.getConfig().getBoolean("effects.low-lag", true);
        maxPerPlayerSecond = Math.max(10, plugin.getConfig().getInt("effects.max-particles-per-player-per-second", 100));
        maxBurst = Math.max(1, plugin.getConfig().getInt("effects.max-particles-per-burst", 36));
        viewDistance = Math.max(8.0D, plugin.getConfig().getDouble("effects.view-distance", 32.0D));
        budgets.clear();
    }

    public void particle(Location location, Particle type, int requested, double offsetX, double offsetY, double offsetZ, double extra) {
        if (!enabled || location == null || location.getWorld() == null || type == null || requested <= 0) return;
        int count = Math.min(requested, maxBurst);
        double rangeSquared = viewDistance * viewDistance;
        for (Player viewer : location.getWorld().getPlayers()) {
            if (!viewer.isOnline() || viewer.getLocation().distanceSquared(location) > rangeSquared) continue;
            int allowed = reserve(viewer.getUniqueId(), count);
            if (allowed <= 0) continue;
            double scale = lowLag ? Math.min(1.0D, allowed / (double) Math.max(1, count)) : 1.0D;
            viewer.spawnParticle(type, location, allowed, offsetX * scale, offsetY * scale, offsetZ * scale, extra);
        }
    }

    public void sound(Location location, Sound sound, float volume, float pitch) {
        if (!enabled || location == null || location.getWorld() == null || sound == null) return;
        double rangeSquared = viewDistance * viewDistance;
        for (Player viewer : location.getWorld().getPlayers()) {
            if (viewer.isOnline() && viewer.getLocation().distanceSquared(location) <= rangeSquared) {
                viewer.playSound(location, sound, volume, pitch);
            }
        }
    }

    private int reserve(UUID player, int wanted) {
        long second = System.currentTimeMillis() / 1000L;
        ParticleBudget budget = budgets.computeIfAbsent(player, ignored -> new ParticleBudget(second));
        if (budget.second != second) { budget.second = second; budget.used = 0; }
        int allowed = Math.max(0, Math.min(wanted, maxPerPlayerSecond - budget.used));
        budget.used += allowed;
        return allowed;
    }

    private static final class ParticleBudget {
        private long second;
        private int used;
        private ParticleBudget(long second) { this.second = second; }
    }
}
