package id.velioragardens.veliorasuite.module.adminmonitor;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;

public final class AdminMonitorListener implements Listener {
    private final AdminMonitorManager manager;
    public AdminMonitorListener(AdminMonitorManager manager) { this.manager = manager; }
    @EventHandler(priority = EventPriority.MONITOR) public void onJoin(PlayerJoinEvent event) { manager.login(event.getPlayer()); }
    @EventHandler(priority = EventPriority.MONITOR) public void onQuit(PlayerQuitEvent event) { manager.logout(event.getPlayer(), "LOGOUT"); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) public void onCommand(PlayerCommandPreprocessEvent event) { manager.command(event.getPlayer(), event.getMessage()); }
    @EventHandler(priority = EventPriority.MONITOR) public void onWorldChange(PlayerChangedWorldEvent event) { manager.worldChange(event.getPlayer(), event.getFrom().getName(), event.getPlayer().getWorld().getName()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) public void onTeleport(PlayerTeleportEvent event) { manager.teleport(event.getPlayer(), event.getCause().name(), event.getFrom(), event.getTo()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) public void onGameMode(PlayerGameModeChangeEvent event) { manager.gameMode(event.getPlayer(), event.getNewGameMode().name()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) public void onFlight(PlayerToggleFlightEvent event) { manager.flight(event.getPlayer(), event.isFlying()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) public void onBlockPlace(BlockPlaceEvent event) { manager.blockPlace(event.getPlayer(), event.getBlockPlaced().getType().name(), event.getBlockPlaced().getLocation()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) public void onBlockBreak(BlockBreakEvent event) { manager.blockBreak(event.getPlayer(), event.getBlock().getType().name(), event.getBlock().getLocation()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) public void onInteract(PlayerInteractEvent event) { manager.interact(event.getPlayer(), event.getAction().name(), event.getClickedBlock() == null ? event.getMaterial().name() : event.getClickedBlock().getType().name(), event.getClickedBlock() == null ? event.getPlayer().getLocation() : event.getClickedBlock().getLocation()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) public void onInteractEntity(PlayerInteractEntityEvent event) { manager.interactEntity(event.getPlayer(), event.getRightClicked().getType().name()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) public void onDamage(EntityDamageByEntityEvent event) { if (event.getDamager() instanceof Player player) manager.attackEntity(player, event.getEntity().getType().name()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) public void onInventory(InventoryClickEvent event) { if (event.getWhoClicked() instanceof Player player) manager.inventory(player, event.getView().getTitle(), event.getCurrentItem() == null ? "AIR" : event.getCurrentItem().getType().name()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) public void onPickup(EntityPickupItemEvent event) { if (event.getEntity() instanceof Player player) manager.itemPickup(player, event.getItem().getItemStack().getType().name()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) public void onDrop(PlayerDropItemEvent event) { manager.itemDrop(event.getPlayer(), event.getItemDrop().getItemStack().getType().name()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) public void onConsume(PlayerItemConsumeEvent event) { manager.consume(event.getPlayer(), event.getItem().getType().name()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) public void onBucketEmpty(PlayerBucketEmptyEvent event) { manager.bucket(event.getPlayer(), "BUCKET_EMPTY", event.getBucket().name()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) public void onBucketFill(PlayerBucketFillEvent event) { manager.bucket(event.getPlayer(), "BUCKET_FILL", event.getBucket().name()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) public void onChat(AsyncPlayerChatEvent event) { manager.chatAsync(event.getPlayer(), event.getMessage()); }
}
