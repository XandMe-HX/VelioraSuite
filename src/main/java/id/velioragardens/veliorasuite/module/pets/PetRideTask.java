package id.velioragardens.veliorasuite.module.pets;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.lang.reflect.Method;

public final class PetRideTask implements Runnable {
    private final PetManager manager;
    private final PetConfigManager config;
    private boolean inputApiChecked;
    private boolean inputApiAvailable;

    public PetRideTask(PetManager manager) {
        this.manager = manager;
        this.config = manager.config();
    }

    @Override
    public void run() {
        // Hanya snapshot pet aktif milik pemain. Ini menghindari scan semua entity
        // setiap dua tick yang bisa berat di world survival besar.
        for (LivingEntity pet : manager.activePetEntities()) {
            if (pet == null || pet.isDead() || pet.getPassengers().isEmpty()) continue;
            if (!(pet.getPassengers().get(0) instanceof Player rider)) continue;
            tickRide(pet, rider);
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
            try { mob.getPathfinder().stopPathfinding(); } catch (Throwable ignored) { }
        }
        boolean flying = config.isAllowedFlyingEntity(pet.getType());
        // Semua AI dimatikan saat dinaiki agar mob tidak memilih target atau
        // berjalan sendiri. Gerak sepenuhnya dari input penunggang.
        pet.setAI(false);
        pet.setGravity(!flying);
        pet.setFallDistance(0.0F);
        pet.setFireTicks(0);

        Location riderLocation = rider.getLocation();
        RideInput input = readInput(rider);
        Vector direction = movementDirection(riderLocation, input);

        facePetToRider(pet, riderLocation);

        if (direction.lengthSquared() < 0.01D) {
            Vector stop = pet.getVelocity();
            stop.setX(0.0D);
            stop.setZ(0.0D);
            if (pet.isOnGround()) stop.setY(0.0D);
            pet.setVelocity(stop);
            return;
        }

        direction.normalize();
        Vector velocity = direction.clone().multiply((flying ? config.rideFlySpeed() : config.rideSpeed()) * input.speedMultiplier());
        if (flying) {
            if (input.jump() && pet.getLocation().getY() < pet.getWorld().getMaxHeight() - 4) velocity.setY(config.rideFlyVerticalSpeed());
            else if (input.forward() < 0.0D) velocity.setY(-config.rideFlyVerticalSpeed());
            else velocity.setY(0.0D);
        } else if (input.jump() || shouldStepUp(pet, direction)) {
            velocity.setY(config.rideJumpY());
        } else if (!pet.isOnGround()) {
            velocity.setY(Math.min(0.0D, pet.getVelocity().getY()));
        } else {
            velocity.setY(Math.max(0.0D, pet.getVelocity().getY()));
        }
        pet.setVelocity(velocity);
    }

    private Vector movementDirection(Location riderLocation, RideInput input) {
        Vector forward = riderLocation.getDirection();
        forward.setY(0.0D);
        if (forward.lengthSquared() < 0.01D) forward = new Vector(0.0D, 0.0D, 1.0D);
        forward.normalize();
        Vector right = new Vector(-forward.getZ(), 0.0D, forward.getX()).normalize();

        if (!input.supported()) return new Vector();
        Vector result = new Vector(0.0D, 0.0D, 0.0D);
        result.add(forward.multiply(input.forward()));
        result.add(right.multiply(input.strafe()));
        return result;
    }

    private RideInput readInput(Player rider) {
        try {
            Method getCurrentInput = rider.getClass().getMethod("getCurrentInput");
            Object input = getCurrentInput.invoke(rider);
            if (input == null) return RideInput.unsupported();
            inputApiChecked = true;
            inputApiAvailable = true;
            double forward = 0.0D;
            double strafe = 0.0D;
            if (bool(input, "isForward", "forward")) forward += 1.0D;
            if (bool(input, "isBackward", "backward")) forward -= 0.65D;
            if (bool(input, "isLeft", "left")) strafe += 0.75D;
            if (bool(input, "isRight", "right")) strafe -= 0.75D;
            boolean jump = bool(input, "isJump", "jump", "isJumping");
            return new RideInput(true, forward, strafe, jump);
        } catch (Throwable ignored) {
            if (!inputApiChecked) {
                inputApiChecked = true;
                inputApiAvailable = false;
            }
            return RideInput.unsupported();
        }
    }

    private boolean bool(Object input, String... names) {
        for (String name : names) {
            try {
                Method method = input.getClass().getMethod(name);
                Object value = method.invoke(input);
                if (value instanceof Boolean bool) return bool;
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    private boolean shouldStepUp(LivingEntity pet, Vector direction) {
        if (!pet.isOnGround() || direction.lengthSquared() < 0.01D) return false;
        Location front = pet.getLocation().clone().add(direction.clone().normalize().multiply(0.85D));
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

    private record RideInput(boolean supported, double forward, double strafe, boolean jump) {
        static RideInput unsupported() { return new RideInput(false, 1.0D, 0.0D, false); }
        double speedMultiplier() { return forward < 0.0D ? 0.65D : 1.0D; }
    }
}
