package id.velioragardens.veliorasuite.util;

import java.util.Locale;

public final class TimeUtil {

    private TimeUtil() {}

    public static String formatSeconds(long seconds) {
        if (seconds <= 0) return "0s";
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        StringBuilder builder = new StringBuilder();
        if (days > 0) builder.append(days).append("d ");
        if (hours > 0) builder.append(hours).append("h ");
        if (minutes > 0) builder.append(minutes).append("m ");
        if (secs > 0 || builder.isEmpty()) builder.append(secs).append("s");
        return builder.toString().trim();
    }

    public static long parseDurationToMillis(String input) {
        if (input == null || input.isBlank() || input.equalsIgnoreCase("0")) return 0L;
        if (input.equalsIgnoreCase("once")) return -1L;
        String value = input.trim().toLowerCase(Locale.ROOT);
        try {
            if (value.endsWith("d")) return Long.parseLong(value.substring(0, value.length() - 1)) * 86400000L;
            if (value.endsWith("h")) return Long.parseLong(value.substring(0, value.length() - 1)) * 3600000L;
            if (value.endsWith("m")) return Long.parseLong(value.substring(0, value.length() - 1)) * 60000L;
            if (value.endsWith("s")) return Long.parseLong(value.substring(0, value.length() - 1)) * 1000L;
            return Long.parseLong(value) * 1000L;
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }
}
