package id.velioragardens.veliorasuite.module.trader.model;

import java.util.Locale;

public enum TraderPaymentType {
    MONEY,
    FISH;

    public static TraderPaymentType from(String input) {
        if (input == null) return MONEY;
        try { return valueOf(input.trim().toUpperCase(Locale.ROOT)); } catch (Exception ignored) { return MONEY; }
    }
}
