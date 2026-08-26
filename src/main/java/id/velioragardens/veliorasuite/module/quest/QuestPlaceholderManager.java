package id.velioragardens.veliorasuite.module.quest;

import id.velioragardens.veliorasuite.module.quest.model.PlayerCategoryProgress;
import id.velioragardens.veliorasuite.module.quest.model.PlayerQuestData;
import id.velioragardens.veliorasuite.module.quest.model.QuestCategory;
import id.velioragardens.veliorasuite.module.quest.model.QuestState;
import org.bukkit.OfflinePlayer;

public final class QuestPlaceholderManager {

    private final QuestDataManager dataManager;

    public QuestPlaceholderManager(QuestDataManager dataManager) {
        this.dataManager = dataManager;
    }

    public String getPlaceholder(OfflinePlayer player, String identifier) {
        if (player == null || identifier == null) return "";
        PlayerQuestData data = dataManager.getOrCreate(player);
        String lower = identifier.toLowerCase();
        if (lower.equals("quest_active")) return activeQuest(data);
        if (lower.equals("quest_progress")) return activeProgress(data);
        if (lower.startsWith("quest_level_")) {
            QuestCategory category = QuestCategory.fromKey(lower.substring("quest_level_".length()));
            PlayerCategoryProgress progress = category == null ? null : data.getCategoryProgress(category);
            return progress == null ? "0" : String.valueOf(progress.getLevel());
        }
        if (lower.equals("quest_total_level")) return String.valueOf(data.getCategories().values().stream().mapToInt(PlayerCategoryProgress::getLevel).sum());
        if (lower.equals("level")) return String.valueOf(data.getCategories().values().stream().mapToInt(PlayerCategoryProgress::getLevel).sum());
        if (lower.startsWith("skill_level_")) return level(data, lower.substring("skill_level_".length()));
        if (lower.startsWith("skill_xp_")) return xp(data, lower.substring("skill_xp_".length()));
        return "";
    }

    private String level(PlayerQuestData data, String key) { QuestCategory c = QuestCategory.fromKey(key); PlayerCategoryProgress p = c == null ? null : data.getCategoryProgress(c); return p == null ? "0" : String.valueOf(p.getLevel()); }
    private String xp(PlayerQuestData data, String key) { QuestCategory c = QuestCategory.fromKey(key); PlayerCategoryProgress p = c == null ? null : data.getCategoryProgress(c); return p == null ? "0" : String.valueOf(p.getExperience()); }

    private String activeQuest(PlayerQuestData data) {
        for (PlayerCategoryProgress progress : data.getCategories().values()) {
            if (progress.getState() == QuestState.ACTIVE || progress.getState() == QuestState.READY_TO_CLAIM) return progress.getCategory().key();
        }
        return "none";
    }

    private String activeProgress(PlayerQuestData data) {
        for (PlayerCategoryProgress progress : data.getCategories().values()) {
            if (progress.getState() == QuestState.ACTIVE || progress.getState() == QuestState.READY_TO_CLAIM) {
                return progress.getCurrentProgress() + "/" + progress.getCurrentTarget();
            }
        }
        return "0/0";
    }
}
