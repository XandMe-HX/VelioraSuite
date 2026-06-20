package id.velioragardens.veliorasuite.module.login;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;

public final class LoginListener implements Listener {

    private final LoginManager loginManager;

    public LoginListener(LoginManager loginManager) {
        this.loginManager = loginManager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) { loginManager.handleJoin(event.getPlayer()); }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!loginManager.getConfigFile().get().getBoolean("protection.block-movement", true)) return;
        if (!loginManager.isLoggedIn(event.getPlayer())) event.setTo(event.getFrom());
    }

    @EventHandler(ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        if (!loginManager.getConfigFile().get().getBoolean("protection.block-chat", true)) return;
        if (!loginManager.isLoggedIn(event.getPlayer())) { event.setCancelled(true); loginManager.message(event.getPlayer(), "need-login-or-register"); }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!loginManager.getConfigFile().get().getBoolean("protection.block-commands", true)) return;
        if (loginManager.isLoggedIn(event.getPlayer())) return;
        if (loginManager.isAllowedBeforeLogin(event.getMessage())) return;
        event.setCancelled(true);
        loginManager.message(event.getPlayer(), "need-login-or-register");
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) { if (!loginManager.isLoggedIn(event.getPlayer())) event.setCancelled(true); }
    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) { if (!loginManager.isLoggedIn(event.getPlayer())) event.setCancelled(true); }
    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) { if (!loginManager.isLoggedIn(event.getPlayer())) event.setCancelled(true); }
    @EventHandler(ignoreCancelled = true)
    public void onInventory(InventoryOpenEvent event) { if (event.getPlayer() instanceof org.bukkit.entity.Player player && !loginManager.isLoggedIn(player)) event.setCancelled(true); }
    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) { if (event.getDamager() instanceof org.bukkit.entity.Player player && !loginManager.isLoggedIn(player)) event.setCancelled(true); }
}
