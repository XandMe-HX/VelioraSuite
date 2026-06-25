package id.velioragardens.veliorasuite.module.quest.model;

public record ActiveQuest(
        QuestCategory category,
        int level,
        int target,
        int progress,
        int rewardMoney,
        QuestState state
) {
}
