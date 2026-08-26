package id.velioragardens.veliorasuite.module.quest;

import id.velioragardens.veliorasuite.module.quest.model.PlayerCategoryProgress;
import id.velioragardens.veliorasuite.module.quest.model.PlayerQuestData;
import id.velioragardens.veliorasuite.module.quest.model.QuestCategory;
import id.velioragardens.veliorasuite.module.quest.model.QuestState;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

public final class QuestPlaceholderManager {

    private final QuestDataManager dataManager;
    private final QuestConfigManager configManager;

    public QuestPlaceholderManager(QuestDataManager dataManager, QuestConfigManager configManager) {
        this.dataManager = dataManager;
        this.configManager = configManager;
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
        if (lower.equals("quest_total_level") || lower.equals("skill_total_level") || lower.equals("level")) return String.valueOf(data.getCategories().values().stream().mapToInt(PlayerCategoryProgress::getLevel).sum());
        if (lower.equals("skill_max_level")) return String.valueOf(configManager.getMaxLevel());
        if (lower.startsWith("skill_level_")) return level(data, lower.substring("skill_level_".length()));
        if (lower.startsWith("skill_xp_")) return xp(data, lower.substring("skill_xp_".length()));
        if (lower.startsWith("skill_xp_required_")) return xpRequired(data, lower.substring("skill_xp_required_".length()));
        if (lower.startsWith("skill_xp_progress_")) return xpProgress(data, lower.substring("skill_xp_progress_".length()));
        if (lower.startsWith("skill_xp_percent_")) return xpPercent(data, lower.substring("skill_xp_percent_".length()));
        if (lower.startsWith("skill_multiplier_")) return multiplier(player, lower.substring("skill_multiplier_".length()));
        return "";
    }

    private String level(PlayerQuestData data, String key) { QuestCategory c = QuestCategory.fromKey(key); PlayerCategoryProgress p = c == null ? null : data.getCategoryProgress(c); return p == null ? "0" : String.valueOf(p.getLevel()); }
    private String xp(PlayerQuestData data, String key) { QuestCategory c = QuestCategory.fromKey(key); PlayerCategoryProgress p = c == null ? null : data.getCategoryProgress(c); return p == null ? "0" : String.valueOf(p.getExperience()); }
    private String xpRequired(PlayerQuestData data, String key) {
        QuestCategory c = QuestCategory.fromKey(key);
        PlayerCategoryProgress p = c == null ? null : data.getCategoryProgress(c);
        return p == null ? "0" : String.valueOf(configManager.xpRequired(c, p.getLevel()));
    }
    private String xpProgress(PlayerQuestData data, String key) {
        QuestCategory c = QuestCategory.fromKey(key);
        PlayerCategoryProgress p = c == null ? null : data.getCategoryProgress(c);
        return p == null ? "0/0" : p.getExperience() + "/" + configManager.xpRequired(c, p.getLevel());
    }
    private String xpPercent(PlayerQuestData data, String key) {
        QuestCategory c = QuestCategory.fromKey(key);
        PlayerCategoryProgress p = c == null ? null : data.getCategoryProgress(c);
        if (p == null) return "0";
        long needed = Math.max(1L, configManager.xpRequired(c, p.getLevel()));
        return String.valueOf(Math.min(100L, Math.round((p.getExperience() * 100.0D) / needed)));
    }

    private String multiplier(OfflinePlayer offlinePlayer, String key) {
        QuestCategory category = QuestCategory.fromKey(key);
        if (category == null) return "1";
        Player player = offlinePlayer.getPlayer();
        if (player == null) return "1";
        double multiplier = configManager.permissionMultiplier(player, category);
        return String.format(java.util.Locale.US, "%.2f", multiplier)
                .replaceAll("0+$", "")
                .replaceAll("\\.$", "");
    }

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
