package id.velioragardens.veliorasuite.module.trader;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.module.trader.model.TraderLocation;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
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
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L * 30L);
    }

    public void stop() {
        if (task != null) task.cancel();
        task = null;
    }

    public void reload() {
        nextSpawnAt = System.currentTimeMillis() + configManager.getIntervalMinutes() * 60_000L;
        dataManager.saveNextSpawnAt(nextSpawnAt);
    }

    public long getNextSpawnAt() {
        return nextSpawnAt;
    }

    private void tick() {
        if (!configManager.isEnabled() || !configManager.isSpawnEnabled()) return;
        long now = System.currentTimeMillis();
        if (traderManager.isActive()) {
            if (now >= traderManager.getDespawnAt()) traderManager.despawn(true);
            return;
        }
        if (now >= nextSpawnAt) {
            Location location = findSpawnLocation();
            if (location != null) traderManager.spawn(location);
            nextSpawnAt = System.currentTimeMillis() + configManager.getIntervalMinutes() * 60_000L;
            dataManager.saveNextSpawnAt(nextSpawnAt);
        }
    }

    private Location findSpawnLocation() {
        List<Location> candidates = new ArrayList<>();
        if (configManager.isRandomFromConfigLocations() && !configManager.getLocations().isEmpty()) {
            List<TraderLocation> locations = new ArrayList<>(configManager.getLocations());
            Collections.shuffle(locations);
            for (TraderLocation traderLocation : locations) {
                Location location = traderLocation.toLocation();
                if (isSafe(location)) return location;
            }
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            Location location = randomNear(player);
            if (location != null) candidates.add(location);
        }
        Collections.shuffle(candidates);
        for (Location candidate : candidates) if (isSafe(candidate)) return candidate;
        return null;
    }

    private Location randomNear(Player player) {
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
        return feet.isEmpty() && head.isEmpty();
    }
}
