package id.velioragardens.veliorasuite.module.team;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public final class TeamChatListener implements Listener {

    private final TeamManager teamManager;

    public TeamChatListener(TeamManager teamManager) {
        this.teamManager = teamManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        String prefix = teamManager.getTeamPrefix(event.getPlayer().getUniqueId());
        if (prefix == null || prefix.isBlank()) {
            return;
        }

        event.setFormat(prefix + event.getFormat());
    }
}
