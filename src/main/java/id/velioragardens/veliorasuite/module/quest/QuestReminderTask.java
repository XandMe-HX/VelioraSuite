package id.velioragardens.veliorasuite.module.quest;

import id.velioragardens.veliorasuite.VelioraSuite;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

public final class QuestReminderTask {

    private final VelioraSuite plugin;
    private final QuestManager manager;
    private BukkitTask task;

    public QuestReminderTask(VelioraSuite plugin, QuestManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    public void start() {
        stop();
        if (!manager.getConfigManager().isEnabled() || !manager.getConfigManager().isStarterReminderEnabled()) return;
        long intervalTicks = 20L * Math.max(60L, manager.getConfigManager().getStarterReminderIntervalSeconds());
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, intervalTicks, intervalTicks);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        if (!manager.getConfigManager().isStarterReminderEnabled()) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            manager.getStarterManager().sendReminderIfNeeded(player);
        }
    }
}
