package id.velioragardens.veliorasuite.module.team;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public final class TeamListener implements Listener {

    private final TeamManager teamManager;

    public TeamListener(TeamManager teamManager) {
        this.teamManager = teamManager;
    }

    @EventHandler
    public void onAsyncPlayerChat(AsyncPlayerChatEvent event) {
        if (!teamManager.getTagManager().isEnabled()) {
            return;
        }

        String prefix = teamManager.getTagManager().getTagPrefix(event.getPlayer().getUniqueId());
        if (prefix.isBlank()) {
            return;
        }

        try {
            event.setFormat(prefix + event.getFormat());
        } catch (RuntimeException exception) {
            event.getPlayer().getServer().getLogger().warning("VelioraTeam: gagal memasang chat tag. Coba matikan settings.chat-tag.enabled jika bentrok dengan plugin chat lain.");
        }
    }
}
