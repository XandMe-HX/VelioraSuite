package id.velioragardens.veliorasuite.module.quest.model;

import java.util.Locale;

public enum QuestCategory {
    WOODCUTTING("woodcutting"),
    MINING("mining"),
    FARMER("farmer"),
    CHEF("chef"),
    MONSTER_HUNTER("monster_hunter"),
    ANIMAL_HUNTER("animal_hunter"),
    FISHING("fishing"),
    AGILITY("agility"),
    ALCHEMY("alchemy"),
    ARCHERY("archery"),
    EXCAVATION("excavation"),
    FIGHTING("fighting"),
    DEFENSE("defense"),
    ENCHANTING("enchanting");

    private final String key;

    QuestCategory(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static QuestCategory fromKey(String input) {
        if (input == null) return null;
        String normalized = input.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        for (QuestCategory category : values()) {
            if (category.key.equals(normalized)) return category;
        }
        return null;
    }
}
