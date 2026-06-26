package id.velioragardens.veliorasuite.module.trader;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.module.trader.model.TraderLocation;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public final class TraderSpawnManager {

    private final VelioraSuite plugin;
    private final TraderConfigManager configManager;
    private final TraderDataManager dataManager;
    private final TraderManager traderManager;
    private final Random random = new Random();
    private BukkitTask task;
    private long nextSpawnAt;
    private long nextReminderAt;

    public TraderSpawnManager(VelioraSuite plugin, TraderConfigManager configManager, TraderDataManager dataManager, TraderManager traderManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.dataManager = dataManager;
        this.traderManager = traderManager;
    }

    public void start() {
        stop();
        long fallback = System.currentTimeMillis() + configManager.getIntervalMinutes() * 60_000L;
        nextSpawnAt = dataManager.getNextSpawnAt(fallback);
        dataManager.saveNextSpawnAt(nextSpawnAt);
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L * 5L);
    }

    public void stop() {
        if (task != null) task.cancel();
        task = null;
    }

    public void reload() {
        scheduleNextFromNow();
    }

    public long getNextSpawnAt() {
        return nextSpawnAt;
    }

    public void scheduleNextFromNow() {
        nextSpawnAt = System.currentTimeMillis() + configManager.getIntervalMinutes() * 60_000L;
        dataManager.saveNextSpawnAt(nextSpawnAt);
    }

    public void resetReminderClock() {
        nextReminderAt = System.currentTimeMillis() + configManager.getReminderMinutes() * 60_000L;
    }

    public Location findSpawnLocation() {
        List<Location> candidates = new ArrayList<>();
        if (configManager.isRandomFromConfigLocations() && !configManager.getLocations().isEmpty()) {
            List<TraderLocation> locations = new ArrayList<>(configManager.getLocations());
            Collections.shuffle(locations);
            for (TraderLocation traderLocation : locations) {
                Location location = prepareSpawnLocation(traderLocation.toLocation());
                if (location != null) return location;
            }
        }
        for (org.bukkit.entity.Player player : Bukkit.getOnlinePlayers()) {
            Location location = randomNear(player);
            if (location != null) candidates.add(location);
        }
        Collections.shuffle(candidates);
        for (Location candidate : candidates) {
            Location location = prepareSpawnLocation(candidate);
            if (location != null) return location;
        }
        return null;
    }

    public Location prepareSpawnLocation(Location input) {
        if (input == null || input.getWorld() == null) return null;
        Location base = input.getBlock().getLocation();
        base.getChunk().load(true);
        if (isSafe(base)) return base;
        World world = base.getWorld();
        Block highest = world.getHighestBlockAt(base.getBlockX(), base.getBlockZ());
        Location highestLocation = highest.getLocation();
        highestLocation.getChunk().load(true);
        if (isSafe(highestLocation)) return highestLocation;
        return null;
    }

    private void tick() {
        if (!configManager.isEnabled() || !configManager.isSpawnEnabled()) return;
        long now = System.currentTimeMillis();
        if (traderManager.isActive()) {
            if (now >= traderManager.getDespawnAt()) {
                traderManager.despawn(true);
                return;
            }
            if (nextReminderAt > 0L && now >= nextReminderAt) {
                traderManager.broadcastReminder();
                resetReminderClock();
            }
            return;
        }
        if (now >= nextSpawnAt) {
            Location location = findSpawnLocation();
            if (!traderManager.spawn(location)) scheduleNextFromNow();
        }
    }

    private Location randomNear(org.bukkit.entity.Player player) {
        if (player == null || player.getWorld() == null) return null;
        World world = player.getWorld();
        for (int i = 0; i < configManager.getMaxRandomAttempts(); i++) {
            int distance = 24 + random.nextInt(96);
            double angle = random.nextDouble() * Math.PI * 2.0D;
            int x = player.getLocation().getBlockX() + (int) Math.round(Math.cos(angle) * distance);
            int z = player.getLocation().getBlockZ() + (int) Math.round(Math.sin(angle) * distance);
            Block highest = world.getHighestBlockAt(x, z);
            Location location = highest.getLocation();
            if (isSafe(location)) return location;
        }
        return null;
    }

    private boolean isSafe(Location location) {
        if (location == null || location.getWorld() == null) return false;
        Block ground = location.getBlock();
        Block feet = ground.getRelative(0, 1, 0);
        Block head = ground.getRelative(0, 2, 0);
        Material groundType = ground.getType();
        if (!groundType.isSolid()) return false;
        if (groundType == Material.LAVA || groundType == Material.WATER) return false;
        if (!feet.isPassable() || !head.isPassable()) return false;
        Block below = ground.getRelative(0, -1, 0);
        return below.getY() <= ground.getY() && !below.isLiquid();
    }
}
