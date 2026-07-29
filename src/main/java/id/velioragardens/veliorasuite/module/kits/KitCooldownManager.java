package id.velioragardens.veliorasuite.module.kits;

import id.velioragardens.veliorasuite.module.kits.model.Kit;

import java.util.UUID;

public final class KitCooldownManager {

    private final KitsDataManager dataManager;

    public KitCooldownManager(KitsDataManager dataManager) {
        this.dataManager = dataManager;
    }

    public long getRemainingMillis(UUID uuid, Kit kit) {
        if (kit.getCooldownMillis() <= 0) {
            return 0L;
        }

        long lastClaim = dataManager.getLastClaim(uuid, kit.getId());
        long endTime = lastClaim + kit.getCooldownMillis();
        long remaining = endTime - System.currentTimeMillis();
        return Math.max(0L, remaining);
    }

    public boolean isOnCooldown(UUID uuid, Kit kit) {
        return getRemainingMillis(uuid, kit) > 0L;
    }

    public String formatTime(long millis) {
        long seconds = Math.max(0L, millis / 1000L);
        long days = seconds / 86_400L;
        seconds %= 86_400L;
        long hours = seconds / 3_600L;
        seconds %= 3_600L;
        long minutes = seconds / 60L;
        seconds %= 60L;

        if (days > 0) {
            return days + "d " + hours + "h";
        }
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        if (minutes > 0) {
            return minutes + "m " + seconds + "s";
        }
        return seconds + "s";
    }
}
