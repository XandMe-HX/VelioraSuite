package id.velioragardens.veliorasuite.module.loginsecurity;

import id.velioragardens.veliorasuite.VelioraSuite;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.Locale;

public final class LoginSecuritySpawnListener implements Listener {
    private final LoginSecurityManager manager;

    public LoginSecuritySpawnListener(LoginSecurityManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAuthCommand(PlayerCommandPreprocessEvent event) {
        String command = commandToken(event.getMessage());
        if (!command.equals("/login") && !command.equals("/l") && !command.equals("/register") && !command.equals("/reg") && !command.equals("/r")) return;
        teleportAfterAuth(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(VelioraSuite.getInstance(), () -> teleportToLoginSpawn(player), 1L);
        Bukkit.getScheduler().runTaskLater(VelioraSuite.getInstance(), () -> teleportToLoginSpawn(player), 5L);
    }

    private void teleportAfterAuth(Player player) {
        Bukkit.getScheduler().runTask(VelioraSuite.getInstance(), () -> {
            if (player == null || !player.isOnline()) return;
            if (!manager.isAuthenticated(player)) return;
            teleportToConfiguredSpawn(player);
        });
    }

    private void teleportToLoginSpawn(Player player) {
        if (player == null || !player.isOnline()) return;
        if (manager.isAuthenticated(player)) {
            teleportToConfiguredSpawn(player);
            return;
        }
        Location location = getConfiguredSpawn();
        if (location == null) return;
        manager.getSessionManager().setAuthLocation(player, location);
        player.teleport(location);
    }

    private void teleportToConfiguredSpawn(Player player) {
        Location location = getConfiguredSpawn();
        if (location == null) return;
        manager.getSessionManager().setAuthLocation(player, location);
        player.teleport(location);
    }

    private Location getConfiguredSpawn() {
        if (!manager.getConfigManager().isTeleportAfterAuthEnabled()) return null;
        Location location = manager.getConfigManager().getTeleportAfterAuthLocation();
        if (location == null || location.getWorld() == null) return null;
        return location;
    }

    private String commandToken(String raw) {
        if (raw == null || raw.isBlank()) return "";
        String command = raw.trim().split("\\s+")[0].toLowerCase(Locale.ROOT);
        return command.startsWith("/") ? command : "/" + command;
    }
}
