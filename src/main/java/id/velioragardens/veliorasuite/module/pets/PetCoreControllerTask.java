package id.velioragardens.veliorasuite.module.pets;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.module.pets.model.OwnedPet;
import id.velioragardens.veliorasuite.module.pets.model.PetDefinition;
import id.velioragardens.veliorasuite.module.pets.model.VelioraPet;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PetCoreControllerTask implements Runnable {
    private static final String PET_TAG = "veliorapets_pet";
    private static final String ANCHOR_TAG = "veliorapets_aquatic_anchor";
    private static final double SIDE_OFFSET = 1.2D;
    private static final double BACK_OFFSET = 1.2D;
    private static final double TELEPORT_DISTANCE = 16.0D;
    private static final double FOLLOW_DISTANCE = 2.5D;
    private static final double STUCK_DISTANCE = 8.0D;
    private static final double PATHFINDER_SPEED = 1.15D;
    private static final double FALLBACK_SPEED = 0.16D;
    private static final long STUCK_MILLIS = 5000L;
    private static final long TELEPORT_COOLDOWN_MILLIS = 5000L;

    private final PetManager manager;
    private final PetConfigManager config;
    private final Map<UUID, Location> lastLocations = new HashMap<>();
    private final Map<UUID, Long> stuckSince = new HashMap<>();
    private final Map<UUID, Long> lastTeleport = new HashMap<>();

    public PetCoreControllerTask(VelioraSuite plugin, PetManager manager) {
        this.manager = manager;
        this.config = manager.config();
    }

    @Override
    public void run() {
        cleanupAnchorsOnly();
        for (Player owner : Bukkit.getOnlinePlayers()) {
            VelioraPet active = manager.activePet(owner.getUniqueId());
            if (active == null) continue;
            LivingEntity pet = active.entity();
            if (pet == null || pet.isDead() || !pet.isValid()) {
                active.targetUuid(null);
                PetMovementDebug.remember(owner.getUniqueId(), "entity invalid", null, false, false, "none");
                continue;
            }
            PetDefinition definition = config.pets().get(active.petId());
            stabilize(pet, definition);
            if (ownerNotCombat(owner) || definition == null || definition.aquaticPet() || definition.flyingPet()) active.targetUuid(null);
            follow(owner, pet, definition);
            combat(owner, active, definition);
        }
    }

    private void stabilize(LivingEntity pet, PetDefinition definition) {
        setInvisible(pet, false);
        pet.setCustomNameVisible(true);
        pet.setRemoveWhenFarAway(false);
        pet.setPersistent(true);
        pet.setCanPickupItems(false);
        pet.setCollidable(false);
        pet.setSilent(config.silentPets());
        pet.setGlowing(config.glowEnabled());
        pet.setFireTicks(0);
        pet.setFallDistance(0.0F);
        pet.setGravity(true);
        if (pet.isInsideVehicle()) pet.leaveVehicle();
        if (pet instanceof Mob mob) mob.setTarget(null);
        pet.setAI(shouldUsePathfinder(definition));
    }

    private void follow(Player owner, LivingEntity pet, PetDefinition definition) {
        Location target = targetLocation(owner);
        boolean targetAllowed = manager.redProtectCompat().canMovePet(owner, pet.getLocation(), target);
        if (!targetAllowed) {
            Location safe = allowedLocationNearOwner(owner, pet.getLocation());
            if (safe != null) {
                target = safe;
                targetAllowed = true;
            }
        }
        if (!targetAllowed) {
            PetMovementDebug.remember(owner.getUniqueId(), "redprotect movement denied target", target, false, false, "none");
            return;
        }
        if (!pet.getWorld().equals(owner.getWorld())) {
            pet.teleport(target);
            lastTeleport.put(pet.getUniqueId(), System.currentTimeMillis());
            PetMovementDebug.remember(owner.getUniqueId(), "teleport world change", target, true, false, "world change");
            return;
        }
        double distance = pet.getLocation().distance(target);
        if (distance > TELEPORT_DISTANCE && canTeleport(pet)) {
            pet.teleport(target);
            lastTeleport.put(pet.getUniqueId(), System.currentTimeMillis());
            markMoved(pet);
            PetMovementDebug.remember(owner.getUniqueId(), "teleport far distance", target, true, false, "distance > 16");
            return;
        }
        if (distance > STUCK_DISTANCE && isStuck(pet) && canTeleport(pet)) {
            pet.teleport(target);
            lastTeleport.put(pet.getUniqueId(), System.currentTimeMillis());
            markMoved(pet);
            PetMovementDebug.remember(owner.getUniqueId(), "teleport stuck", target, true, false, "stuck > 5s and distance > 8");
            return;
        }
        if (distance <= FOLLOW_DISTANCE) {
            PetMovementDebug.remember(owner.getUniqueId(), "near owner idle", target, true, false, "none");
            markMoved(pet);
            return;
        }
        boolean pathfinderUsed = false;
        if (shouldUsePathfinder(definition) && pet instanceof Mob mob) {
            try {
                pathfinderUsed = mob.getPathfinder().moveTo(target, PATHFINDER_SPEED);
            } catch (Throwable ignored) {
                pathfinderUsed = false;
            }
        }
        if (!pathfinderUsed && distance > FOLLOW_DISTANCE) {
            Vector velocity = target.toVector().subtract(pet.getLocation().toVector());
            if (velocity.lengthSquared() > 0.01D) {
                velocity.normalize().multiply(FALLBACK_SPEED);
                velocity.setY(clamp(velocity.getY(), -0.15D, 0.20D));
                pet.setVelocity(velocity);
            }
        }
        markMoved(pet);
        PetMovementDebug.remember(owner.getUniqueId(), pathfinderUsed ? "pathfinder follow" : "fallback velocity follow", target, true, pathfinderUsed, "none");
    }

    private Location allowedLocationNearOwner(Player owner, Location fallback) {
        Location base = owner.getLocation();
        for (int radius = 0; radius <= 3; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    Location candidate = base.clone().add(dx + 0.5D, 0.0D, dz + 0.5D);
                    if (manager.redProtectCompat().canMovePet(owner, fallback, candidate)) return candidate;
                }
            }
        }
        return null;
    }

    private boolean canTeleport(LivingEntity pet) {
        return System.currentTimeMillis() - lastTeleport.getOrDefault(pet.getUniqueId(), 0L) >= TELEPORT_COOLDOWN_MILLIS;
    }

    private Location targetLocation(Player owner) {
        Location base = owner.getLocation().clone();
        Vector direction = base.getDirection();
        direction.setY(0.0D);
        if (direction.lengthSquared() < 0.01D) direction = new Vector(0.0D, 0.0D, 1.0D);
        direction.normalize();
        Vector side = new Vector(-direction.getZ(), 0.0D, direction.getX()).normalize().multiply(SIDE_OFFSET);
        Vector back = direction.clone().multiply(-BACK_OFFSET);
        return base.add(side).add(back);
    }

    private void combat(Player owner, VelioraPet active, PetDefinition definition) {
        if (definition == null || !config.combatEnabled() || ownerNotCombat(owner) || active.targetUuid() == null) return;
        Entity targetEntity = Bukkit.getEntity(active.targetUuid());
        if (!(targetEntity instanceof LivingEntity target) || target.isDead() || !target.isValid() || target instanceof Player || target.getScoreboardTags().contains(PET_TAG)) {
            active.targetUuid(null);
            return;
        }
        LivingEntity pet = active.entity();
        if (!target.getWorld().equals(pet.getWorld())) { active.targetUuid(null); return; }
        if (!manager.redProtectCompat().canMovePet(owner, pet.getLocation(), target.getLocation())) { active.targetUuid(null); return; }
        double distance = pet.getLocation().distance(target.getLocation());
        if (distance > 18.0D) { active.targetUuid(null); return; }
        if (distance > config.attackRange()) {
            if (shouldUsePathfinder(definition) && pet instanceof Mob mob) {
                try { mob.getPathfinder().moveTo(target.getLocation(), PATHFINDER_SPEED); return; } catch (Throwable ignored) { }
            }
            Vector velocity = target.getLocation().toVector().subtract(pet.getLocation().toVector());
            if (velocity.lengthSquared() > 0.01D) {
                velocity.normalize().multiply(FALLBACK_SPEED);
                velocity.setY(clamp(velocity.getY(), -0.15D, 0.20D));
                pet.setVelocity(velocity);
            }
            return;
        }
        long now = System.currentTimeMillis();
        if (now - active.lastAttackMillis() < config.attackCooldownSeconds() * 1000L) return;
        target.damage(Math.max(0.0D, definition.damage() * config.petDamageMultiplier()), owner);
        active.lastAttackMillis(now);
        OwnedPet owned = manager.playerData(owner.getUniqueId()).get(active.petId());
        if (owned != null) {
            owned.addExp(2, config.maxLevel());
            manager.data().save(owner.getUniqueId());
        }
    }

    private boolean shouldUsePathfinder(PetDefinition definition) {
        if (definition == null || definition.aquaticPet() || definition.flyingPet()) return false;
        EntityType type = definition.entityType();
        return switch (type) {
            case COW, SHEEP, PIG, CHICKEN, RABBIT, TURTLE, GOAT, CAMEL, HORSE, DONKEY, MULE,
                    LLAMA, TRADER_LLAMA, FOX, PANDA, CAT, WOLF, FROG, AXOLOTL, ARMADILLO, SNIFFER,
                    MUSHROOM_COW, VILLAGER, WANDERING_TRADER, STRIDER, SKELETON_HORSE, ZOMBIE_HORSE -> true;
            default -> false;
        };
    }

    private boolean ownerNotCombat(Player owner) {
        return owner.getGameMode() == GameMode.CREATIVE || owner.getGameMode() == GameMode.SPECTATOR;
    }

    private boolean isStuck(LivingEntity pet) {
        if (pet.getLocation().getBlock().getType().isSolid()) return true;
        UUID uuid = pet.getUniqueId();
        Location last = lastLocations.get(uuid);
        long now = System.currentTimeMillis();
        if (last == null || !last.getWorld().equals(pet.getWorld()) || last.distanceSquared(pet.getLocation()) > 0.04D) {
            lastLocations.put(uuid, pet.getLocation().clone());
            stuckSince.put(uuid, now);
            return false;
        }
        return now - stuckSince.getOrDefault(uuid, now) >= STUCK_MILLIS;
    }

    private void markMoved(LivingEntity pet) {
        if (!pet.isOnGround()) return;
        UUID uuid = pet.getUniqueId();
        lastLocations.putIfAbsent(uuid, pet.getLocation().clone());
        stuckSince.putIfAbsent(uuid, System.currentTimeMillis());
    }

    private void cleanupAnchorsOnly() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity.getScoreboardTags().contains(ANCHOR_TAG)) entity.remove();
            }
        }
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private void setInvisible(LivingEntity pet, boolean invisible) {
        try {
            Method method = pet.getClass().getMethod("setInvisible", boolean.class);
            method.invoke(pet, invisible);
        } catch (Exception ignored) {
        }
    }
}
