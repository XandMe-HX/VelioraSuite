package id.velioragardens.veliorasuite.module.adventure;

import java.util.Locale;

public enum AdventureQuestType {
    KILL, BREAK, FARM, FISH, BOSS, EXPLORE;

    public static AdventureQuestType parse(String value) {
        if (value == null) return KILL;
        try { return valueOf(value.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ignored) { return KILL; }
    }
}
