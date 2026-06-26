package id.velioragardens.veliorasuite.module.boss.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public record BossSpawnPoint(String name, String world, double x, double y, double z, float yaw, float pitch) {
    public Location toLocation() {
        World bukkitWorld = Bukkit.getWorld(world);
        if (bukkitWorld == null) return null;
        return new Location(bukkitWorld, x, y, z, yaw, pitch);
    }

    public static BossSpawnPoint from(String name, Location location) {
        return new BossSpawnPoint(name, location.getWorld().getName(), location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
    }
}
