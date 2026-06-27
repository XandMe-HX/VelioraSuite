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

public final class PetAquaticFollowTask implements Runnable {
    private static final String PET_TAG = "veliorapets_pet";
    private static final double HOVER_Y = 0.9D;
    private static final double SIDE_OFFSET = 1.0D;
    private static final double BACK_OFFSET = 1.0D;
    private static final double FOLLOW_SPEED = 0.26D;
    private static final double FAST_SPEED = 0.45D;
    private static final double TELEPORT_DISTANCE = 18.0D;
    private static final double SOFT_DISTANCE = 8.0D;

    private final PetConfigManager config;
    private final NamespacedKey ownerKey;
    private final NamespacedKey petIdKey;

    public PetAquaticFollowTask(VelioraSuite plugin, PetConfigManager config) {
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
                if (definition == null || !definition.aquaticPet()) continue;
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
        pet.setRemainingAir(pet.getMaximumAir());
        if (pet instanceof Mob mob) mob.setTarget(null);

        Location target = targetLocation(owner);
        if (!pet.getWorld().equals(owner.getWorld())) {
            pet.teleport(target);
            return;
        }
        double distance = pet.getLocation().distance(target);
        if (distance > TELEPORT_DISTANCE) {
            pet.teleport(target);
            pet.setVelocity(new Vector(0.0D, 0.02D, 0.0D));
            return;
        }
        Vector delta = target.toVector().subtract(pet.getLocation().toVector());
        if (delta.lengthSquared() < 0.04D) {
            pet.setVelocity(new Vector(0.0D, 0.02D, 0.0D));
            return;
        }
        double speed = distance > SOFT_DISTANCE ? FAST_SPEED : FOLLOW_SPEED;
        Vector velocity = delta.normalize().multiply(speed);
        velocity.setY(clamp(velocity.getY(), -0.30D, 0.30D));
        pet.setVelocity(velocity);
    }

    private Location targetLocation(Player owner) {
        Location base = owner.getLocation().clone();
        Vector direction = base.getDirection();
        direction.setY(0.0D);
        if (direction.lengthSquared() < 0.01D) direction = new Vector(0.0D, 0.0D, 1.0D);
        direction.normalize();
        Vector side = new Vector(-direction.getZ(), 0.0D, direction.getX()).normalize().multiply(SIDE_OFFSET);
        Vector back = direction.clone().multiply(-BACK_OFFSET);
        return base.add(side).add(back).add(0.0D, HOVER_Y, 0.0D);
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
