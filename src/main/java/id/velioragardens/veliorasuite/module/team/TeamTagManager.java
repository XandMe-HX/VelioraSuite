package id.velioragardens.veliorasuite.module.team;

import id.velioragardens.veliorasuite.module.team.model.Team;

import java.util.Map;
import java.util.UUID;

public final class TeamTagManager {

    private final TeamConfigManager configManager;
    private final TeamDataManager dataManager;

    public TeamTagManager(TeamConfigManager configManager, TeamDataManager dataManager) {
        this.configManager = configManager;
        this.dataManager = dataManager;
    }

    public boolean isEnabled() {
        return configManager.isChatTagEnabled();
    }

    public String getTeamName(UUID playerId) {
        Team team = dataManager.getTeamByPlayer(playerId);
        if (team == null) {
            return "";
        }

        return team.getDisplayName();
    }

    public String getRawTagPrefix(UUID playerId) {
        String teamName = getTeamName(playerId);
        if (teamName.isBlank()) {
            return "";
        }

        int maxLength = configManager.getMaxTeamNameLengthInChat();
        if (teamName.length() > maxLength) {
            teamName = teamName.substring(0, maxLength);
        }

        return apply(configManager.getChatTagFormat(), Map.of("%team%", teamName));
    }

    public String getTagPrefix(UUID playerId) {
        return configManager.color(getRawTagPrefix(playerId));
    }

    private String apply(String text, Map<String, String> placeholders) {
        String result = text == null ? "" : text;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
    }
}
