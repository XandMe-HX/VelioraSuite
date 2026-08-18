package id.velioragardens.veliorasuite.module.adventure;

import java.util.Locale;

public enum AdventureRank {
    F, E, D, C, B, A, S, SS, SSS;

    public static AdventureRank parse(String value) {
        if (value == null) return F;
        try { return valueOf(value.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ignored) { return F; }
    }
}
