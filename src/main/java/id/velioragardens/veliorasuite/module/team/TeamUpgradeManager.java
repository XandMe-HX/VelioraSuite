package id.velioragardens.veliorasuite.module.team;

import id.velioragardens.veliorasuite.module.team.model.Team;

public final class TeamUpgradeManager {

    private final TeamConfigManager configManager;

    public TeamUpgradeManager(TeamConfigManager configManager) {
        this.configManager = configManager;
    }

    public boolean canUpgrade(Team team) {
        return configManager.isUpgradeEnabled()
                && team != null
                && !team.isUpgraded()
                && team.getMaxMembers() < configManager.getMaxMembers();
    }

    public int getNextMaxMembers(Team team) {
        if (team == null) {
            return configManager.getDefaultMaxMembers();
        }

        return Math.min(configManager.getMaxMembers(), team.getMaxMembers() + configManager.getUpgradeAddMembers());
    }
}
