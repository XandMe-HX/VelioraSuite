package id.velioragardens.veliorasuite.module.security;

import id.velioragardens.veliorasuite.module.security.model.SecurityDecision;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
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
        manager.scheduleAntiDupeScan(event.getPlayer(), 100L);
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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!manager.handleSpawnerPlace(event.getPlayer(), event.getBlockPlaced(), event.getItemInHand())) {
            event.setCancelled(true);
            return;
        }
        manager.trackOrePlace(event.getPlayer(), event.getBlock());
    }

    /** A later protection plugin may still cancel a valid placement after the guard reserved its slot. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onBlockPlaceResult(BlockPlaceEvent event) {
        if (event.isCancelled()) manager.rollbackSpawnerPlace(event.getPlayer(), event.getBlockPlaced());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        manager.handleSpawnerBreak(event.getBlock());
        manager.trackMiningBreak(event.getPlayer(), event.getBlock());
        manager.trackOreBreak(event.getPlayer(), event.getBlock());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().forEach(manager::handleSpawnerRemoved);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().forEach(manager::handleSpawnerRemoved);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof org.bukkit.entity.Player player) manager.scheduleAntiDupeScan(player, 1L);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof org.bukkit.entity.Player player) manager.scheduleAntiDupeScan(player, 1L);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof org.bukkit.entity.Player player) manager.scheduleAntiDupeScan(player, 1L);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof org.bukkit.entity.Player player) manager.scheduleAntiDupeScan(player, 1L);
    }

    @EventHandler
    public void onCommandSend(PlayerCommandSendEvent event) {
        manager.getTabProtectionManager().filter(event.getPlayer(), event.getCommands());
    }
}
