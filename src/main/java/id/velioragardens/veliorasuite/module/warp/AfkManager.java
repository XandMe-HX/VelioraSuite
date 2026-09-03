package id.velioragardens.veliorasuite.module.warp;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.module.guard.GuardClaimModule;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
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
    private final Set<UUID> manualAfk = new HashSet<>();
    private final Map<UUID, Long> lastReward = new HashMap<>();
    private int taskId = -1;
    private boolean essentialsAvailable;

    public AfkManager(VelioraSuite plugin, WarpManager warps) {
        this.plugin = plugin;
        this.warps = warps;
    }

    public void start() {
        cleanupOrphanMarkers();
        essentialsAvailable = Bukkit.getPluginManager().isPluginEnabled("Essentials");
        for (Player player : Bukkit.getOnlinePlayers()) touch(player);
        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            long now = System.currentTimeMillis();
            long timeout = warps.afkTimeoutSeconds() * 1000L;
            for (Player player : Bukkit.getOnlinePlayers()) {
                Boolean essentialsAfk = essentialsAfk(player);
                if (essentialsAfk != null && essentialsAfk != afk.contains(player.getUniqueId())) {
                    setAfk(player, essentialsAfk, false);
                } else if (essentialsAfk == null && !afk.contains(player.getUniqueId()) && timeout > 0
                        && now - lastActivity.getOrDefault(player.getUniqueId(), now) >= timeout) setAfk(player, true);
                if (afk.contains(player.getUniqueId()) && inAfkArea(player)
                        && warps.afkRewardsEnabled() && now - lastReward.getOrDefault(player.getUniqueId(), now) >= 60_000L) {
                    reward(player);
                    lastReward.put(player.getUniqueId(), now);
                }
                TextDisplay marker = markers.get(player.getUniqueId());
                if (marker != null && marker.isValid() && marker.getVehicle() != player) player.addPassenger(marker);
            }
        }, 20L, 20L);
    }

    public void stop() {
        if (taskId >= 0) Bukkit.getScheduler().cancelTask(taskId);
        markers.values().forEach(marker -> { if (marker != null && marker.isValid()) marker.remove(); });
        cleanupOrphanMarkers();
        markers.clear(); afk.clear(); manualAfk.clear(); lastActivity.clear(); lastReward.clear();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Command ini hanya untuk pemain."); return true; }
        if (essentialsAvailable) {
            Bukkit.dispatchCommand(player, "essentials:afk");
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Boolean state = essentialsAfk(player);
                if (state != null) setAfk(player, state, false);
            }, 1L);
            return true;
        }
        boolean enable = !afk.contains(player.getUniqueId());
        if (enable) manualAfk.add(player.getUniqueId()); else manualAfk.remove(player.getUniqueId());
        setAfk(player, enable);
        return true;
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) { touch(event.getPlayer()); }
    @EventHandler public void onInteract(PlayerInteractEvent event) { activate(event.getPlayer()); }
    @EventHandler public void onChat(AsyncPlayerChatEvent event) { Bukkit.getScheduler().runTask(plugin, () -> activate(event.getPlayer())); }
    @EventHandler public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!event.getMessage().trim().equalsIgnoreCase("/afk")) activate(event.getPlayer());
    }
    @EventHandler public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null || (event.getFrom().getX() == event.getTo().getX()
                && event.getFrom().getY() == event.getTo().getY() && event.getFrom().getZ() == event.getTo().getZ())) return;
        activate(event.getPlayer());
    }
    @EventHandler public void onQuit(PlayerQuitEvent event) { clear(event.getPlayer()); }

    private void activate(Player player) {
        touch(player);
        // EssentialsX owns automatic AFK transitions when present. Mirroring it here avoids
        // two plugins rapidly toggling the same player on movement, vehicle, or GSit packets.
        if (essentialsAvailable) return;
        // Manual AFK is allowed to chat and walk around its designated AFK area.
        if (afk.contains(player.getUniqueId()) && !(manualAfk.contains(player.getUniqueId()) && inAfkArea(player))) setAfk(player, false);
    }

    private void touch(Player player) { lastActivity.put(player.getUniqueId(), System.currentTimeMillis()); }

    private void setAfk(Player player, boolean state) { setAfk(player, state, true); }

    private void setAfk(Player player, boolean state, boolean notify) {
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
            player.addPassenger(marker);
            if (notify) player.sendMessage(warps.color(warps.message("afk-on", "%prefix% &eKamu sekarang AFK.")));
        } else {
            if (!afk.remove(player.getUniqueId())) return;
            TextDisplay marker = markers.remove(player.getUniqueId());
            if (marker != null && marker.isValid()) marker.remove();
            touch(player);
            if (notify) player.sendMessage(warps.color(warps.message("afk-off", "%prefix% &aKamu tidak lagi AFK.")));
        }
    }

    /** Reads EssentialsX as the authoritative AFK state without a hard dependency. */
    private Boolean essentialsAfk(Player player) {
        if (!essentialsAvailable) return null;
        try {
            org.bukkit.plugin.Plugin hook = Bukkit.getPluginManager().getPlugin("Essentials");
            if (hook == null || !hook.isEnabled()) return null;
            Object user = hook.getClass().getMethod("getUser", UUID.class).invoke(hook, player.getUniqueId());
            if (user == null) return false;
            Object value = user.getClass().getMethod("isAfk").invoke(user);
            return value instanceof Boolean result ? result : false;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    private boolean inAfkArea(Player player) {
        if (GuardClaimModule.isAfkClaim(player.getLocation())) return true;
        double radius = warps.afkZoneRadius();
        WarpManager.WarpPoint point = warps.get(warps.afkZoneWarp());
        if (point == null || player.getWorld() == null || !player.getWorld().getUID().equals(point.world())) return false;
        double dx = player.getLocation().getX() - point.x();
        double dz = player.getLocation().getZ() - point.z();
        return dx * dx + dz * dz <= radius * radius;
    }

    private Location markerLocation(Player player) {
        // Keep the AFK label above the current player pose instead of floating at a fixed old height.
        return player.getLocation().clone().add(0, player.isSneaking() ? 2.15D : 2.75D, 0);
    }

    private void reward(Player player) {
        double amount = warps.afkRewardPerMinute();
        if (amount <= 0D) return;
        try {
            Class<?> economyType = Class.forName("net.milkbowl.vault.economy.Economy");
            Object registration = Bukkit.getServicesManager().getRegistration((Class) economyType);
            if (registration == null) return;
            Object economy = registration.getClass().getMethod("getProvider").invoke(registration);
            economyType.getMethod("depositPlayer", org.bukkit.OfflinePlayer.class, double.class).invoke(economy, player, amount);
            player.sendActionBar(Component.text("AFK +$" + String.format(java.util.Locale.US, "%.0f", amount), NamedTextColor.GREEN));
        } catch (ReflectiveOperationException | LinkageError ignored) { }
    }

    private void clear(Player player) {
        afk.remove(player.getUniqueId()); manualAfk.remove(player.getUniqueId()); lastActivity.remove(player.getUniqueId()); lastReward.remove(player.getUniqueId());
        TextDisplay marker = markers.remove(player.getUniqueId());
        if (marker != null && marker.isValid()) marker.remove();
    }

    /** Removes only Suite-owned AFK labels left after an interrupted reload. */
    private void cleanupOrphanMarkers() {
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (org.bukkit.entity.Entity entity : new java.util.ArrayList<>(world.getEntities())) {
                if (entity.getScoreboardTags().contains("veliora_afk_marker")) entity.remove();
            }
        }
    }
}
