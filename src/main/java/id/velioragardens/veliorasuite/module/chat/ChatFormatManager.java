package id.velioragardens.veliorasuite.module.chat;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ChatFormatManager {

    private static final Pattern PLACEHOLDER_TOKEN = Pattern.compile("%[A-Za-z0-9_.:-]+%");

    private final ChatConfigManager configManager;
    private final ChatPlaceholderManager placeholderManager;

    public ChatFormatManager(ChatConfigManager configManager, ChatPlaceholderManager placeholderManager) {
        this.configManager = configManager;
        this.placeholderManager = placeholderManager;
    }

    public String formatPublicChat(Player player, String message) {
        String format = configManager.getPublicChatFormat();
        String result = apply(format, Map.of(
                "%veliorateam_name%", placeholderManager.getTeamName(player.getUniqueId()),
                "%veliorateam_tag%", placeholderManager.getTeamTag(player.getUniqueId()),
                "%luckperms_prefix%", placeholderManager.getLuckPermsPrefix(player),
                "%player%", player.getName(),
                "%displayname%", player.getDisplayName(),
                "%message%", message
        ));

        if (configManager.isUsePlaceholderApi() && Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            result = applyPlaceholderApi(player, result);
        }

        return configManager.color(result);
    }

    private String applyPlaceholderApi(Player player, String text) {
        if (text == null || text.isEmpty()) return "";
        Matcher matcher = PLACEHOLDER_TOKEN.matcher(text);
        Set<String> tokens = new LinkedHashSet<>();
        while (matcher.find() && tokens.size() < 32) tokens.add(matcher.group());

        String result = text;
        for (String token : tokens) {
            try {
                Class<?> placeholderApi = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
                Object expanded = placeholderApi
                        .getMethod("setPlaceholders", org.bukkit.OfflinePlayer.class, String.class)
                        .invoke(null, player, token);
                if (expanded instanceof String value && !value.equals(token)) {
                    result = result.replace(token, value);
                }
            } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
                // Satu expansion yang rusak tidak boleh memutus prefix, suffix, atau placeholder lain.
            }
        }
        return result;
    }

    private String apply(String text, Map<String, String> placeholders) {
        String result = text == null ? "" : text;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue() == null ? "" : entry.getValue());
        }
        return result;
    }
}
