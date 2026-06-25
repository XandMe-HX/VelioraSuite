package id.velioragardens.veliorasuite.module.clearlag;

import id.velioragardens.veliorasuite.VelioraSuite;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.Set;

public final class ClearLagTaskManager {

    private final VelioraSuite plugin;
    private final ClearLagManager manager;
    private final ClearLagConfigManager configManager;
    private BukkitTask task;
    private int remainingSeconds;

    public ClearLagTaskManager(VelioraSuite plugin, ClearLagManager manager, ClearLagConfigManager configManager) {
        this.plugin = plugin;
        this.manager = manager;
        this.configManager = configManager;
    }

    public void start() {
        stop();
        if (!configManager.isEnabled() || !configManager.isAutoClearEnabled()) return;
        remainingSeconds = configManager.getAutoClearIntervalSeconds();
        Set<Integer> warningSet = new HashSet<>(configManager.getWarningSeconds());
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> tick(warningSet), 20L, 20L);
    }

    public void restart() {
        start();
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public int getRemainingSeconds() {
        return Math.max(0, remainingSeconds);
    }

    private void tick(Set<Integer> warningSet) {
        if (!configManager.isEnabled() || !configManager.isAutoClearEnabled()) {
            stop();
            return;
        }

        if (warningSet.contains(remainingSeconds)) {
            manager.broadcastWarning(remainingSeconds);
        }

        if (remainingSeconds <= 0) {
            manager.clearItems(true);
            if (configManager.isProjectileAutoClearEnabled()) {
                manager.clearProjectiles(plugin.getServer().getConsoleSender());
            }
            remainingSeconds = configManager.getAutoClearIntervalSeconds();
            return;
        }

        remainingSeconds--;
    }
}
