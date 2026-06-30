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
        teleportNextTick(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(VelioraSuite.getInstance(), () -> teleportIfAuthenticated(event.getPlayer()), 3L);
    }

    private void teleportNextTick(Player player) {
        Bukkit.getScheduler().runTask(VelioraSuite.getInstance(), () -> teleportIfAuthenticated(player));
    }

    private void teleportIfAuthenticated(Player player) {
        if (player == null || !player.isOnline()) return;
        if (!manager.isAuthenticated(player)) return;
        if (!manager.getConfigManager().isTeleportAfterAuthEnabled()) return;
        Location location = manager.getConfigManager().getTeleportAfterAuthLocation();
        if (location == null || location.getWorld() == null) return;
        player.teleport(location);
    }

    private String commandToken(String raw) {
        if (raw == null || raw.isBlank()) return "";
        String command = raw.trim().split("\\s+")[0].toLowerCase(Locale.ROOT);
        return command.startsWith("/") ? command : "/" + command;
    }
}
