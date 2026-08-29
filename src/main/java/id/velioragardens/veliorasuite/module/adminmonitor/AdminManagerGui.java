package id.velioragardens.veliorasuite.module.adminmonitor;

import id.velioragardens.veliorasuite.VelioraSuite;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** A deliberately small, confirmation-first moderation screen. It never scans worlds or profiles. */
public final class AdminManagerGui implements Listener {
    private static final int PAGE_SIZE = 45;
    private final VelioraSuite plugin;
    private final AdminMonitorManager monitor;
    private final NamespacedKey actionKey;
    private final NamespacedKey targetKey;
    private final Set<UUID> frozen = new HashSet<>();

    public AdminManagerGui(VelioraSuite plugin, AdminMonitorManager monitor) {
        this.plugin = plugin;
        this.monitor = monitor;
        this.actionKey = new NamespacedKey(plugin, "adminmanager_action");
        this.targetKey = new NamespacedKey(plugin, "adminmanager_target");
    }

    public void openPlayers(Player viewer, int requestedPage) {
        List<OfflinePlayer> players = new ArrayList<>(List.of(Bukkit.getOfflinePlayers()));
        Bukkit.getOnlinePlayers().forEach(player -> { if (players.stream().noneMatch(value -> value.getUniqueId().equals(player.getUniqueId()))) players.add(player); });
        players.removeIf(player -> player.getName() == null);
        players.sort(Comparator.comparingLong(OfflinePlayer::getLastPlayed).reversed());
        int pages = Math.max(1, (int) Math.ceil(players.size() / (double) PAGE_SIZE));
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        MenuHolder holder = new MenuHolder("players", null, page);
        Inventory inventory = Bukkit.createInventory(holder, 54, color("&8Admin Manager &7(" + (page + 1) + "/" + pages + ")"));
        holder.inventory = inventory;
        int start = page * PAGE_SIZE;
        for (int index = start; index < Math.min(players.size(), start + PAGE_SIZE); index++) {
            OfflinePlayer target = players.get(index);
            boolean online = target.isOnline();
            String name = target.getName();
            List<String> lore = new ArrayList<>();
            lore.add(online ? "&a● Online sekarang" : "&7● Offline");
            lore.add("&7Terakhir terlihat: &f" + timeAgo(target.getLastPlayed()));
            lore.add("&7Klik untuk buka tindakan dan profil.");
            inventory.setItem(index - start, skull(target, (online ? "&a" : "&7") + name, lore, "profile", name));
        }
        inventory.setItem(45, item(Material.ARROW, "&e← Halaman sebelumnya", List.of("&7Klik untuk kembali."), page > 0 ? "previous" : null, null));
        inventory.setItem(49, item(Material.COMPASS, "&bPemain & Moderasi", List.of("&7Pilih kepala pemain untuk melihat profil.", "&7Semua tindakan penting memakai konfirmasi."), null, null));
        inventory.setItem(53, item(Material.ARROW, "&eHalaman berikutnya →", List.of("&7Klik untuk lanjut."), page + 1 < pages ? "next" : null, null));
        viewer.openInventory(inventory);
    }

    private void openProfile(Player viewer, String targetName) {
        OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(targetName);
        if (target == null) target = Bukkit.getOfflinePlayer(targetName);
        MenuHolder holder = new MenuHolder("profile", targetName, 0);
        Inventory inventory = Bukkit.createInventory(holder, 45, color("&8Kelola: &f" + targetName));
        holder.inventory = inventory;
        long played = 0;
        try { played = target.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20L; } catch (Exception ignored) { }
        List<String> info = List.of(
                target.isOnline() ? "&aSedang online" : "&7Sedang offline",
                "&7Terakhir join: &f" + timeAgo(target.getLastPlayed()),
                "&7Pertama join: &f" + timeAgo(target.getFirstPlayed()),
                "&7Playtime: &f" + formatSeconds(played),
                "&7UUID: &8" + target.getUniqueId());
        inventory.setItem(4, skull(target, "&f" + targetName, info, null, null));
        inventory.setItem(19, item(Material.ENDER_PEARL, "&bTeleport ke pemain", List.of("&7Hanya jika pemain sedang online."), "teleport", targetName));
        inventory.setItem(20, item(Material.PACKED_ICE, frozen.contains(target.getUniqueId()) ? "&aLepaskan freeze" : "&eFreeze pemain", List.of("&7Mencegah berjalan dan damage jatuh.", "&7Tidak memberi hukuman permanen."), "freeze", targetName));
        inventory.setItem(21, item(Material.LEATHER_BOOTS, "&eKick", List.of("&7Alasan: &fDikeluarkan oleh staf", "&cButuh konfirmasi."), "confirm_kick", targetName));
        inventory.setItem(23, item(Material.BARRIER, "&cBan", List.of("&7Alasan: &fMelanggar peraturan server", "&cButuh konfirmasi."), "confirm_ban", targetName));
        inventory.setItem(24, item(Material.CLOCK, "&6Tempban 1 hari", List.of("&7Alasan: &fPelanggaran sementara", "&cButuh konfirmasi."), "confirm_tempban", targetName));
        inventory.setItem(25, item(Material.LIME_DYE, "&aUnban", List.of("&7Menghapus ban nama pemain.", "&cButuh konfirmasi."), "confirm_unban", targetName));
        inventory.setItem(40, item(Material.ARROW, "&eKembali ke daftar", List.of("&7Tidak ada perubahan."), "back", null));
        viewer.openInventory(inventory);
    }

    private void openConfirm(Player viewer, String target, String action) {
        MenuHolder holder = new MenuHolder("confirm:" + action, target, 0);
        Inventory inventory = Bukkit.createInventory(holder, 27, color("&4Konfirmasi tindakan"));
        holder.inventory = inventory;
        inventory.setItem(11, item(Material.LIME_WOOL, "&aYa, lanjutkan", List.of("&7Tindakan: &f" + displayAction(action), "&7Target: &f" + target), "execute_" + action, target));
        inventory.setItem(15, item(Material.RED_WOOL, "&cBatal", List.of("&7Kembali tanpa perubahan."), "profile", target));
        viewer.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player viewer)) return;
        if (!(event.getView().getTopInventory().getHolder() instanceof MenuHolder holder)) return;
        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        ItemMeta meta = clicked.getItemMeta();
        String action = meta.getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        String target = meta.getPersistentDataContainer().get(targetKey, PersistentDataType.STRING);
        if (action == null) return;
        if (action.equals("previous")) { openPlayers(viewer, holder.page - 1); return; }
        if (action.equals("next")) { openPlayers(viewer, holder.page + 1); return; }
        if (action.equals("profile")) { if (target != null) openProfile(viewer, target); return; }
        if (action.equals("back")) { openPlayers(viewer, 0); return; }
        if (target == null) return;
        if (action.startsWith("confirm_")) { openConfirm(viewer, target, action.substring("confirm_".length())); return; }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(target);
        if (action.equals("teleport")) {
            Player online = Bukkit.getPlayerExact(target);
            if (online == null) viewer.sendMessage(color("&cPemain sedang tidak online.")); else viewer.teleport(online);
            return;
        }
        if (action.equals("freeze")) {
            if (!offline.isOnline()) { viewer.sendMessage(color("&cFreeze hanya dapat dilakukan pada pemain online.")); return; }
            if (!frozen.add(offline.getUniqueId())) frozen.remove(offline.getUniqueId());
            viewer.sendMessage(color("&aStatus freeze " + target + ": &f" + (frozen.contains(offline.getUniqueId()) ? "aktif" : "nonaktif")));
            openProfile(viewer, target);
            return;
        }
        if (!action.startsWith("execute_")) return;
        String execute = action.substring("execute_".length());
        switch (execute) {
            case "kick" -> { Player online = Bukkit.getPlayerExact(target); if (online != null) online.kickPlayer(color("&cDikeluarkan oleh staf.")); }
            case "ban" -> Bukkit.getBanList(BanList.Type.NAME).addBan(target, "Melanggar peraturan server", null, viewer.getName());
            case "tempban" -> Bukkit.getBanList(BanList.Type.NAME).addBan(target, "Pelanggaran sementara", new Date(System.currentTimeMillis() + 86_400_000L), viewer.getName());
            case "unban" -> Bukkit.getBanList(BanList.Type.NAME).pardon(target);
            default -> { return; }
        }
        Player online = Bukkit.getPlayerExact(target);
        if ((execute.equals("ban") || execute.equals("tempban")) && online != null) online.kickPlayer(color("&cAkun kamu telah diblokir oleh staf."));
        viewer.sendMessage(color("&aTindakan berhasil: &f" + displayAction(execute) + " &ake " + target + "."));
        monitor.command(viewer, "/adminmanager " + execute + " " + target);
        openProfile(viewer, target);
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!frozen.contains(event.getPlayer().getUniqueId())) return;
        if (event.getTo() == null) return;
        if (event.getFrom().getBlockX() != event.getTo().getBlockX() || event.getFrom().getBlockY() != event.getTo().getBlockY() || event.getFrom().getBlockZ() != event.getTo().getBlockZ()) event.setTo(event.getFrom());
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && frozen.contains(player.getUniqueId()) && event.getCause() == EntityDamageEvent.DamageCause.FALL) event.setCancelled(true);
    }

    private ItemStack skull(OfflinePlayer owner, String name, List<String> lore, String action, String target) {
        ItemStack stack = item(Material.PLAYER_HEAD, name, lore, action, target);
        SkullMeta skull = (SkullMeta) stack.getItemMeta();
        skull.setOwningPlayer(owner);
        stack.setItemMeta(skull);
        return stack;
    }
    private ItemStack item(Material material, String name, List<String> lore, String action, String target) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(color(name));
        meta.setLore(lore.stream().map(this::color).toList());
        if (action != null) meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        if (target != null) meta.getPersistentDataContainer().set(targetKey, PersistentDataType.STRING, target);
        stack.setItemMeta(meta);
        return stack;
    }
    private String displayAction(String action) { return switch (action) { case "kick" -> "Kick"; case "ban" -> "Ban"; case "tempban" -> "Tempban 1 hari"; case "unban" -> "Unban"; default -> action; }; }
    private String color(String text) { return ChatColor.translateAlternateColorCodes('&', text); }
    private String timeAgo(long time) { return time <= 0 ? "belum ada data" : formatSeconds(Math.max(0, (System.currentTimeMillis() - time) / 1000L)) + " lalu"; }
    private String formatSeconds(long seconds) { long hours = seconds / 3600L; return hours + " jam " + ((seconds % 3600L) / 60L) + " menit"; }
    private static final class MenuHolder implements InventoryHolder {
        private final String type; private final String target; private final int page; private Inventory inventory;
        private MenuHolder(String type, String target, int page) { this.type = type; this.target = target; this.page = page; }
        @Override public Inventory getInventory() { return inventory; }
    }
}
