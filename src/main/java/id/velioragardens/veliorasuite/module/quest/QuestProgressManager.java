package id.velioragardens.veliorasuite.module.quest;

import id.velioragardens.veliorasuite.module.quest.model.PlayerCategoryProgress;
import id.velioragardens.veliorasuite.module.quest.model.PlayerQuestData;
import id.velioragardens.veliorasuite.module.quest.model.QuestCategory;
import id.velioragardens.veliorasuite.module.quest.model.QuestState;
import org.bukkit.entity.Player;

import java.util.Map;

public final class QuestProgressManager {

    private final QuestConfigManager configManager;
    private final QuestDataManager dataManager;

    public QuestProgressManager(QuestConfigManager configManager, QuestDataManager dataManager) {
        this.configManager = configManager;
        this.dataManager = dataManager;
    }

    public boolean addProgress(Player player, QuestCategory category, int amount) {
        if (player == null || category == null || amount <= 0 || !configManager.isCategoryEnabled(category)) return false;
        PlayerQuestData data = dataManager.getOrCreate(player);
        PlayerCategoryProgress progress = data.getCategoryProgress(category);
        if (progress == null) return false;
        if (progress.getState() == QuestState.READY_TO_CLAIM) return true;

        if (progress.getState() != QuestState.ACTIVE) {
            if (!configManager.isAutoStartOnProgress()) return false;
            progress.setState(QuestState.ACTIVE);
            progress.setCurrentProgress(0);
            progress.setCurrentTarget(configManager.calculateTarget(category, progress.getLevel()));
            progress.setCurrentRewardMoney(configManager.calculateRewardMoney(progress.getLevel()));
        }

        progress.setCurrentProgress(progress.getCurrentProgress() + amount);
        if (progress.getCurrentProgress() >= progress.getCurrentTarget()) {
            progress.setCurrentProgress(progress.getCurrentTarget());
            progress.setState(QuestState.READY_TO_CLAIM);
            dataManager.save(data);
            return true;
        }
        dataManager.save(data);
        return false;
    }

    public String progressSummary(Player player) {
        PlayerQuestData data = dataManager.getOrCreate(player);
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<QuestCategory, PlayerCategoryProgress> entry : data.getCategories().entrySet()) {
            PlayerCategoryProgress progress = entry.getValue();
            if (progress.getState() == QuestState.ACTIVE || progress.getState() == QuestState.READY_TO_CLAIM) {
                builder.append(entry.getKey().key()).append(": ")
                        .append(progress.getCurrentProgress()).append("/")
                        .append(progress.getCurrentTarget()).append(" ")
                        .append(progress.getState().name()).append("\n");
            }
        }
        return builder.isEmpty() ? "Tidak ada quest aktif." : builder.toString().trim();
    }
}
