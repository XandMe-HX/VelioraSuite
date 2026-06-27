package id.velioragardens.veliorasuite.module.pets;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
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
        Vector direction = rider.getLocation().getDirection();
        direction.setY(0.0D);
        if (direction.lengthSquared() < 0.01D) return;
        Vector velocity = direction.normalize().multiply(config.rideSpeed());
        if (!pet.isOnGround()) velocity.setY(Math.min(0.0D, pet.getVelocity().getY()));
        pet.setVelocity(velocity);
        pet.setFallDistance(0.0F);
    }
}
