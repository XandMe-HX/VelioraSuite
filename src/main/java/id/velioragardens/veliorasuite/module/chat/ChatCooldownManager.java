package id.velioragardens.veliorasuite.module.chat;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ChatCooldownManager {

    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public long getRemainingSeconds(UUID uuid) {
        long remainingMillis = cooldowns.getOrDefault(uuid, 0L) - System.currentTimeMillis();
        if (remainingMillis <= 0L) {
            cooldowns.remove(uuid);
            return 0L;
        }
        return Math.max(1L, (remainingMillis + 999L) / 1000L);
    }

    public void setCooldown(UUID uuid, int seconds) {
        if (uuid == null || seconds <= 0) return;
        cooldowns.put(uuid, System.currentTimeMillis() + (seconds * 1000L));
    }

    public void clear() {
        cooldowns.clear();
    }
}
