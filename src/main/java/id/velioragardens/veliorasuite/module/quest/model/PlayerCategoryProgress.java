package id.velioragardens.veliorasuite.module.quest.model;

public final class PlayerCategoryProgress {

    private static final int MAX_LEVEL = 100;

    private final QuestCategory category;
    private int level;
    private int completedCount;
    private QuestState state;
    private int currentTarget;
    private int currentProgress;
    private int currentRewardMoney;
    // XP is separate from daily/automatic quest progress. Keeping it here makes
    // old quest data compatible while allowing AuraSkills-style advancement.
    private long experience;

    public PlayerCategoryProgress(QuestCategory category, int level, int completedCount, QuestState state, int currentTarget, int currentProgress, int currentRewardMoney) {
        this.category = category;
        this.level = Math.max(1, Math.min(MAX_LEVEL, level));
        this.completedCount = completedCount;
        this.state = state;
        this.currentTarget = currentTarget;
        this.currentProgress = currentProgress;
        this.currentRewardMoney = currentRewardMoney;
    }

    public QuestCategory getCategory() { return category; }
    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = Math.max(1, Math.min(MAX_LEVEL, level)); }
    public int getCompletedCount() { return completedCount; }
    public void setCompletedCount(int completedCount) { this.completedCount = Math.max(0, completedCount); }
    public QuestState getState() { return state; }
    public void setState(QuestState state) { this.state = state == null ? QuestState.NOT_STARTED : state; }
    public int getCurrentTarget() { return currentTarget; }
    public void setCurrentTarget(int currentTarget) { this.currentTarget = Math.max(1, currentTarget); }
    public int getCurrentProgress() { return currentProgress; }
    public void setCurrentProgress(int currentProgress) { this.currentProgress = Math.max(0, currentProgress); }
    public int getCurrentRewardMoney() { return currentRewardMoney; }
    public void setCurrentRewardMoney(int currentRewardMoney) { this.currentRewardMoney = Math.max(0, currentRewardMoney); }
    public long getExperience() { return experience; }
    public void setExperience(long experience) { this.experience = Math.max(0L, experience); }
}
