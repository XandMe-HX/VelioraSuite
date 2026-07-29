package id.velioragardens.veliorasuite.module.pets;

import org.bukkit.Location;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PetMovementDebug {
    private static final Map<UUID, String> REASON = new HashMap<>();
    private static final Map<UUID, String> TARGET = new HashMap<>();
    private static final Map<UUID, Boolean> REDPROTECT_ALLOWED = new HashMap<>();
    private static final Map<UUID, Boolean> PATHFINDER_USED = new HashMap<>();
    private static final Map<UUID, String> TELEPORT_REASON = new HashMap<>();

    private PetMovementDebug() {}

    public static void remember(UUID owner, String reason, Location target, boolean redProtectAllowed, boolean pathfinderUsed, String teleportReason) {
        if (owner == null) return;
        REASON.put(owner, reason == null ? "none" : reason);
        TARGET.put(owner, format(target));
        REDPROTECT_ALLOWED.put(owner, redProtectAllowed);
        PATHFINDER_USED.put(owner, pathfinderUsed);
        TELEPORT_REASON.put(owner, teleportReason == null ? "none" : teleportReason);
    }

    public static String reason(UUID owner) { return REASON.getOrDefault(owner, "none"); }
    public static String target(UUID owner) { return TARGET.getOrDefault(owner, "none"); }
    public static boolean redProtectAllowed(UUID owner) { return REDPROTECT_ALLOWED.getOrDefault(owner, false); }
    public static boolean pathfinderUsed(UUID owner) { return PATHFINDER_USED.getOrDefault(owner, false); }
    public static String teleportReason(UUID owner) { return TELEPORT_REASON.getOrDefault(owner, "none"); }

    private static String format(Location location) {
        if (location == null || location.getWorld() == null) return "none";
        return location.getWorld().getName() + "," + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ();
    }
}
