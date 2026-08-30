package id.velioragardens.veliorasuite.module.chat;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ChatCooldownManager {

    /** Chat events can be asynchronous on Paper, so this map must be safe for concurrent access. */
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

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

    public void clear(UUID uuid) {
        if (uuid != null) cooldowns.remove(uuid);
    }
}
