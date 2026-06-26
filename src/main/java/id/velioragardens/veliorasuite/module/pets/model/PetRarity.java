package id.velioragardens.veliorasuite.module.pets.model;

import org.bukkit.ChatColor;

import java.util.Locale;

public enum PetRarity {
    COMMON(ChatColor.WHITE),
    RARE(ChatColor.AQUA),
    EPIC(ChatColor.LIGHT_PURPLE),
    LEGENDARY(ChatColor.GOLD),
    MYTHIC(ChatColor.RED);

    private final ChatColor color;

    PetRarity(ChatColor color) {
        this.color = color;
    }

    public ChatColor color() { return color; }

    public static PetRarity from(String raw) {
        if (raw == null || raw.isBlank()) return COMMON;
        try { return valueOf(raw.trim().toUpperCase(Locale.ROOT)); } catch (Exception ignored) { return COMMON; }
    }
}
