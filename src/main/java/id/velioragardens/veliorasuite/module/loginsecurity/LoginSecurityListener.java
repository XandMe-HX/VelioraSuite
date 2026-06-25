package id.velioragardens.veliorasuite.module.loginsecurity;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;

public final class LoginSecurityListener implements Listener {

    private final LoginSecurityManager manager;

    public LoginSecurityListener(LoginSecurityManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        manager.handleJoin(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        manager.handleQuit(event.getPlayer());
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (manager.isAuthenticated(player) || !manager.getConfigManager().isBlockMovementBeforeLogin()) return;
        if (event.getTo() == null) return;

        Location authLocation = manager.getSessionManager().getAuthLocation(player);
        if (authLocation == null) {
            manager.getSessionManager().setAuthLocation(player, event.getFrom());
            return;
        }

        if (authLocation.getWorld() == null || event.getTo().getWorld() == null || !authLocation.getWorld().equals(event.getTo().getWorld())) {
            event.setTo(authLocation);
            return;
        }

        double allowed = manager.getConfigManager().getAllowedMoveDistanceBeforeLogin();
        if (authLocation.distanceSquared(event.getTo()) > allowed * allowed) {
            Location target = authLocation.clone();
            target.setYaw(event.getTo().getYaw());
            target.setPitch(event.getTo().getPitch());
            event.setTo(target);
        }
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (manager.isAuthenticated(player) || !manager.getConfigManager().isBlockChatBeforeLogin()) return;
        event.setCancelled(true);
        player.sendMessage(manager.getConfigManager().color(manager.getConfigManager().getMessage("auth-required-chat", "%prefix% &cLogin/register dulu sebelum chat.")));
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (manager.isAuthenticated(player)) return;
        if (manager.getConfigManager().isAllowedBeforeLogin(event.getMessage())) return;

        event.setCancelled(true);
        player.sendMessage(manager.getConfigManager().color(manager.getConfigManager().getMessage("auth-required-command", "%prefix% &cLogin/register dulu sebelum memakai command.")));
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.PHYSICAL) return;
        if (manager.isAuthenticated(event.getPlayer()) || !manager.getConfigManager().isBlockActionsBeforeLogin()) return;
        event.setCancelled(true);
        event.getPlayer().sendMessage(manager.getConfigManager().color(manager.getConfigManager().getMessage("auth-required-action", "%prefix% &cLogin/register dulu sebelum melakukan aksi.")));
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (manager.isAuthenticated(event.getPlayer()) || !manager.getConfigManager().isBlockActionsBeforeLogin()) return;
        event.setCancelled(true);
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (manager.isAuthenticated(player) || !manager.getConfigManager().isBlockActionsBeforeLogin()) return;
        event.setCancelled(true);
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (manager.isAuthenticated(player) || !manager.getConfigManager().isBlockActionsBeforeLogin()) return;
        event.setCancelled(true);
    }

    @EventHandler
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (manager.isAuthenticated(player) || !manager.getConfigManager().isBlockActionsBeforeLogin()) return;
        event.setCancelled(true);
    }
}
