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

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        if (!teamManager.isChatPrefixEnabled()) {
            return;
        }

        String prefix = teamManager.getTeamPrefix(event.getPlayer().getUniqueId());
        if (prefix == null || prefix.isBlank()) {
            return;
        }

        String format = event.getFormat();
        if (format == null || format.startsWith(prefix)) {
            return;
        }

        event.setFormat(prefix + format);
    }
}
