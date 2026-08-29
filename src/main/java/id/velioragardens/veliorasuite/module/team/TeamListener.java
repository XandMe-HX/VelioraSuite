package id.velioragardens.veliorasuite.module.team;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.entity.Player;

public final class TeamListener implements Listener {

    private final TeamManager teamManager;

    public TeamListener(TeamManager teamManager) {
        this.teamManager = teamManager;
    }

    @EventHandler
    public void onAsyncPlayerChat(AsyncPlayerChatEvent event) {
        if (teamManager.isTeamChatMode(event.getPlayer())) {
            event.setCancelled(true);
            teamManager.teamChat(event.getPlayer(), event.getMessage());
            return;
        }
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

    @EventHandler(ignoreCancelled = true)
    public void onTeamDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker) || !(event.getEntity() instanceof Player victim)) return;
        if (teamManager.isFriendlyFireBlocked(attacker, victim)) {
            event.setCancelled(true);
            teamManager.sendFriendlyFireBlocked(attacker);
        }
    }
}
