package id.velioragardens.veliorasuite.module.team;

import id.velioragardens.veliorasuite.module.team.model.Team;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;

public final class TeamChatManager {

    private final TeamConfigManager configManager;

    public TeamChatManager(TeamConfigManager configManager) {
        this.configManager = configManager;
    }

    public void sendTeamChat(Player sender, Team team, String message) {
        if (sender == null || team == null || message == null || message.isBlank()) {
            return;
        }

        String format = configManager.getFormat("team-chat", "&8[&bTeam Chat&8] &f%player%&7: &f%message%");
        String output = apply(format, Map.of(
                "%team%", team.getDisplayName(),
                "%player%", sender.getName(),
                "%message%", message
        ));

        for (UUID uuid : team.getMembers().keySet()) {
            Player member = Bukkit.getPlayer(uuid);
            if (member != null && member.isOnline()) {
                member.sendMessage(configManager.color(output));
            }
        }
    }

    private String apply(String text, Map<String, String> placeholders) {
        String result = text;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
    }
}
