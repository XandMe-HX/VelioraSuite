package id.velioragardens.veliorasuite.module.pets;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public final class PetRideTask implements Runnable {
    private final PetConfigManager config;

    public PetRideTask(PetConfigManager config) {
        this.config = config;
    }

    @Override
    public void run() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (!entity.getScoreboardTags().contains("veliorapets_pet")) continue;
                if (!(entity instanceof LivingEntity pet)) continue;
                if (pet.getPassengers().isEmpty()) continue;
                if (!(pet.getPassengers().get(0) instanceof Player rider)) continue;
                tickRide(pet, rider);
            }
        }
    }

    private void tickRide(LivingEntity pet, Player rider) {
        if (!rider.getWorld().equals(pet.getWorld()) || rider.getLocation().distanceSquared(pet.getLocation()) > 64.0D) {
            pet.removePassenger(rider);
            return;
        }
        if (rider.isSneaking()) {
            pet.removePassenger(rider);
            rider.sendMessage(config.color(config.message("pet-ride-stop", "%prefix% &eKamu turun dari &f%pet%&e.").replace("%pet%", pet.getName())));
            return;
        }

        if (pet instanceof Mob mob) {
            mob.setTarget(null);
            mob.getPathfinder().stopPathfinding();
        }
        pet.setAI(false);
        pet.setGravity(true);
        pet.setFallDistance(0.0F);
        pet.setFireTicks(0);

        Location riderLocation = rider.getLocation();
        Vector direction = riderLocation.getDirection();
        direction.setY(0.0D);
        if (direction.lengthSquared() < 0.01D) return;
        direction.normalize();

        facePetToRider(pet, riderLocation);

        Vector velocity = direction.clone().multiply(config.rideSpeed());
        if (shouldStepUp(pet, direction)) {
            velocity.setY(config.rideJumpY());
        } else if (!pet.isOnGround()) {
            velocity.setY(Math.min(0.0D, pet.getVelocity().getY()));
        }
        pet.setVelocity(velocity);
    }

    private boolean shouldStepUp(LivingEntity pet, Vector direction) {
        if (!pet.isOnGround()) return false;
        Location front = pet.getLocation().clone().add(direction.clone().multiply(0.85D));
        Block feet = front.getBlock();
        Block head = front.clone().add(0.0D, 1.0D, 0.0D).getBlock();
        Block aboveHead = front.clone().add(0.0D, 2.0D, 0.0D).getBlock();
        return feet.getType().isSolid() && !head.getType().isSolid() && !aboveHead.getType().isSolid();
    }

    private void facePetToRider(LivingEntity pet, Location riderLocation) {
        try {
            pet.setRotation(riderLocation.getYaw(), 0.0F);
        } catch (Throwable ignored) {
            Location location = pet.getLocation();
            location.setYaw(riderLocation.getYaw());
            location.setPitch(0.0F);
            pet.teleport(location);
        }
    }
}
