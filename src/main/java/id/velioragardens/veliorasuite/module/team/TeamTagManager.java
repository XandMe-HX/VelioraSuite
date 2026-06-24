package id.velioragardens.veliorasuite.module.team;

import id.velioragardens.veliorasuite.module.team.model.Team;

import java.util.Map;

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

    public String getTagPrefix(java.util.UUID playerId) {
        Team team = dataManager.getTeamByPlayer(playerId);
        if (team == null) {
            return "";
        }

        String teamName = team.getDisplayName();
        int maxLength = configManager.getMaxTeamNameLengthInChat();
        if (teamName.length() > maxLength) {
            teamName = teamName.substring(0, maxLength);
        }

        return configManager.color(apply(configManager.getChatTagFormat(), Map.of("%team%", teamName)));
    }

    private String apply(String text, Map<String, String> placeholders) {
        String result = text;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
    }
}
