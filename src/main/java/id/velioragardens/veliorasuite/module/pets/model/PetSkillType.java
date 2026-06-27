package id.velioragardens.veliorasuite.module.pets.model;

import java.util.Locale;

public enum PetSkillType {
    NONE,
    PET_DAMAGE,
    FISHING_LUCK,
    QUEST_MONEY;

    public static PetSkillType from(String raw) {
        if (raw == null || raw.isBlank()) return NONE;
        try { return valueOf(raw.trim().toUpperCase(Locale.ROOT).replace('-', '_')); } catch (Exception ignored) { return NONE; }
    }
}
