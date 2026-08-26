package id.velioragardens.veliorasuite.module.warp;

import id.velioragardens.veliorasuite.VelioraSuite;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Global safety net for command/plugin teleports, broken RTP destinations and void falls. */
public final class TeleportSafetyListener implements Listener {
    private final VelioraSuite plugin;
    private final WarpManager warps;
    private final Map<UUID, Long> frozenUntil = new HashMap<>();
    private final Map<UUID, Long> rtpRequestedAt = new HashMap<>();
    private final Map<UUID, Double> rtpBalances = new HashMap<>();
    private final Set<UUID> internalRescue = new HashSet<>();
    private int safetyTask = -1;

    public TeleportSafetyListener(VelioraSuite plugin, WarpManager warps) {
        this.plugin = plugin;
        this.warps = warps;
    }

    public void start() {
        safetyTask = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            long now = System.currentTimeMillis();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.isDead()) continue;
                if (player.getLocation().getY() <= player.getWorld().getMinHeight() + 3) rescue(player, "void");
                Long until = frozenUntil.get(player.getUniqueId());
                if (until != null && until <= now) {
                    frozenUntil.remove(player.getUniqueId());
                    player.removePotionEffect(PotionEffectType.BLINDNESS);
                    player.clearTitle();
                }
            }
        }, 5L, 5L);
    }

    public void stop() {
        if (safetyTask >= 0) Bukkit.getScheduler().cancelTask(safetyTask);
        safetyTask = -1;
        frozenUntil.clear();
        rtpRequestedAt.clear();
        rtpBalances.clear();
        internalRescue.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        boolean rescue = internalRescue.remove(player.getUniqueId());
        PlayerTeleportEvent.TeleportCause cause = event.getCause();
        if (!rescue && cause != PlayerTeleportEvent.TeleportCause.COMMAND && cause != PlayerTeleportEvent.TeleportCause.PLUGIN) return;
        Location destination = event.getTo() == null ? null : event.getTo().clone();
        if (destination == null) return;
        destination.getChunk().load(true);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            protectArrival(player);
            Long requested = rtpRequestedAt.remove(player.getUniqueId());
            if (requested != null && System.currentTimeMillis() - requested <= 30_000L) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> validateRtp(player, destination), 40L);
            }
        });
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String command = event.getMessage().toLowerCase(java.util.Locale.ROOT);
        if (command.equals("/rtp") || command.startsWith("/rtp ") || command.startsWith("/betterrtp")) {
            rtpRequestedAt.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
            Double balance = vaultBalance(event.getPlayer());
            if (balance != null) rtpBalances.put(event.getPlayer().getUniqueId(), balance);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Long until = frozenUntil.get(event.getPlayer().getUniqueId());
        if (until == null || until <= System.currentTimeMillis() || event.getTo() == null) return;
        if (event.getFrom().getX() == event.getTo().getX() && event.getFrom().getY() == event.getTo().getY()
                && event.getFrom().getZ() == event.getTo().getZ()) return;
        Location locked = event.getFrom().clone();
        locked.setYaw(event.getTo().getYaw());
        locked.setPitch(event.getTo().getPitch());
        event.setTo(locked);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVoidDamage(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.VOID || !(event.getEntity() instanceof Player player)) return;
        event.setCancelled(true);
        rescue(player, "void");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        frozenUntil.remove(id);
        rtpRequestedAt.remove(id);
        rtpBalances.remove(id);
        internalRescue.remove(id);
    }

    private void protectArrival(Player player) {
        long arrivalId = System.currentTimeMillis() + 3_000L;
        frozenUntil.put(player.getUniqueId(), arrivalId);
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0, false, false, false));
        final int[] seconds = {3};
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            if (!player.isOnline() || !Long.valueOf(arrivalId).equals(frozenUntil.get(player.getUniqueId())) || seconds[0] <= 0) {
                task.cancel();
                return;
            }
            player.sendTitle("§bLokasi dimuat", "§fAman dalam §e" + seconds[0] + " detik", 0, 24, 0);
            seconds[0]--;
        }, 0L, 20L);
    }

    private void validateRtp(Player player, Location expected) {
        if (!player.isOnline() || player.getWorld() != expected.getWorld()) {
            rtpBalances.remove(player.getUniqueId());
            return;
        }
        Location current = player.getLocation();
        if (current.getY() <= current.getWorld().getMinHeight() + 4 || !current.getChunk().isLoaded()
                || (current.getBlock().getType() == Material.VOID_AIR)) {
            rescue(player, "rtp");
        } else rtpBalances.remove(player.getUniqueId());
    }

    private void rescue(Player player, String reason) {
        if (!player.isOnline() || internalRescue.contains(player.getUniqueId())) return;
        Location target = warps.safetyTarget(player.getLocation());
        if (target == null || target.getWorld() == null) return;
        target.getChunk().load(true);
        internalRescue.add(player.getUniqueId());
        if (!player.teleport(target, PlayerTeleportEvent.TeleportCause.PLUGIN)) {
            internalRescue.remove(player.getUniqueId());
            return;
        }
        player.setFallDistance(0);
        player.sendMessage(warps.color(warps.message("rescued", "%prefix% &eKamu dipindahkan ke lokasi aman karena area tujuan belum aman.")));
        if (reason.equals("rtp")) refundRtp(player);
        plugin.getLogger().info("VelioraWarp: " + player.getName() + " diselamatkan dari " + reason + ".");
    }

    private Double vaultBalance(Player player) {
        try {
            Class<?> economyClass = Class.forName("net.milkbowl.vault.economy.Economy");
            Object registration = Bukkit.getServicesManager().getRegistration((Class) economyClass);
            if (registration == null) return null;
            Object economy = registration.getClass().getMethod("getProvider").invoke(registration);
            return ((Number) economyClass.getMethod("getBalance", org.bukkit.OfflinePlayer.class)
                    .invoke(economy, player)).doubleValue();
        } catch (ReflectiveOperationException | LinkageError ignored) { return null; }
    }

    private void refundRtp(Player player) {
        Double before = rtpBalances.remove(player.getUniqueId());
        Double current = vaultBalance(player);
        if (before == null || current == null || before <= current) return;
        double refund = before - current;
        try {
            Class<?> economyClass = Class.forName("net.milkbowl.vault.economy.Economy");
            Object registration = Bukkit.getServicesManager().getRegistration((Class) economyClass);
            if (registration == null) return;
            Object economy = registration.getClass().getMethod("getProvider").invoke(registration);
            economyClass.getMethod("depositPlayer", org.bukkit.OfflinePlayer.class, double.class)
                    .invoke(economy, player, refund);
            player.sendMessage(warps.color(warps.message("rtp-refund", "%prefix% &aBiaya RTP dikembalikan karena lokasi gagal dimuat.")));
        } catch (ReflectiveOperationException | LinkageError ignored) { }
    }
}
