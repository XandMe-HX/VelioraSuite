package id.velioragardens.veliorasuite.module.warp;

import id.velioragardens.veliorasuite.VelioraSuite;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class AfkManager implements Listener, CommandExecutor {
    private final VelioraSuite plugin;
    private final WarpManager warps;
    private final Map<UUID, Long> lastActivity = new HashMap<>();
    private final Map<UUID, TextDisplay> markers = new HashMap<>();
    private final Set<UUID> afk = new HashSet<>();
    private int taskId = -1;

    public AfkManager(VelioraSuite plugin, WarpManager warps) {
        this.plugin = plugin;
        this.warps = warps;
    }

    public void start() {
        for (Player player : Bukkit.getOnlinePlayers()) touch(player);
        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            long now = System.currentTimeMillis();
            long timeout = warps.afkTimeoutSeconds() * 1000L;
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!afk.contains(player.getUniqueId()) && timeout > 0
                        && now - lastActivity.getOrDefault(player.getUniqueId(), now) >= timeout) setAfk(player, true);
                TextDisplay marker = markers.get(player.getUniqueId());
                if (marker != null && marker.isValid()) marker.teleport(markerLocation(player));
            }
        }, 20L, 10L);
    }

    public void stop() {
        if (taskId >= 0) Bukkit.getScheduler().cancelTask(taskId);
        markers.values().forEach(marker -> { if (marker != null && marker.isValid()) marker.remove(); });
        markers.clear(); afk.clear(); lastActivity.clear();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Command ini hanya untuk pemain."); return true; }
        setAfk(player, !afk.contains(player.getUniqueId()));
        return true;
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) { touch(event.getPlayer()); }
    @EventHandler public void onInteract(PlayerInteractEvent event) { activate(event.getPlayer()); }
    @EventHandler public void onChat(AsyncPlayerChatEvent event) { Bukkit.getScheduler().runTask(plugin, () -> activate(event.getPlayer())); }
    @EventHandler public void onCommand(PlayerCommandPreprocessEvent event) {
        if (event.getMessage().equalsIgnoreCase("/afk") && essentialsOwnsAfkCommand()) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Boolean state = essentialsAfk(event.getPlayer());
                if (state != null) setAfk(event.getPlayer(), state);
            }, 2L);
        } else if (!event.getMessage().equalsIgnoreCase("/afk")) activate(event.getPlayer());
    }
    @EventHandler public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null || (event.getFrom().getX() == event.getTo().getX()
                && event.getFrom().getY() == event.getTo().getY() && event.getFrom().getZ() == event.getTo().getZ())) return;
        activate(event.getPlayer());
    }
    @EventHandler public void onQuit(PlayerQuitEvent event) { clear(event.getPlayer()); }

    private void activate(Player player) {
        touch(player);
        if (afk.contains(player.getUniqueId())) setAfk(player, false);
    }

    private void touch(Player player) { lastActivity.put(player.getUniqueId(), System.currentTimeMillis()); }

    private void setAfk(Player player, boolean state) {
        if (state) {
            if (!afk.add(player.getUniqueId())) return;
            TextDisplay marker = player.getWorld().spawn(markerLocation(player), TextDisplay.class, display -> {
                display.text(Component.text("AFK", NamedTextColor.YELLOW));
                display.setBillboard(Display.Billboard.CENTER);
                display.setSeeThrough(true);
                display.setShadowed(true);
                display.setPersistent(false);
                display.addScoreboardTag("veliora_afk_marker");
            });
            markers.put(player.getUniqueId(), marker);
            player.sendMessage(warps.color(warps.message("afk-on", "%prefix% &eKamu sekarang AFK.")));
        } else {
            if (!afk.remove(player.getUniqueId())) return;
            TextDisplay marker = markers.remove(player.getUniqueId());
            if (marker != null && marker.isValid()) marker.remove();
            touch(player);
            player.sendMessage(warps.color(warps.message("afk-off", "%prefix% &aKamu tidak lagi AFK.")));
        }
    }

    private Location markerLocation(Player player) { return player.getLocation().clone().add(0, 2.65D, 0); }

    private boolean essentialsOwnsAfkCommand() {
        PluginCommand command = Bukkit.getPluginCommand("afk");
        return command != null && command.getPlugin().getName().equalsIgnoreCase("Essentials");
    }

    private Boolean essentialsAfk(Player player) {
        Plugin essentials = Bukkit.getPluginManager().getPlugin("Essentials");
        if (essentials == null || !essentials.isEnabled()) return null;
        try {
            Object user;
            try {
                user = essentials.getClass().getMethod("getUser", UUID.class).invoke(essentials, player.getUniqueId());
            } catch (NoSuchMethodException ignored) {
                user = essentials.getClass().getMethod("getUser", Player.class).invoke(essentials, player);
            }
            if (user == null) return null;
            return (Boolean) user.getClass().getMethod("isAfk").invoke(user);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    private void clear(Player player) {
        afk.remove(player.getUniqueId()); lastActivity.remove(player.getUniqueId());
        TextDisplay marker = markers.remove(player.getUniqueId());
        if (marker != null && marker.isValid()) marker.remove();
    }
}
