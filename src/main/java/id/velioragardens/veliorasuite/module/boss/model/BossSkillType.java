package id.velioragardens.veliorasuite.module.boss.model;

import java.util.Locale;

public enum BossSkillType {
    GROUND_SLAM,
    SUMMON_MINIONS,
    FIRE_BOMB,
    PULL_AURA,
    POISON_CLOUD,
    LIGHTNING_CHAIN,
    FEAR_PULSE,
    HEAL_PULSE,
    SOUL_CAGE,
    RAGE_MODE;

    public static BossSkillType from(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            return null;
        }
    }
}
