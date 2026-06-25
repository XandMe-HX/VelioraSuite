package id.velioragardens.veliorasuite.module.loginsecurity;

import id.velioragardens.veliorasuite.VelioraSuite;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
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

import java.util.Locale;

public final class LoginSecurityListener implements Listener {

    private final LoginSecurityManager manager;

    public LoginSecurityListener(LoginSecurityManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        manager.handleJoin(event.getPlayer());
        LoginSecurityBlindnessManager.sync(event.getPlayer(), manager);
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

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage();

        if (manager.isAuthenticated(player)) {
            if (isAuthStateChangingCommand(message)) {
                syncBlindnessNextTick(player);
            }
            return;
        }

        if (handlePreAuthShortcut(player, message)) {
            event.setCancelled(true);
            LoginSecurityBlindnessManager.sync(player, manager);
            return;
        }

        if (manager.getConfigManager().isAllowedBeforeLogin(message)) {
            syncBlindnessNextTick(player);
            return;
        }

        event.setCancelled(true);
        player.sendMessage(manager.getConfigManager().color(manager.getConfigManager().getMessage("auth-required-command", "%prefix% &cLogin/register dulu sebelum memakai command.")));
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
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

    private boolean handlePreAuthShortcut(Player player, String commandLine) {
        if (!manager.getConfigManager().isPreAuthShortcutsEnabled()) return false;
        String[] args = manager.getConfigManager().getCommandArgs(commandLine);

        if (manager.getConfigManager().isRegisterShortcut(commandLine)) {
            if (args.length < 2) {
                manager.sendUsage(player, "register-usage");
            } else {
                manager.register(player, args[0], args[1]);
            }
            return true;
        }

        if (manager.getConfigManager().isLoginShortcut(commandLine)) {
            if (args.length < 1) {
                manager.sendUsage(player, "login-usage");
            } else {
                manager.login(player, args[0]);
            }
            return true;
        }

        return false;
    }

    private boolean isAuthStateChangingCommand(String commandLine) {
        String command = commandToken(commandLine);
        return command.equals("/logout") || command.equals("/unregister");
    }

    private String commandToken(String commandLine) {
        if (commandLine == null || commandLine.isBlank()) return "";
        String command = commandLine.trim().split("\\s+")[0].toLowerCase(Locale.ROOT);
        return command.startsWith("/") ? command : "/" + command;
    }

    private void syncBlindnessNextTick(Player player) {
        Bukkit.getScheduler().runTask(VelioraSuite.getInstance(), () -> LoginSecurityBlindnessManager.sync(player, manager));
    }
}
