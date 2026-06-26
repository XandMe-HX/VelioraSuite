package id.velioragardens.veliorasuite.module.security;

import id.velioragardens.veliorasuite.module.security.model.SecurityDecision;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.bukkit.event.player.PlayerJoinEvent;

public final class SecurityListener implements Listener {

    private final SecurityManager manager;

    public SecurityListener(SecurityManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onJoin(PlayerJoinEvent event) {
        SecurityDecision decision = manager.checkJoin(event.getPlayer());
        if (decision.blocked()) {
            event.getPlayer().kickPlayer(manager.denyMessage(decision));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        SecurityDecision decision = manager.checkCommand(event.getPlayer(), event.getMessage());
        if (decision.blocked()) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(manager.denyMessage(decision));
        }
    }

    @EventHandler
    public void onCommandSend(PlayerCommandSendEvent event) {
        manager.getTabProtectionManager().filter(event.getPlayer(), event.getCommands());
    }
}
