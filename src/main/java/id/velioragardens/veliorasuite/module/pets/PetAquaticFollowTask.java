package id.velioragardens.veliorasuite.module.pets;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.module.pets.model.PetDefinition;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.lang.reflect.Method;
import java.util.UUID;

public final class PetAquaticFollowTask implements Runnable {
    public static final String PET_TAG = "veliorapets_pet";
    public static final String ANCHOR_TAG = "veliorapets_aquatic_anchor";

    private static final double SIDE_OFFSET = 1.2D;
    private static final double BACK_OFFSET = 1.0D;
    private static final double HOVER_Y = 1.35D;
    private static final double WATER_HOVER_Y = 0.7D;
    private static final double TELEPORT_DISTANCE = 12.0D;
    private static final double LERP_STRENGTH = 0.30D;
    private static final int BUBBLE_INTERVAL_TICKS = 10;

    private final PetConfigManager config;
    private final NamespacedKey ownerKey;
    private final NamespacedKey petIdKey;
    private int tick;

    public PetAquaticFollowTask(VelioraSuite plugin, PetConfigManager config) {
        this.config = config;
        this.ownerKey = new NamespacedKey(plugin, "veliorapets_owner_uuid");
        this.petIdKey = new NamespacedKey(plugin, "veliorapets_pet_id");
    }

    @Override
    public void run() {
        tick += 2;
        cleanupAnchors();
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (!entity.getScoreboardTags().contains(PET_TAG)) continue;
                if (!(entity instanceof LivingEntity pet)) continue;
                String petId = pet.getPersistentDataContainer().get(petIdKey, PersistentDataType.STRING);
                String ownerRaw = pet.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
                if (petId == null || ownerRaw == null) continue;
                PetDefinition definition = config.pets().get(petId);
                if (definition == null || !definition.aquaticPet()) continue;
                Player owner = ownerOf(ownerRaw);
                if (owner == null || !owner.isOnline()) continue;
                follow(owner, pet);
            }
        }
    }

    private Player ownerOf(String raw) {
        try { return Bukkit.getPlayer(UUID.fromString(raw)); } catch (IllegalArgumentException ignored) { return null; }
    }

    private void follow(Player owner, LivingEntity pet) {
        preparePet(pet);
        if (pet.isInsideVehicle()) pet.leaveVehicle();
        Location target = safeTargetLocation(owner);
        if (!pet.getWorld().equals(owner.getWorld())) {
            pet.teleport(target);
            return;
        }
        double distance = pet.getLocation().distance(target);
        if (distance > TELEPORT_DISTANCE || pet.isOnGround()) {
            pet.teleport(target);
        } else {
            Location current = pet.getLocation();
            Vector delta = target.toVector().subtract(current.toVector()).multiply(LERP_STRENGTH);
            Location next = current.clone().add(delta);
            next.setYaw(target.getYaw());
            next.setPitch(target.getPitch());
            pet.teleport(next);
        }
        preparePet(pet);
        if (tick % BUBBLE_INTERVAL_TICKS == 0) {
            pet.getWorld().spawnParticle(Particle.BUBBLE_POP, pet.getLocation().clone().add(0.0D, 0.25D, 0.0D), 1, 0.08D, 0.08D, 0.08D, 0.0D);
        }
    }

    private void preparePet(LivingEntity pet) {
        setInvisible(pet, false);
        pet.setCustomNameVisible(true);
        pet.setAI(false);
        pet.setGravity(false);
        pet.setSilent(true);
        pet.setInvulnerable(true);
        pet.setCollidable(false);
        pet.setRemoveWhenFarAway(false);
        pet.setPersistent(false);
        pet.setCanPickupItems(false);
        pet.setFireTicks(0);
        pet.setFallDistance(0.0F);
        pet.setRemainingAir(pet.getMaximumAir());
        if (pet instanceof Mob mob) mob.setTarget(null);
    }

    private Location safeTargetLocation(Player owner) {
        Location base = owner.getLocation().clone();
        Vector direction = base.getDirection();
        direction.setY(0.0D);
        if (direction.lengthSquared() < 0.01D) direction = new Vector(0.0D, 0.0D, 1.0D);
        direction.normalize();
        Vector side = new Vector(-direction.getZ(), 0.0D, direction.getX()).normalize().multiply(SIDE_OFFSET);
        Vector back = direction.clone().multiply(-BACK_OFFSET);
        double yOffset = isInWater(owner) ? WATER_HOVER_Y : HOVER_Y;
        Location target = base.add(side).add(back).add(0.0D, yOffset, 0.0D);
        target.setY(Math.max(target.getY(), owner.getLocation().getY() + 1.1D));
        target.setYaw(owner.getLocation().getYaw());
        target.setPitch(0.0F);
        for (int i = 0; i <= 4; i++) {
            if (isSafeSpace(target)) return target;
            target.add(0.0D, 0.5D, 0.0D);
        }
        return target;
    }

    private boolean isInWater(Player owner) {
        return owner.getLocation().getBlock().isLiquid() || owner.getEyeLocation().getBlock().isLiquid();
    }

    private boolean isSafeSpace(Location location) {
        Block block = location.getBlock();
        Block above = location.clone().add(0.0D, 0.9D, 0.0D).getBlock();
        return !block.getType().isSolid() && !above.getType().isSolid();
    }

    private void cleanupAnchors() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity.getScoreboardTags().contains(ANCHOR_TAG)) entity.remove();
            }
        }
    }

    private void setInvisible(LivingEntity pet, boolean invisible) {
        try {
            Method method = pet.getClass().getMethod("setInvisible", boolean.class);
            method.invoke(pet, invisible);
        } catch (Exception ignored) {
        }
    }
}
