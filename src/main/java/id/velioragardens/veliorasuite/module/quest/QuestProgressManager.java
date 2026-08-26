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

    /**
     * Adds XP to a skill only. Daily quest state is deliberately not touched here:
     * normal gameplay should feel like AuraSkills, not silently start a job.
     */
    public boolean addSkillExperience(Player player, QuestCategory category, int amount) {
        if (player == null || category == null || amount <= 0 || !configManager.isCategoryEnabled(category) || !configManager.canUseSkill(player, category)) return false;
        PlayerQuestData data = dataManager.getOrCreate(player);
        PlayerCategoryProgress progress = data.getCategoryProgress(category);
        if (progress == null) return false;

        int xp = (int) Math.max(1L, Math.round(configManager.sourceXp(category, amount) * configManager.permissionMultiplier(player, category)));
        long totalXp = progress.getExperience() + xp;
        boolean levelUp = false;
        while (progress.getLevel() < configManager.getMaxLevel()) {
            long need = configManager.xpRequired(category, progress.getLevel());
            if (totalXp < need) break;
            totalXp -= need;
            progress.setLevel(progress.getLevel() + 1);
            levelUp = true;
        }
        progress.setExperience(totalXp);
        dataManager.save(data);
        return levelUp;
    }

    /** Adds only progress to a daily quest that was explicitly started. */
    public boolean addQuestProgress(Player player, QuestCategory category, int amount) {
        if (player == null || category == null || amount <= 0 || !configManager.isCategoryEnabled(category) || !configManager.canUseSkill(player, category)) return false;
        PlayerQuestData data = dataManager.getOrCreate(player);
        PlayerCategoryProgress progress = data.getCategoryProgress(category);
        if (progress == null || progress.getState() != QuestState.ACTIVE) return false;
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
