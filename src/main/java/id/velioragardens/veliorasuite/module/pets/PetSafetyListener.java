package id.velioragardens.veliorasuite.module.pets;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.EntityBlockFormEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;

public final class PetSafetyListener implements Listener {
    private static final String PET_TAG = "veliorapets_pet";
    private static final String BOSS_TAG = "velioraboss_boss";

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPetHazard(EntityDamageEvent event) {
        if (!isPet(event.getEntity())) return;
        switch (event.getCause()) {
            case FIRE, FIRE_TICK, LAVA, HOT_FLOOR, DROWNING, FREEZE, FALL -> {
                event.setCancelled(true);
                event.getEntity().setFireTicks(0);
            }
            default -> { }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPetHit(EntityDamageByEntityEvent event) {
        if (isPet(event.getEntity())) {
            Entity source = event.getDamager();
            if (source instanceof Player || source instanceof Monster || source instanceof Projectile || source.getScoreboardTags().contains(BOSS_TAG)) {
                event.setCancelled(true);
                event.getEntity().setFireTicks(0);
            }
            return;
        }
        if (isPet(event.getDamager()) && event.getEntity() instanceof Player) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPetTarget(EntityTargetLivingEntityEvent event) {
        if (isPet(event.getEntity())) {
            event.setCancelled(true);
            return;
        }
        if (event.getTarget() != null && isPet(event.getTarget())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPetCombust(EntityCombustEvent event) {
        if (isPet(event.getEntity())) {
            event.setCancelled(true);
            event.getEntity().setFireTicks(0);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPetBlockChange(EntityChangeBlockEvent event) {
        if (isPet(event.getEntity())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPetBlockForm(EntityBlockFormEvent event) {
        if (isPet(event.getEntity())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPetPickup(EntityPickupItemEvent event) {
        if (isPet(event.getEntity())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPetExplode(EntityExplodeEvent event) {
        if (event.getEntity() != null && isPet(event.getEntity())) {
            event.blockList().clear();
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPetPrime(ExplosionPrimeEvent event) {
        if (isPet(event.getEntity())) event.setCancelled(true);
    }

    private boolean isPet(Entity entity) {
        return entity != null && entity.getScoreboardTags().contains(PET_TAG);
    }
}
