package id.velioragardens.veliorasuite.module.quest;

import id.velioragardens.veliorasuite.module.quest.model.PlayerCategoryProgress;
import id.velioragardens.veliorasuite.module.quest.model.QuestCategory;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class QuestBossBarManager {

    private final QuestConfigManager configManager;
    private final Map<UUID, BossBar> bossBars = new HashMap<>();

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
    }

    public void hide(Player player) {
        if (player == null) return;
        BossBar bossBar = bossBars.remove(player.getUniqueId());
        if (bossBar != null) {
            bossBar.removePlayer(player);
            bossBar.removeAll();
            bossBar.setVisible(false);
        }
    }

    public void hideAll() {
        for (BossBar bossBar : bossBars.values()) {
            bossBar.removeAll();
            bossBar.setVisible(false);
        }
        bossBars.clear();
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
