package id.velioragardens.veliorasuite.module.chat;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;

public final class ChatFormatManager {

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
        try {
            Class<?> placeholderApi = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            Object result = placeholderApi.getMethod("setPlaceholders", org.bukkit.OfflinePlayer.class, String.class).invoke(null, player, text);
            return result instanceof String value ? value : text;
        } catch (ReflectiveOperationException | LinkageError exception) {
            return text;
        }
    }

    private String apply(String text, Map<String, String> placeholders) {
        String result = text == null ? "" : text;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue() == null ? "" : entry.getValue());
        }
        return result;
    }
}
