package id.velioragardens.veliorasuite.module.fishing.model;

import java.util.Locale;

public enum FishRarity {
    TRASH(0, "Trash", "&8"),
    VANILLA(1, "Vanilla", "&7"),
    COMMON(2, "Common", "&f"),
    ORNAMENTAL(3, "Ornamental", "&d"),
    RARE(4, "Rare", "&9"),
    LEGENDARY(5, "Legendary", "&6"),
    MITOLOGI(6, "Mitologi", "&c");

    private final int power;
    private final String displayName;
    private final String color;

    FishRarity(int power, String displayName, String color) {
        this.power = power;
        this.displayName = displayName;
        this.color = color;
    }

    public int power() { return power; }
    public String displayName() { return displayName; }
    public String color() { return color; }
    public String key() { return name().toLowerCase(Locale.ROOT); }

    public static FishRarity fromKey(String input) {
        if (input == null) return COMMON;
        try { return FishRarity.valueOf(input.trim().toUpperCase(Locale.ROOT)); } catch (Exception ignored) { return COMMON; }
    }
}
