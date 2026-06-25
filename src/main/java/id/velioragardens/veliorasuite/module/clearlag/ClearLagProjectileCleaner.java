package id.velioragardens.veliorasuite.module.clearlag;

import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Projectile;

import java.util.Set;

public final class ClearLagProjectileCleaner {

    private static final Set<EntityType> CLEANABLE_TYPES = Set.of(
            EntityType.ARROW,
            EntityType.SPECTRAL_ARROW,
            EntityType.TRIDENT,
            EntityType.SNOWBALL,
            EntityType.EGG,
            EntityType.FIREBALL,
            EntityType.SMALL_FIREBALL
    );

    private final ClearLagConfigManager configManager;

    public ClearLagProjectileCleaner(ClearLagConfigManager configManager) {
        this.configManager = configManager;
    }

    public int clear(World world) {
        if (world == null || !configManager.isProjectileCleanerEnabled()) return 0;
        int removed = 0;
        for (Projectile projectile : world.getEntitiesByClass(Projectile.class)) {
            if (shouldKeep(projectile)) continue;
            projectile.remove();
            removed++;
        }
        return removed;
    }

    private boolean shouldKeep(Projectile projectile) {
        if (projectile == null || projectile.isDead() || !projectile.isValid()) return true;
        if (!CLEANABLE_TYPES.contains(projectile.getType())) return true;
        return configManager.getIgnoredProjectileTypes().contains(projectile.getType());
    }
}
