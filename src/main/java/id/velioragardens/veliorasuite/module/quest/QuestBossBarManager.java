package id.velioragardens.veliorasuite.module.quest;

import id.velioragardens.veliorasuite.module.quest.model.PlayerCategoryProgress;
import id.velioragardens.veliorasuite.module.quest.model.QuestCategory;
import org.bukkit.Bukkit;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class QuestBossBarManager {

    private final QuestConfigManager configManager;
    private final Map<UUID, BossBar> bossBars = new HashMap<>();
    private final Map<UUID, BukkitTask> hideTasks = new HashMap<>();

    public QuestBossBarManager(QuestConfigManager configManager) {
        this.configManager = configManager;
    }

    public void showOrUpdate(Player player, QuestCategory category, PlayerCategoryProgress progress) {
        if (player == null || category == null || progress == null || !configManager.isBossBarEnabled()) return;
        BossBar bossBar = bossBars.computeIfAbsent(player.getUniqueId(), uuid -> Bukkit.createBossBar("", configManager.getBossBarColor(), configManager.getBossBarStyle()));
        bossBar.setColor(configManager.getBossBarColor());
        bossBar.setStyle(configManager.getBossBarStyle());
        bossBar.setTitle(configManager.color(title(category, progress)));
        bossBar.setProgress(progressPercent(progress));
        if (!bossBar.getPlayers().contains(player)) bossBar.addPlayer(player);
        bossBar.setVisible(true);
        scheduleAutoHide(player);
    }

    public void hide(Player player) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();
        BukkitTask task = hideTasks.remove(uuid);
        if (task != null) task.cancel();
        BossBar bossBar = bossBars.remove(uuid);
        if (bossBar != null) {
            bossBar.removePlayer(player);
            bossBar.removeAll();
            bossBar.setVisible(false);
        }
    }

    public void hideAll() {
        for (BukkitTask task : hideTasks.values()) task.cancel();
        hideTasks.clear();
        for (BossBar bossBar : bossBars.values()) {
            bossBar.removeAll();
            bossBar.setVisible(false);
        }
        bossBars.clear();
    }

    private void scheduleAutoHide(Player player) {
        int seconds = configManager.getBossBarAutoHideSeconds();
        if (seconds <= 0) return;
        UUID uuid = player.getUniqueId();
        BukkitTask oldTask = hideTasks.remove(uuid);
        if (oldTask != null) oldTask.cancel();
        Plugin plugin = Bukkit.getPluginManager().getPlugin("VelioraSuite");
        if (plugin == null || !plugin.isEnabled()) return;
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) hide(player);
        }, seconds * 20L);
        hideTasks.put(uuid, task);
    }

    private String title(QuestCategory category, PlayerCategoryProgress progress) {
        int percent = percent(progress);
        return configManager.getBossBarTitle()
                .replace("%quest%", configManager.getCategoryDisplayName(category))
                .replace("%progress%", String.valueOf(progress.getCurrentProgress()))
                .replace("%target%", String.valueOf(progress.getCurrentTarget()))
                .replace("%percent%", String.valueOf(percent));
    }

    private double progressPercent(PlayerCategoryProgress progress) {
        if (progress.getCurrentTarget() <= 0) return 0.0D;
        double value = progress.getCurrentProgress() / (double) progress.getCurrentTarget();
        return Math.max(0.0D, Math.min(1.0D, value));
    }

    private int percent(PlayerCategoryProgress progress) {
        return (int) Math.round(progressPercent(progress) * 100.0D);
    }
}
