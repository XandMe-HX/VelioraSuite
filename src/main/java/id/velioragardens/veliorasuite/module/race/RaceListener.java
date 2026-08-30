package id.velioragardens.veliorasuite.module.race;

import id.velioragardens.veliorasuite.VelioraSuite;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Enforcement is dormant in phase 1, but every restricted action already has one guarded path. */
public final class RaceListener implements Listener, CommandExecutor, TabCompleter {
    private final VelioraSuite plugin;
    private final RaceManager manager;
    private final RaceGui gui;
    private final Map<UUID, Long> reminders = new ConcurrentHashMap<>();
    public RaceListener(VelioraSuite plugin, RaceManager manager, RaceGui gui) { this.plugin = plugin; this.manager = manager; this.gui = gui; }

    private boolean pending(Player player) { return manager.enforcementEnabled() && !player.hasPermission("veliorasuite.race.admin") && !manager.selected(player.getUniqueId()); }
    private void remind(Player player) {
        long now = System.currentTimeMillis();
        if (now - reminders.getOrDefault(player.getUniqueId(), 0L) < 2_000L) return;
        reminders.put(player.getUniqueId(), now);
        player.sendActionBar(net.kyori.adventure.text.Component.text("Selesaikan pemilihan ras untuk mulai bermain."));
    }

    @EventHandler public void move(PlayerMoveEvent event) { if (!pending(event.getPlayer()) || event.getTo() == null) return; if (event.getFrom().getBlockX() != event.getTo().getBlockX() || event.getFrom().getBlockY() != event.getTo().getBlockY() || event.getFrom().getBlockZ() != event.getTo().getBlockZ()) { event.setTo(event.getFrom()); remind(event.getPlayer()); } }
    @EventHandler public void breakBlock(BlockBreakEvent event) { if (pending(event.getPlayer())) { event.setCancelled(true); remind(event.getPlayer()); } }
    @EventHandler public void drop(PlayerDropItemEvent event) { if (pending(event.getPlayer())) { event.setCancelled(true); remind(event.getPlayer()); } }
    @EventHandler public void pickup(PlayerPickupItemEvent event) { if (pending(event.getPlayer())) { event.setCancelled(true); remind(event.getPlayer()); } }
    @EventHandler public void teleport(PlayerTeleportEvent event) { if (pending(event.getPlayer())) { event.setCancelled(true); remind(event.getPlayer()); } }
    @EventHandler public void damage(EntityDamageByEntityEvent event) { if (event.getDamager() instanceof Player player && pending(player)) { event.setCancelled(true); remind(player); } }
    @EventHandler public void damagePending(EntityDamageEvent event) { if (event.getEntity() instanceof Player player && pending(player)) event.setCancelled(true); }
    @EventHandler public void interact(PlayerInteractEvent event) { if (pending(event.getPlayer())) { event.setCancelled(true); remind(event.getPlayer()); } }
    @EventHandler public void commandPreprocess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!pending(player)) return;
        String root = event.getMessage().trim().toLowerCase(java.util.Locale.ROOT).split("\\s+", 2)[0];
        if (root.equals("/race") || root.equals("/ras") || root.equals("/vrace") || root.equals("/login") || root.equals("/l") || root.equals("/register") || root.equals("/reg")) return;
        event.setCancelled(true);
        remind(player);
    }
    @EventHandler public void join(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (manager.selected(player.getUniqueId())) { Bukkit.getScheduler().runTaskLater(plugin, () -> gui.applySavedScale(player), 10L); return; }
        if (pending(player)) Bukkit.getScheduler().runTaskLater(plugin, () -> gui.openGuide(player), 30L);
    }
    @EventHandler public void quit(PlayerQuitEvent event) { reminders.remove(event.getPlayer().getUniqueId()); manager.clearDraft(event.getPlayer().getUniqueId()); }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("admin")) {
            if (!sender.hasPermission("veliorasuite.race.admin")) { sender.sendMessage("§cKhusus admin."); return true; }
            if (args.length >= 3 && args[1].equalsIgnoreCase("reset")) {
                Player target = Bukkit.getPlayerExact(args[2]);
                if (target == null) { sender.sendMessage("§cPemain harus online untuk reset aman di fase ini."); return true; }
                manager.reset(target.getUniqueId()); gui.resetScale(target); sender.sendMessage("§aData ras " + target.getName() + " direset dan skala dikembalikan normal."); return true;
            }
            if (args.length >= 3 && args[1].equalsIgnoreCase("enforce")) {
                if (!args[2].equalsIgnoreCase("on") && !args[2].equalsIgnoreCase("off")) { sender.sendMessage("§e/race admin enforce <on|off>"); return true; }
                boolean enabled = args[2].equalsIgnoreCase("on");
                manager.setEnforcementEnabled(enabled);
                sender.sendMessage(enabled ? "§aPenguncian pilihan ras aktif. Player baru yang belum memilih akan diarahkan ke GUI." : "§ePenguncian pilihan ras dimatikan.");
                return true;
            }
            sender.sendMessage("§e/race admin reset <player> §7- Reset data ras player.");
            sender.sendMessage("§e/race admin enforce <on|off> §7- Aktif/nonaktifkan pemilihan wajib."); return true;
        }
        if (!(sender instanceof Player player)) { sender.sendMessage("§cCommand ini khusus player."); return true; }
        if (manager.selected(player.getUniqueId())) sender.sendMessage("§d[Ras] §fRas aktif: §e" + manager.race(player.getUniqueId()));
        else gui.openGuide(player);
        return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("veliorasuite.race.admin")) return List.of("admin", "status");
        if (args.length == 2 && args[0].equalsIgnoreCase("admin")) return List.of("reset", "enforce");
        if (args.length == 3 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("enforce")) return List.of("on", "off");
        return List.of();
    }
}
