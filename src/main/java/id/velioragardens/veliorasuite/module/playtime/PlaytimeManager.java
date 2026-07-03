package id.velioragardens.veliorasuite.module.playtime;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.module.playtime.PlaytimeDataManager.PlaytimePlayerData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PlaytimeManager {

    private final VelioraSuite plugin;
    private final PlaytimeConfigManager config;
    private final PlaytimeDataManager data;
    private final Map<UUID, PlaytimePlayerData> players = new HashMap<>();
    private final Map<UUID, Long> activeStart = new HashMap<>();
    private BukkitTask cleanupTask;

    public PlaytimeManager(VelioraSuite plugin, PlaytimeConfigManager config, PlaytimeDataManager data) {
        this.plugin = plugin;
        this.config = config;
        this.data = data;
    }

    public void load() {
        data.load();
        players.clear();
        players.putAll(data.loadPlayers());
        cleanupExpiredPending();
        for (Player player : Bukkit.getOnlinePlayers()) onJoin(player);
    }

    public void start() {
        stopTask();
        cleanupTask = Bukkit.getScheduler().runTaskTimer(plugin, this::cleanupExpiredPending, 20L * 60L, 20L * 60L);
    }

    public void shutdown() {
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) markPending(player, now);
        activeStart.clear();
        cleanupExpiredPending();
        save();
        stopTask();
    }

    public void reload() {
        save();
        load();
    }

    public void onJoin(Player player) {
        if (player == null) return;
        long now = System.currentTimeMillis();
        UUID uuid = player.getUniqueId();
        PlaytimePlayerData playerData = players.computeIfAbsent(uuid, PlaytimePlayerData::new);
        playerData.name = player.getName();
        playerData.lastSeen = now;

        if (playerData.pendingStart > 0L && playerData.pendingDisconnectedAt > 0L) {
            long graceMillis = config.reconnectGraceMinutes() * 60_000L;
            if (now - playerData.pendingDisconnectedAt <= graceMillis) {
                activeStart.put(uuid, playerData.pendingStart);
            } else {
                finishPending(playerData);
                activeStart.put(uuid, now);
            }
            playerData.pendingStart = 0L;
            playerData.pendingDisconnectedAt = 0L;
        } else {
            activeStart.put(uuid, now);
        }
        save();
    }

    public void onQuit(Player player) {
        if (player == null) return;
        markPending(player, System.currentTimeMillis());
        save();
    }

    public long currentSessionMillis(UUID uuid) {
        long now = System.currentTimeMillis();
        Long start = activeStart.get(uuid);
        if (start != null) return Math.max(0L, now - start);
        PlaytimePlayerData playerData = players.get(uuid);
        if (playerData == null || playerData.pendingStart <= 0L || playerData.pendingDisconnectedAt <= 0L) return 0L;
        if (now - playerData.pendingDisconnectedAt <= config.reconnectGraceMinutes() * 60_000L) {
            return Math.max(0L, playerData.pendingDisconnectedAt - playerData.pendingStart);
        }
        return 0L;
    }

    public long bestSessionMillis(UUID uuid) {
        PlaytimePlayerData playerData = players.get(uuid);
        long best = playerData == null ? 0L : playerData.bestSessionMillis;
        return Math.max(best, currentSessionMillis(uuid));
    }

    public long lastSessionMillis(UUID uuid) {
        PlaytimePlayerData playerData = players.get(uuid);
        return playerData == null ? 0L : playerData.lastSessionMillis;
    }

    public List<PlaytimeEntry> top(int limit) {
        cleanupExpiredPending();
        List<PlaytimeEntry> entries = new ArrayList<>();
        for (PlaytimePlayerData playerData : players.values()) {
            long best = Math.max(playerData.bestSessionMillis, currentSessionMillis(playerData.uuid));
            if (best <= 0L) continue;
            entries.add(new PlaytimeEntry(playerData.uuid, playerData.name, best));
        }
        entries.sort(Comparator.comparingLong(PlaytimeEntry::millis).reversed());
        return entries.stream().limit(Math.max(1, limit)).toList();
    }

    public String format(long millis) {
        long seconds = Math.max(0L, millis / 1000L);
        long days = seconds / 86_400L;
        long hours = (seconds % 86_400L) / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        long secs = seconds % 60L;
        if (days > 0L) return days + "d " + hours + "h " + minutes + "m";
        if (hours > 0L) return hours + "h " + minutes + "m";
        if (minutes > 0L) return minutes + "m " + secs + "s";
        return secs + "s";
    }

    public void save() {
        data.savePlayers(players);
    }

    private void markPending(Player player, long now) {
        UUID uuid = player.getUniqueId();
        PlaytimePlayerData playerData = players.computeIfAbsent(uuid, PlaytimePlayerData::new);
        playerData.name = player.getName();
        playerData.lastSeen = now;
        Long start = activeStart.remove(uuid);
        playerData.pendingStart = start == null ? now : start;
        playerData.pendingDisconnectedAt = now;
    }

    private void cleanupExpiredPending() {
        long now = System.currentTimeMillis();
        long graceMillis = config.reconnectGraceMinutes() * 60_000L;
        boolean changed = false;
        for (PlaytimePlayerData playerData : players.values()) {
            if (playerData.pendingStart <= 0L || playerData.pendingDisconnectedAt <= 0L) continue;
            if (now - playerData.pendingDisconnectedAt > graceMillis) {
                finishPending(playerData);
                changed = true;
            }
        }
        if (changed) save();
    }

    private void finishPending(PlaytimePlayerData playerData) {
        long sessionMillis = Math.max(0L, playerData.pendingDisconnectedAt - playerData.pendingStart);
        playerData.lastSessionMillis = sessionMillis;
        playerData.bestSessionMillis = Math.max(playerData.bestSessionMillis, sessionMillis);
        playerData.pendingStart = 0L;
        playerData.pendingDisconnectedAt = 0L;
    }

    private void stopTask() {
        if (cleanupTask != null) cleanupTask.cancel();
        cleanupTask = null;
    }

    public record PlaytimeEntry(UUID uuid, String name, long millis) { }
}
