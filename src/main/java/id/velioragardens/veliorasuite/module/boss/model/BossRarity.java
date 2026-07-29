package id.velioragardens.veliorasuite.module.boss.model;

import org.bukkit.boss.BarColor;

import java.util.Locale;

public enum BossRarity {
    COMMON("Common", BarColor.GREEN, 1),
    RARE("Rare", BarColor.BLUE, 2),
    EPIC("Epic", BarColor.PURPLE, 3),
    LEGENDARY("Legendary", BarColor.YELLOW, 4),
    MYTHIC("Mythic", BarColor.RED, 5);

    private final String displayName;
    private final BarColor barColor;
    private final int power;

    BossRarity(String displayName, BarColor barColor, int power) {
        this.displayName = displayName;
        this.barColor = barColor;
        this.power = power;
    }

    public String displayName() { return displayName; }
    public BarColor barColor() { return barColor; }
    public int power() { return power; }

    public static BossRarity from(String value) {
        if (value == null || value.isBlank()) return COMMON;
        try { return valueOf(value.trim().toUpperCase(Locale.ROOT)); } catch (Exception ignored) { return COMMON; }
    }
}
