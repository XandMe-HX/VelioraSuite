package id.velioragardens.veliorasuite.module.pets;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.module.pets.model.PetDefinition;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.UUID;

public final class PetFlyingFollowTask implements Runnable {
    private static final String PET_TAG = "veliorapets_pet";
    private static final double HOVER_Y_GROUND = 1.4D;
    private static final double HOVER_Y_FLYING = 0.6D;
    private static final double SIDE_OFFSET = 1.2D;
    private static final double BACK_OFFSET = 1.4D;
    private static final double FOLLOW_SPEED = 0.32D;
    private static final double FAST_FOLLOW_SPEED = 0.55D;
    private static final double TELEPORT_DISTANCE = 24.0D;
    private static final double MAX_SOFT_DISTANCE = 10.0D;
    private static final double ORBIT_RADIUS = 1.2D;
    private static final double ORBIT_SPEED = 0.12D;

    private final VelioraSuite plugin;
    private final PetConfigManager config;
    private final NamespacedKey ownerKey;
    private final NamespacedKey petIdKey;

    public PetFlyingFollowTask(VelioraSuite plugin, PetConfigManager config) {
        this.plugin = plugin;
        this.config = config;
        this.ownerKey = new NamespacedKey(plugin, "veliorapets_owner_uuid");
        this.petIdKey = new NamespacedKey(plugin, "veliorapets_pet_id");
    }

    @Override
    public void run() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (!entity.getScoreboardTags().contains(PET_TAG)) continue;
                if (!(entity instanceof LivingEntity pet)) continue;
                String petId = pet.getPersistentDataContainer().get(petIdKey, PersistentDataType.STRING);
                if (petId == null) continue;
                PetDefinition definition = config.pets().get(petId);
                if (definition == null || !definition.flyingPet()) continue;
                Player owner = ownerOf(pet);
                if (owner == null || !owner.isOnline()) continue;
                follow(owner, pet);
            }
        }
    }

    private Player ownerOf(LivingEntity pet) {
        String raw = pet.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        if (raw == null) return null;
        try { return Bukkit.getPlayer(UUID.fromString(raw)); } catch (IllegalArgumentException ignored) { return null; }
    }

    private void follow(Player owner, LivingEntity pet) {
        pet.setAI(false);
        pet.setGravity(false);
        pet.setCollidable(false);
        pet.setFireTicks(0);
        pet.setFallDistance(0.0F);
        if (pet instanceof Mob mob) mob.setTarget(null);

        Location target = targetLocation(owner);
        if (!pet.getWorld().equals(owner.getWorld())) {
            pet.teleport(target);
            return;
        }

        double distance = pet.getLocation().distance(target);
        if (distance > TELEPORT_DISTANCE) {
            pet.teleport(target);
            pet.setVelocity(new Vector(0, 0.02D, 0));
            return;
        }

        Vector delta = target.toVector().subtract(pet.getLocation().toVector());
        if (delta.lengthSquared() < 0.04D) {
            pet.setVelocity(new Vector(0.0D, 0.03D, 0.0D));
            return;
        }

        double speed = distance > MAX_SOFT_DISTANCE ? FAST_FOLLOW_SPEED : FOLLOW_SPEED;
        Vector velocity = delta.normalize().multiply(speed);
        velocity.setY(clamp(velocity.getY(), -0.45D, 0.45D));
        pet.setVelocity(velocity);
    }

    private Location targetLocation(Player owner) {
        Location base = owner.getLocation().clone();
        Vector direction = base.getDirection();
        direction.setY(0.0D);
        if (direction.lengthSquared() < 0.01D) direction = new Vector(0, 0, 1);
        direction.normalize();
        Vector side = new Vector(-direction.getZ(), 0.0D, direction.getX()).normalize().multiply(SIDE_OFFSET);
        Vector back = direction.clone().multiply(-BACK_OFFSET);
        double yOffset = isOwnerFlying(owner) ? HOVER_Y_FLYING : HOVER_Y_GROUND;

        if (base.getWorld() != null) {
            double angle = (System.currentTimeMillis() / 50.0D) * ORBIT_SPEED;
            side.add(new Vector(Math.cos(angle) * ORBIT_RADIUS, 0.0D, Math.sin(angle) * ORBIT_RADIUS));
        }

        base.add(side).add(back).add(0.0D, yOffset, 0.0D);
        return base;
    }

    private boolean isOwnerFlying(Player owner) {
        return owner.isFlying() || owner.isGliding() || !owner.isOnGround();
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
