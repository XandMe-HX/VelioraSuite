package id.velioragardens.veliorasuite.module.chat;

import me.clip.placeholderapi.PlaceholderAPI;
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
            try {
                result = PlaceholderAPI.setPlaceholders(player, result);
            } catch (RuntimeException ignored) {
                // PlaceholderAPI is optional. If a placeholder fails, keep the safe internal format.
            }
        }

        return configManager.color(result);
    }

    private String apply(String text, Map<String, String> placeholders) {
        String result = text == null ? "" : text;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue() == null ? "" : entry.getValue());
        }
        return result;
    }
}
