package id.velioragardens.veliorasuite.module.adminmonitor;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;

public final class AdminMonitorListener implements Listener {
    private final AdminMonitorManager manager;
    public AdminMonitorListener(AdminMonitorManager manager) { this.manager = manager; }
    @EventHandler(priority = EventPriority.MONITOR) public void onJoin(PlayerJoinEvent event) { if (manager.isStaff(event.getPlayer())) manager.login(event.getPlayer()); }
    @EventHandler(priority = EventPriority.MONITOR) public void onQuit(PlayerQuitEvent event) { if (manager.isStaff(event.getPlayer())) manager.logout(event.getPlayer(), "LOGOUT"); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) public void onCommand(PlayerCommandPreprocessEvent event) { if (manager.isStaff(event.getPlayer())) manager.command(event.getPlayer(), event.getMessage()); }
    @EventHandler(priority = EventPriority.MONITOR) public void onWorldChange(PlayerChangedWorldEvent event) { if (manager.isStaff(event.getPlayer())) manager.worldChange(event.getPlayer(), event.getFrom().getName(), event.getPlayer().getWorld().getName()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) public void onTeleport(PlayerTeleportEvent event) { if (manager.isStaff(event.getPlayer())) manager.teleport(event.getPlayer(), event.getCause().name(), event.getFrom(), event.getTo()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) public void onGameMode(PlayerGameModeChangeEvent event) { if (manager.isStaff(event.getPlayer())) manager.gameMode(event.getPlayer(), event.getNewGameMode().name()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) public void onFlight(PlayerToggleFlightEvent event) { if (manager.isStaff(event.getPlayer())) manager.flight(event.getPlayer(), event.isFlying()); }
}
