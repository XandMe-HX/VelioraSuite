package id.velioragardens.veliorasuite.module.team;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
public final class TeamPlaceholderExpansion extends PlaceholderExpansion {

    private final TeamManager teamManager;

    public TeamPlaceholderExpansion(TeamManager teamManager) {
        this.teamManager = teamManager;
    }

    @Override
    public String getIdentifier() {
        return "veliorasuite";
    }

    @Override
    public String getAuthor() {
        return "Veliora Gardens";
    }

    @Override
    public String getVersion() {
        return "1.6.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (player == null || player.getUniqueId() == null) {
            return "";
        }

        Team team = teamManager.getTeamByPlayer(player.getUniqueId());
        String key = params.toLowerCase();

        if (key.equals("team") || key.equals("team_name")) {
            return team == null ? "" : team.getName();
        }
        if (key.equals("team_prefix")) {
            return team == null ? "" : teamManager.getTeamPrefix(player.getUniqueId());
        }
        if (key.equals("team_role")) {
            return team == null ? "" : teamManager.getRole(player.getUniqueId(), team);
        }
        if (key.equals("team_members")) {
            return team == null ? "0" : String.valueOf(team.getTotalMembers());
        }
        if (key.equals("team_max_members")) {
            return team == null ? "0" : String.valueOf(team.getMaxMembers());
        }

        return null;
    }
}
