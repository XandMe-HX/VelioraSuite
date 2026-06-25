package id.velioragardens.veliorasuite.module.clearlag;

import org.bukkit.World;
import org.bukkit.entity.Ambient;
import org.bukkit.entity.Animals;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Villager;
import org.bukkit.entity.WaterMob;

public final class ClearLagMobCleaner {

    private final ClearLagConfigManager configManager;

    public ClearLagMobCleaner(ClearLagConfigManager configManager) {
        this.configManager = configManager;
    }

    public int clear(World world) {
        if (world == null || !configManager.isMobCleanerEnabled()) return 0;
        int removed = 0;
        for (LivingEntity entity : world.getEntitiesByClass(LivingEntity.class)) {
            if (shouldKeep(entity)) continue;
            entity.remove();
            removed++;
        }
        return removed;
    }

    private boolean shouldKeep(LivingEntity entity) {
        if (entity == null || entity.isDead() || !entity.isValid()) return true;
        if (entity instanceof Player || entity instanceof Villager || entity instanceof ArmorStand) return true;
        if (configManager.getIgnoredEntityTypes().contains(entity.getType())) return true;
        if (configManager.isIgnoreNamedMobs() && entity.getCustomName() != null && !entity.getCustomName().isBlank()) return true;
        if (configManager.isIgnoreTamedAnimals() && entity instanceof Tameable tameable && tameable.isTamed()) return true;
        return !isConfiguredForRemoval(entity);
    }

    private boolean isConfiguredForRemoval(Entity entity) {
        if (configManager.isRemoveHostileMobs() && (entity instanceof Monster || entity instanceof Slime)) return true;
        return configManager.isRemovePassiveMobs() && (entity instanceof Animals || entity instanceof WaterMob || entity instanceof Ambient);
    }
}
