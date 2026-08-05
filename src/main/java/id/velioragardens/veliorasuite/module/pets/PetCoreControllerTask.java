package id.velioragardens.veliorasuite.module.pets;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.module.pets.model.OwnedPet;
import id.velioragardens.veliorasuite.module.pets.model.PetDefinition;
import id.velioragardens.veliorasuite.module.pets.model.VelioraPet;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PetCoreControllerTask implements Runnable {
    private static final String PET_TAG = "veliorapets_pet";
    private static final double SIDE_OFFSET = 1.2D;
    private static final double BACK_OFFSET = 1.2D;
    private static final double TELEPORT_DISTANCE = 16.0D;
    private static final double FOLLOW_DISTANCE = 2.5D;
    private static final double STUCK_DISTANCE = 8.0D;
    private static final double PATHFINDER_SPEED = 1.15D;
    private static final double FALLBACK_SPEED = 0.18D;
    private static final double COMBAT_SPEED = 0.34D;
    private static final double AUTO_TARGET_RADIUS = 14.0D;
    private static final long STUCK_MILLIS = 5000L;
    private static final long TELEPORT_COOLDOWN_MILLIS = 5000L;
    private static final long TARGET_SCAN_COOLDOWN_MILLIS = 1000L;

    private final PetManager manager;
    private final PetConfigManager config;
    private final Map<UUID, Location> lastLocations = new HashMap<>();
    private final Map<UUID, Long> stuckSince = new HashMap<>();
    private final Map<UUID, Long> lastTeleport = new HashMap<>();
    private final Map<UUID, Long> lastTargetScan = new HashMap<>();
    private int maintenanceTick;

    public PetCoreControllerTask(VelioraSuite plugin, PetManager manager) {
        this.manager = manager;
        this.config = manager.config();
    }

    @Override
    public void run() {
        boolean runMaintenance = ++maintenanceTick >= 10;
        if (runMaintenance) maintenanceTick = 0;
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
            if (runMaintenance) normalizeVisualAge(owner, active, definition, pet);
            if (hasPlayerPassenger(pet)) {
                stabilizeRiddenPet(pet);
                active.targetUuid(null);
                PetMovementDebug.remember(owner.getUniqueId(), "ridden by owner", pet.getLocation(), true, false, "ride control active");
                continue;
            }
            if (runMaintenance) stabilize(pet, definition);
            if (ownerNotCombat(owner) || definition == null || definition.aquaticPet() || definition.flyingPet()) active.targetUuid(null);
            acquireTargetIfNeeded(owner, active, definition);
            combat(owner, active, definition);
            if (active.targetUuid() == null) follow(owner, pet, definition);
        }
    }

    private void normalizeVisualAge(Player owner, VelioraPet active, PetDefinition definition, LivingEntity pet) {
        if (definition == null || pet == null) return;
        OwnedPet owned = manager.playerData(owner.getUniqueId()).get(active.petId());
        if (owned == null) return;
        String id = active.petId() == null ? "" : active.petId().toLowerCase();
        boolean shouldStayBaby = id.startsWith("baby_") || id.startsWith("mini_");
        boolean adultLevelReached = owned.level() >= definition.adultLevel();
        if (adultLevelReached && !shouldStayBaby) {
            tryAdult(pet);
        } else {
            tryBaby(pet);
        }
    }

    private void tryAdult(LivingEntity pet) {
        try {
            Method method = pet.getClass().getMethod("setAdult");
            method.invoke(pet);
            return;
        } catch (Exception ignored) {
        }
        try {
            Method method = pet.getClass().getMethod("setBaby", boolean.class);
            method.invoke(pet, false);
        } catch (Exception ignored) {
        }
    }

    private void tryBaby(LivingEntity pet) {
        try {
            Method method = pet.getClass().getMethod("setBaby", boolean.class);
            method.invoke(pet, true);
            return;
        } catch (Exception ignored) {
        }
        try {
            Method method = pet.getClass().getMethod("setBaby");
            method.invoke(pet);
        } catch (Exception ignored) {
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
        // FIX 4: Only reset fall distance when grounded or in vehicle to preserve fall damage and prevent fly exploit
        if (pet.isOnGround() || pet.isInsideVehicle()) pet.setFallDistance(0.0F);
        pet.setGravity(true);
        if (pet.isInsideVehicle()) pet.leaveVehicle();
        if (pet instanceof Mob mob && !hasPlayerPassenger(pet)) mob.setTarget(null);
        pet.setAI(shouldUsePathfinder(definition));
    }

    private void stabilizeRiddenPet(LivingEntity pet) {
        setInvisible(pet, false);
        pet.setCustomNameVisible(true);
        pet.setRemoveWhenFarAway(false);
        pet.setPersistent(true);
        pet.setCanPickupItems(false);
        pet.setCollidable(false);
        pet.setSilent(config.silentPets());
        pet.setGlowing(config.glowEnabled());
        pet.setFireTicks(0);
        // FIX 4: Only reset fall distance when grounded or in vehicle
        if (pet.isOnGround() || pet.isInsideVehicle()) pet.setFallDistance(0.0F);
        pet.setGravity(true);
        pet.setAI(false);
        if (pet instanceof Mob mob) {
            mob.setTarget(null);
            try { mob.getPathfinder().stopPathfinding(); } catch (Throwable ignored) { }
        }
    }

    private boolean hasPlayerPassenger(LivingEntity pet) {
        return !pet.getPassengers().isEmpty() && pet.getPassengers().get(0) instanceof Player;
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
            moveWithVelocity(pet, target, FALLBACK_SPEED);
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

    private void acquireTargetIfNeeded(Player owner, VelioraPet active, PetDefinition definition) {
        if (definition == null || !config.combatEnabled() || ownerNotCombat(owner) || definition.aquaticPet() || definition.flyingPet()) return;
        if (active.targetUuid() != null) {
            Entity current = Bukkit.getEntity(active.targetUuid());
            if (current instanceof LivingEntity living && !living.isDead() && living.isValid() && isAllowedTarget(living) && living.getWorld().equals(owner.getWorld()) && living.getLocation().distanceSquared(owner.getLocation()) <= AUTO_TARGET_RADIUS * AUTO_TARGET_RADIUS) {
                return;
            }
            active.targetUuid(null);
        }
        long now = System.currentTimeMillis();
        UUID ownerUuid = owner.getUniqueId();
        if (now - lastTargetScan.getOrDefault(ownerUuid, 0L) < TARGET_SCAN_COOLDOWN_MILLIS) return;
        lastTargetScan.put(ownerUuid, now);
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (Entity entity : owner.getWorld().getNearbyEntities(owner.getLocation(), AUTO_TARGET_RADIUS, 8.0D, AUTO_TARGET_RADIUS)) {
            if (!(entity instanceof LivingEntity candidate)) continue;
            if (!isAllowedTarget(candidate)) continue;
            if (!manager.redProtectCompat().canMovePet(owner, active.entity().getLocation(), candidate.getLocation())) continue;
            double score = candidate.getLocation().distanceSquared(owner.getLocation());
            if (candidate instanceof Mob mob && mob.getTarget() != null && mob.getTarget().getUniqueId().equals(owner.getUniqueId())) score -= 80.0D;
            if (score < bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        if (best != null) active.targetUuid(best.getUniqueId());
    }

    private void combat(Player owner, VelioraPet active, PetDefinition definition) {
        if (definition == null || !config.combatEnabled() || ownerNotCombat(owner) || active.targetUuid() == null) return;
        Entity targetEntity = Bukkit.getEntity(active.targetUuid());
        if (!(targetEntity instanceof LivingEntity target) || target.isDead() || !target.isValid() || !isAllowedTarget(target)) {
            active.targetUuid(null);
            return;
        }
        LivingEntity pet = active.entity();
        if (!target.getWorld().equals(pet.getWorld())) { active.targetUuid(null); return; }
        if (!manager.redProtectCompat().canMovePet(owner, pet.getLocation(), target.getLocation())) { active.targetUuid(null); return; }
        double distance = pet.getLocation().distance(target.getLocation());
        if (distance > 18.0D) { active.targetUuid(null); return; }
        face(pet, target.getLocation());
        if (distance > config.attackRange()) {
            if (shouldUsePathfinder(definition) && pet instanceof Mob mob) {
                try { mob.getPathfinder().moveTo(target.getLocation(), PATHFINDER_SPEED); return; } catch (Throwable ignored) { }
            }
            moveWithVelocity(pet, target.getLocation(), COMBAT_SPEED);
            return;
        }
        long now = System.currentTimeMillis();
        if (now - active.lastAttackMillis() < config.attackCooldownSeconds() * 1000L) return;
        double damage = Math.max(0.5D, definition.damage() * config.petDamageMultiplier());
        target.damage(damage, owner);
        active.lastAttackMillis(now);
        OwnedPet owned = manager.playerData(owner.getUniqueId()).get(active.petId());
        if (owned != null) {
            owned.addExp(2, config.maxLevel());
            manager.data().save(owner.getUniqueId());
        }
    }

    private boolean isAllowedTarget(LivingEntity target) {
        if (target == null || target instanceof Player || target.getScoreboardTags().contains(PET_TAG)) return false;
        if (!config.allowAttackPassive() && !(target instanceof Monster)) return false;
        return true;
    }

    private void moveWithVelocity(LivingEntity pet, Location target, double speed) {
        Vector velocity = target.toVector().subtract(pet.getLocation().toVector());

        // Hanya gunakan arah horizontal
        velocity.setY(0.0D);

        if (velocity.lengthSquared() <= 0.01D) return;

        velocity.normalize().multiply(speed);

        if (pet.isOnGround() && shouldStepUp(pet, velocity.clone())) {
            // Naik 1 block
            velocity.setY(0.20D);
        } else {
            // Jangan pernah memberi dorongan ke atas saat di udara
            velocity.setY(Math.min(pet.getVelocity().getY(), 0.0D));
        }

        pet.setVelocity(velocity);
    }

    private boolean shouldStepUp(LivingEntity pet, Vector direction) {
        if (!pet.isOnGround() || direction.lengthSquared() < 0.01D) return false;
        direction.normalize();
        Location front = pet.getLocation().clone().add(direction.multiply(0.85D));
        return front.getBlock().getType().isSolid()
                && !front.clone().add(0.0D, 1.0D, 0.0D).getBlock().getType().isSolid()
                && !front.clone().add(0.0D, 2.0D, 0.0D).getBlock().getType().isSolid();
    }

    private void face(LivingEntity pet, Location target) {
        Vector diff = target.toVector().subtract(pet.getLocation().toVector());
        if (diff.lengthSquared() < 0.01D) return;
        float yaw = (float) Math.toDegrees(Math.atan2(-diff.getX(), diff.getZ()));
        try {
            pet.setRotation(yaw, 0.0F);
        } catch (Throwable ignored) {
            Location location = pet.getLocation();
            location.setYaw(yaw);
            location.setPitch(0.0F);
            pet.teleport(location);
        }
    }

    private boolean shouldUsePathfinder(PetDefinition definition) {
        if (definition == null || definition.aquaticPet() || definition.flyingPet()) return false;
        String type = definition.entityType().name();
        return switch (type) {
            case "COW", "SHEEP", "PIG", "CHICKEN", "RABBIT", "TURTLE", "GOAT", "CAMEL",
                    "HORSE", "DONKEY", "MULE", "LLAMA", "TRADER_LLAMA",
                    "FOX", "PANDA", "CAT", "WOLF", "FROG", "AXOLOTL", "ARMADILLO", "SNIFFER",
                    "MOOSHROOM", "MUSHROOM_COW", "IRON_GOLEM", "SNOW_GOLEM", "RAVAGER",
                    "ZOMBIE", "HUSK", "DROWNED", "ZOMBIE_VILLAGER", "SLIME", "MAGMA_CUBE",
                    "SKELETON", "STRAY", "BOGGED", "SPIDER", "CAVE_SPIDER", "ENDERMAN",
                    "PILLAGER", "VINDICATOR", "EVOKER", "PIGLIN", "PIGLIN_BRUTE",
                    "VILLAGER", "WANDERING_TRADER", "STRIDER", "WARDEN",
                    "SKELETON_HORSE", "ZOMBIE_HORSE" -> true;
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
