package id.velioragardens.veliorasuite.module.security;

import id.velioragardens.veliorasuite.module.security.model.SecurityDecision;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

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
            return;
        }
        String altKick = manager.checkAltJoin(event.getPlayer());
        if (altKick != null && !altKick.isBlank()) {
            event.getPlayer().kickPlayer(altKick);
            return;
        }
        manager.scheduleOreDigest(event.getPlayer(), 60L);
        sendAdminQuickAccess(event.getPlayer());
    }

    private void sendAdminQuickAccess(org.bukkit.entity.Player player) {
        if (!manager.getConfigManager().hasAdmin(player)) return;
        player.sendMessage("§8[§6VelioraAdmin§8] §eAdmin tools aktif. Buka §f/cmdadmin §euntuk panduan lengkap.");
        player.sendMessage("§8[§6VelioraAdmin§8] §7Security cepat: §f/valt alerts §8| §f/valt list §8| §f/vxray alerts §8| §f/vsecurity status");
        player.sendMessage("§8[§6VelioraAdmin§8] §7Jika ada masalah penting/private, arahkan player ke Owner. Jangan spam, cukup laporan rapi.");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        manager.handleAltQuit(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        SecurityDecision decision = manager.checkCommand(event.getPlayer(), event.getMessage());
        if (decision.blocked()) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(manager.denyMessage(decision));
            return;
        }
        if (manager.checkAltPay(event.getPlayer(), event.getMessage())) {
            event.setCancelled(true);
            return;
        }
        String lower = event.getMessage().toLowerCase();
        if (lower.startsWith("/login") || lower.startsWith("/l ") || lower.equals("/l") || lower.startsWith("/register") || lower.startsWith("/reg")) {
            manager.scheduleOreDigest(event.getPlayer(), 60L);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        manager.trackOrePlace(event.getPlayer(), event.getBlock());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        manager.trackOreBreak(event.getPlayer(), event.getBlock());
    }

    @EventHandler
    public void onCommandSend(PlayerCommandSendEvent event) {
        manager.getTabProtectionManager().filter(event.getPlayer(), event.getCommands());
    }
}
