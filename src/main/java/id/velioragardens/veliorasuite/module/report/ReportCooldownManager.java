package id.velioragardens.veliorasuite.module.report;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ReportCooldownManager {

    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public boolean isOnCooldown(UUID uuid) {
        return getRemainingMillis(uuid) > 0L;
    }

    public long getRemainingMillis(UUID uuid) {
        if (uuid == null) {
            return 0L;
        }

        long expiresAt = cooldowns.getOrDefault(uuid, 0L);
        long remaining = expiresAt - System.currentTimeMillis();

        if (remaining <= 0L) {
            cooldowns.remove(uuid);
            return 0L;
        }

        return remaining;
    }

    public void setCooldown(UUID uuid, long cooldownMillis) {
        if (uuid == null || cooldownMillis <= 0L) {
            return;
        }

        cooldowns.put(uuid, System.currentTimeMillis() + cooldownMillis);
    }

    public void clear() {
        cooldowns.clear();
    }

    public String formatTime(long millis) {
        long seconds = Math.max(0L, millis / 1000L);
        long minutes = seconds / 60L;
        seconds %= 60L;

        if (minutes > 0) {
            return minutes + "m " + seconds + "s";
        }

        return seconds + "s";
    }
}
