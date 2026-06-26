package id.velioragardens.veliorasuite.module.trader.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public record TraderLocation(String world, double x, double y, double z) {
    public Location toLocation() {
        World bukkitWorld = Bukkit.getWorld(world);
        if (bukkitWorld == null) return null;
        return new Location(bukkitWorld, x, y, z);
    }
}
