package id.velioragardens.veliorasuite.module.announcement;

import id.velioragardens.veliorasuite.VelioraSuite;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

public final class AnnouncementTask {

    private final VelioraSuite plugin;
    private final AnnouncementManager announcementManager;
    private BukkitTask task;

    public AnnouncementTask(VelioraSuite plugin, AnnouncementManager announcementManager) {
        this.plugin = plugin;
        this.announcementManager = announcementManager;
    }

    public void start(long initialDelayTicks, long intervalTicks) {
        stop();
        task = Bukkit.getScheduler().runTaskTimer(plugin, announcementManager::sendNext, initialDelayTicks, intervalTicks);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public boolean isRunning() {
        return task != null && !task.isCancelled();
    }
}
