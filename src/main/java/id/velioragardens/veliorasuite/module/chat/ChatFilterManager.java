package id.velioragardens.veliorasuite.module.chat;

import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class ChatFilterManager {

    private final ChatConfigManager configManager;
    private final Map<UUID, String> lastMessages = new HashMap<>();
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

        for (String blocked : configManager.getBlockedWords()) {
            if (blocked == null || blocked.isBlank()) continue;
            if (!result.toLowerCase(Locale.ROOT).contains(blocked.toLowerCase(Locale.ROOT))) continue;

            if (configManager.getWordFilterAction().equals("CANCEL")) {
                return new WordFilterResult(result, true);
            }

            result = result.replaceAll("(?i)" + java.util.regex.Pattern.quote(blocked), configManager.getReplacement());
            changed = true;
        }

        return new WordFilterResult(result, false, changed);
    }

    public record FilterResult(boolean cancelled, String message, String messageKey, boolean changed) {
        public static FilterResult allowed(String message) { return new FilterResult(false, message, "", false); }
        public static FilterResult changed(String message, String key) { return new FilterResult(false, message, key, true); }
        public static FilterResult cancel(String key) { return new FilterResult(true, "", key, false); }
    }

    private record WordFilterResult(String message, boolean cancelled, boolean changed) {
        private WordFilterResult(String message, boolean cancelled) {
            this(message, cancelled, false);
        }
    }
}
