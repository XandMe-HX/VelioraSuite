package id.velioragardens.veliorasuite.module.chat;

import org.bukkit.entity.Player;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class ChatFilterManager {

    private final ChatConfigManager configManager;
    private final Map<UUID, String> lastMessages = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> repeatCounts = new HashMap<>();

    public ChatFilterManager(ChatConfigManager configManager) {
        this.configManager = configManager;
    }

    public FilterResult filter(Player player, String message) {
        String result = message == null ? "" : message;

        if (configManager.isAntiRepeatEnabled() && isRepeated(player.getUniqueId(), result)) {
            return FilterResult.cancel("repeat-blocked");
        }

        if (configManager.isAntiCapsEnabled() && shouldHandleCaps(result)) {
            if (configManager.getCapsAction().equals("CANCEL")) {
                return FilterResult.cancel("caps-blocked");
            }
            result = result.toLowerCase(Locale.ROOT);
            return FilterResult.changed(result, "caps-fixed");
        }

        if (configManager.isWordFilterEnabled()) {
            WordFilterResult wordResult = applyWordFilter(result);
            if (wordResult.cancelled()) {
                return FilterResult.cancel("word-blocked");
            }
            result = wordResult.message();
            if (wordResult.changed()) {
                return FilterResult.changed(result, "");
            }
        }

        return FilterResult.allowed(result);
    }

    public void clear() {
        lastMessages.clear();
        repeatCounts.clear();
    }

    private boolean isRepeated(UUID uuid, String message) {
        String normalized = message.trim().toLowerCase(Locale.ROOT);
        String last = lastMessages.get(uuid);

        if (normalized.equals(last)) {
            int count = repeatCounts.getOrDefault(uuid, 1) + 1;
            repeatCounts.put(uuid, count);
            return count > configManager.getMaxRepeat();
        }

        lastMessages.put(uuid, normalized);
        repeatCounts.put(uuid, 1);
        return false;
    }

    private boolean shouldHandleCaps(String message) {
        String plain = message.replaceAll("[^A-Za-z]", "");
        if (plain.length() < configManager.getCapsMinLength()) {
            return false;
        }

        long uppercase = plain.chars().filter(Character::isUpperCase).count();
        int percent = (int) ((uppercase * 100) / plain.length());
        return percent >= configManager.getMaxCapsPercent();
    }

    private WordFilterResult applyWordFilter(String message) {
        String result = message;
        boolean changed = false;
        String lowerOriginal = message.toLowerCase(Locale.ROOT);
        String normalizedI = normalize(message, false);
        String normalizedL = normalize(message, true);

        for (String pattern : configManager.getBlockedPatterns()) {
            if (pattern == null || pattern.isBlank()) continue;
            try {
                if (Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(message).find()) {
                    if (configManager.getWordFilterAction().equals("CANCEL")) {
                        return new WordFilterResult(result, true, false);
                    }
                    return new WordFilterResult(configManager.getReplacement(), false, true);
                }
            } catch (PatternSyntaxException ignored) {
                // Invalid custom regex should not break chat.
            }
        }

        for (String blocked : configManager.getBlockedWords()) {
            if (blocked == null || blocked.isBlank()) continue;

            String cleanBlocked = blocked.toLowerCase(Locale.ROOT).trim();
            if (cleanBlocked.isBlank()) continue;

            boolean exactDetected = lowerOriginal.contains(cleanBlocked);
            boolean normalizedDetected = false;

            if (configManager.isCheckNormalizedEnabled()) {
                String normalizedBlockedI = normalize(cleanBlocked, false);
                String normalizedBlockedL = normalize(cleanBlocked, true);
                normalizedDetected = normalizedI.contains(normalizedBlockedI) || normalizedL.contains(normalizedBlockedL);
            }

            if (!exactDetected && !normalizedDetected) continue;

            if (configManager.getWordFilterAction().equals("CANCEL")) {
                return new WordFilterResult(result, true, false);
            }

            if (exactDetected) {
                result = result.replaceAll("(?i)" + Pattern.quote(cleanBlocked), configManager.getReplacement());
                lowerOriginal = result.toLowerCase(Locale.ROOT);
                normalizedI = normalize(result, false);
                normalizedL = normalize(result, true);
                changed = true;
            } else {
                return new WordFilterResult(configManager.getReplacement(), false, true);
            }
        }

        return new WordFilterResult(result, false, changed);
    }

    private String normalize(String input, boolean oneAsL) {
        if (input == null) return "";

        StringBuilder builder = new StringBuilder();
        char previous = 0;
        int repeatCount = 0;

        for (char raw : input.toLowerCase(Locale.ROOT).toCharArray()) {
            char mapped = mapLeet(raw, oneAsL);
            if (!Character.isLetterOrDigit(mapped)) {
                if (configManager.isBlockSeparatedLettersEnabled()) continue;
                mapped = raw;
            }

            if (configManager.isReduceRepeatedLettersEnabled() && mapped == previous) {
                repeatCount++;
                if (repeatCount > 1) continue;
            } else {
                repeatCount = 1;
                previous = mapped;
            }

            builder.append(mapped);
        }

        return builder.toString();
    }

    private char mapLeet(char input, boolean oneAsL) {
        return switch (input) {
            case '0' -> 'o';
            case '1' -> oneAsL ? 'l' : 'i';
            case '3' -> 'e';
            case '4', '@' -> 'a';
            case '5', '$' -> 's';
            case '7' -> 't';
            default -> input;
        };
    }

    public record FilterResult(boolean cancelled, String message, String messageKey, boolean changed) {
        public static FilterResult allowed(String message) { return new FilterResult(false, message, "", false); }
        public static FilterResult changed(String message, String key) { return new FilterResult(false, message, key, true); }
        public static FilterResult cancel(String key) { return new FilterResult(true, "", key, false); }
    }

    private record WordFilterResult(String message, boolean cancelled, boolean changed) {
    }
}
