package id.velioragardens.veliorasuite.module.adminmonitor;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.core.gui.GuiLayout;
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
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.block.BlockBreakEvent;
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
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.Map;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.Location;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Animals;

/** A deliberately small, confirmation-first moderation screen. It never fetches Mojang profiles. */
public final class AdminManagerGui implements Listener {
    // The central 7 x 4 grid keeps player heads visually tidy, with a fixed
    // frame and controls that never move when the number of players changes.
    private static final int PAGE_SIZE = 28;
    private static final int[] PLAYER_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };
    private final VelioraSuite plugin;
    private final AdminMonitorManager monitor;
    private final NamespacedKey actionKey;
    private final NamespacedKey targetKey;
    private final Set<UUID> frozen = ConcurrentHashMap.newKeySet();
    private final Set<UUID> vanished = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> mutedUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Location> backs = new HashMap<>();
    private final Map<UUID, List<String>> moderationHistory = new HashMap<>();
    private final File stateFile;
    private final AtomicBoolean stateWriteRunning = new AtomicBoolean();
    private final AtomicReference<String> pendingStateSnapshot = new AtomicReference<>();
    private volatile boolean globalChatMuted;

    public AdminManagerGui(VelioraSuite plugin, AdminMonitorManager monitor) {
        this.plugin = plugin;
        this.monitor = monitor;
        this.actionKey = new NamespacedKey(plugin, "adminmanager_action");
        this.targetKey = new NamespacedKey(plugin, "adminmanager_target");
        this.stateFile = new File(plugin.getDataFolder(), "data/admin-manager.yml");
        loadState();
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
        prepareMenu(inventory, "&8Admin Manager");
        int start = page * PAGE_SIZE;
        for (int index = start; index < Math.min(players.size(), start + PAGE_SIZE); index++) {
            OfflinePlayer target = players.get(index);
            boolean online = target.isOnline();
            String name = target.getName();
            List<String> lore = new ArrayList<>();
            lore.add(online ? "&a● Online sekarang" : "&7● Offline");
            lore.add("&7Terakhir terlihat: &f" + timeAgo(target.getLastPlayed()));
            lore.add("&7Klik untuk buka tindakan dan profil.");
            inventory.setItem(PLAYER_SLOTS[index - start], skull(target, (online ? "&a" : "&7") + name, lore, "profile", name));
        }
        inventory.setItem(45, item(Material.ARROW, "&e← Halaman sebelumnya", List.of("&7Klik untuk kembali."), page > 0 ? "previous" : null, null));
        inventory.setItem(47, item(Material.RECOVERY_COMPASS, "&bTeleport Kembali", List.of("&7Kembali ke lokasi sebelum teleport", "&7atau lokasi kematian terakhir."), "backtp", null));
        inventory.setItem(49, item(Material.REDSTONE, "&cKontrol Server", List.of("&7Chat, cuaca, waktu, difficulty", "&7dan bersihkan mob dengan konfirmasi."), "server", null));
        inventory.setItem(51, item(Material.ENDER_EYE, vanished.contains(viewer.getUniqueId()) ? "&aVanish aktif" : "&eVanish diri", List.of("&7Menyembunyikan dirimu dari pemain biasa.", "&7Chat dimatikan saat vanish aktif."), "vanish", null));
        inventory.setItem(53, item(Material.ARROW, "&eHalaman berikutnya →", List.of("&7Klik untuk lanjut."), page + 1 < pages ? "next" : null, null));
        inventory.setItem(4, item(Material.BOOK, "&bDaftar Pemain", List.of("&7Pilih kepala pemain untuk membuka", "&7profil, moderasi, dan tindakan staf.", "&8Halaman " + (page + 1) + " dari " + pages), null, null));
        viewer.openInventory(inventory);
    }

    private void openProfile(Player viewer, String targetName) {
        OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(targetName);
        if (target == null) target = Bukkit.getOfflinePlayer(targetName);
        MenuHolder holder = new MenuHolder("profile", targetName, 0);
        Inventory inventory = Bukkit.createInventory(holder, 54, color("&8Kelola: &f" + targetName));
        holder.inventory = inventory;
        prepareMenu(inventory, "&8Kelola pemain");
        long played = 0;
        try { played = target.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20L; } catch (Exception ignored) { }
        List<String> info = List.of(
                target.isOnline() ? "&aSedang online" : "&7Sedang offline",
                "&7Terakhir join: &f" + timeAgo(target.getLastPlayed()),
                "&7Pertama join: &f" + timeAgo(target.getFirstPlayed()),
                "&7Playtime: &f" + formatSeconds(played),
                "&7UUID: &8" + target.getUniqueId());
        inventory.setItem(4, skull(target, "&f" + targetName, info, null, null));
        inventory.setItem(1, item(Material.COMPASS, "&bI. Navigasi & Inspeksi", List.of("&7Baris kedua: teleport dan informasi target."), null, null));

        // Posisi tetap per kategori. Tidak ada pane dekorasi karena resource pack
        // dapat mengubah pane menjadi model acak dan membuat GUI membingungkan.
        inventory.setItem(10, item(Material.ENDER_PEARL, "&bTeleport ke pemain", List.of("&7Hanya jika pemain sedang online."), "teleport", targetName));
        inventory.setItem(12, item(Material.CHORUS_FRUIT, "&bTarik pemain ke kamu", List.of("&7Memindahkan target online ke lokasi kamu.", "&cButuh konfirmasi."), "confirm_tohere", targetName));
        inventory.setItem(14, item(Material.COMPASS, "&bTeleport ke Spawn", List.of("&7Teleport dirimu ke spawn world target."), "spawn", targetName));
        inventory.setItem(16, item(Material.SPYGLASS, "&bInspeksi pemain", List.of("&7HP, food, gamemode, lokasi."), "inspect", targetName));
        inventory.setItem(19, item(Material.CHEST, "&eEdit inventory live", List.of("&7Membuka salinan aman, lalu perubahan", "&7disalin saat GUI ditutup."), "inventory", targetName));
        inventory.setItem(21, item(Material.ENDER_CHEST, "&5Ender Chest", List.of("&7Klik kiri: lihat saja", "&7Shift+klik: edit live aman"), "ender", targetName));
        inventory.setItem(23, item(Material.PACKED_ICE, frozen.contains(target.getUniqueId()) ? "&aLepaskan freeze" : "&eFreeze pemain", List.of("&7Mencegah berjalan, menghancurkan blok", "&7dan damage jatuh."), "freeze", targetName));
        inventory.setItem(25, item(Material.BOOK, "&bRiwayat Moderasi", List.of("&7Lihat maksimal 15 tindakan terakhir.", "&7Tersimpan setelah server direstart."), "history", targetName));
        inventory.setItem(28, item(Material.LEATHER_BOOTS, "&eKick", List.of("&7Pilih alasan, lalu konfirmasi."), "choose_kick", targetName));
        inventory.setItem(29, item(Material.BARRIER, "&cBan", List.of("&7Pilih alasan, lalu konfirmasi."), "choose_ban", targetName));
        inventory.setItem(30, item(Material.CLOCK, "&6Tempban", List.of("&7Pilih alasan dan durasi.", "&cButuh konfirmasi."), "choose_tempban", targetName));
        inventory.setItem(31, item(Material.LIME_DYE, "&aUnban", List.of("&7Menghapus ban nama pemain.", "&cButuh konfirmasi."), "confirm_unban", targetName));
        inventory.setItem(32, item(Material.GRAY_DYE, "&eMute", List.of("&7Pilih alasan, lalu konfirmasi."), "choose_mute", targetName));
        inventory.setItem(33, item(Material.CLOCK, "&6Tempmute", List.of("&7Pilih alasan dan durasi.", "&cButuh konfirmasi."), "choose_tempmute", targetName));
        inventory.setItem(34, item(Material.LIME_DYE, "&aUnmute", List.of("&7Membuka akses chat target.", "&cButuh konfirmasi."), "confirm_unmute", targetName));
        inventory.setItem(40, item(Material.PAPER, "&eWarn", List.of("&7Pilih alasan peringatan."), "choose_warn", targetName));
        inventory.setItem(49, item(Material.ARROW, "&eKembali ke daftar", List.of("&7Tidak ada perubahan."), "back", null));
        viewer.openInventory(inventory);
    }

    private void openConfirm(Player viewer, String target, String action) {
        MenuHolder holder = new MenuHolder("confirm:" + action, target, 0);
        Inventory inventory = Bukkit.createInventory(holder, 27, color("&4Konfirmasi tindakan"));
        holder.inventory = inventory;
        prepareMenu(inventory, "&4Konfirmasi tindakan");
        inventory.setItem(11, item(Material.LIME_WOOL, "&aYa, lanjutkan", List.of("&7Tindakan: &f" + displayAction(action), "&7Target: &f" + target), "execute_" + action, target));
        inventory.setItem(15, item(Material.RED_WOOL, "&cBatal", List.of("&7Kembali tanpa perubahan."), "profile", target));
        viewer.openInventory(inventory);
    }

    private void openReasonPicker(Player viewer, String target, String kind) {
        MenuHolder holder = new MenuHolder("reason:" + kind, target, 0);
        Inventory inventory = Bukkit.createInventory(holder, 27, color("&8Pilih alasan: &f" + displayAction(kind)));
        holder.inventory = inventory;
        prepareMenu(inventory, "&8Pilih alasan");
        String[] reasons = {"CHEAT", "XRAY", "BUG_ABUSE", "MOD_ABUSE", "RUSUH", "SPAM", "STAFF"};
        String[] labels = {"&cCheat", "&6X-Ray", "&eMenyalahgunakan Bug", "&dMenyalahgunakan Mod", "&4Merusuh", "&bSpam", "&7Keputusan Staf"};
        Material[] icons = {Material.DIAMOND_SWORD, Material.DEEPSLATE_DIAMOND_ORE, Material.TRIPWIRE_HOOK, Material.REDSTONE, Material.TNT, Material.WRITABLE_BOOK, Material.PAPER};
        for (int index = 0; index < reasons.length; index++) {
            inventory.setItem(10 + index, item(icons[index], labels[index], List.of("&7Gunakan alasan ini.", "&7Tahap berikutnya tetap perlu konfirmasi."), "reason:" + kind + ":" + reasons[index], target));
        }
        inventory.setItem(22, item(Material.ARROW, "&eKembali", List.of("&7Kembali ke profil pemain."), "profile", target));
        viewer.openInventory(inventory);
    }

    private void openDurationPicker(Player viewer, String target, String kind, String reason) {
        MenuHolder holder = new MenuHolder("duration:" + kind, target, 0);
        Inventory inventory = Bukkit.createInventory(holder, 27, color("&8Pilih durasi: &f" + displayAction(kind)));
        holder.inventory = inventory;
        prepareMenu(inventory, "&8Pilih durasi");
        inventory.setItem(11, item(Material.CLOCK, "&e1 Jam", List.of("&7Alasan: &f" + reasonText(reason), "&7Lanjut ke konfirmasi akhir."), "confirm_" + kind + ":" + reason + ":1h", target));
        inventory.setItem(13, item(Material.CLOCK, "&61 Hari", List.of("&7Alasan: &f" + reasonText(reason), "&7Lanjut ke konfirmasi akhir."), "confirm_" + kind + ":" + reason + ":1d", target));
        inventory.setItem(15, item(Material.CLOCK, "&c7 Hari", List.of("&7Alasan: &f" + reasonText(reason), "&7Lanjut ke konfirmasi akhir."), "confirm_" + kind + ":" + reason + ":7d", target));
        inventory.setItem(22, item(Material.ARROW, "&eKembali", List.of("&7Pilih alasan lagi."), "choose_" + kind, target));
        viewer.openInventory(inventory);
    }

    private void openHistory(Player viewer, String targetName) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        List<String> entries = moderationHistory.getOrDefault(target.getUniqueId(), List.of());
        MenuHolder holder = new MenuHolder("history", targetName, 0);
        Inventory inventory = Bukkit.createInventory(holder, 54, color("&8Riwayat: &f" + targetName));
        holder.inventory = inventory;
        prepareMenu(inventory, "&8Riwayat Moderasi");
        if (entries.isEmpty()) {
            inventory.setItem(13, item(Material.PAPER, "&7Belum ada riwayat", List.of("&7Tindakan baru akan tercatat di sini."), null, null));
        } else {
            int[] historySlots = {10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34};
            int slot = 0;
            for (String entry : entries) {
                if (slot >= historySlots.length) break;
                inventory.setItem(historySlots[slot++], item(Material.WRITTEN_BOOK, "&eTindakan staf", List.of("&7" + entry), null, null));
            }
        }
        inventory.setItem(49, item(Material.ARROW, "&eKembali", List.of("&7Kembali ke profil pemain."), "profile", targetName));
        viewer.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player viewer)) return;
        if (!(event.getView().getTopInventory().getHolder() instanceof MenuHolder holder)) return;
        if (holder.type.startsWith("editor:")) {
            handleEditorClick(event, viewer, holder);
            return;
        }
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
        if (action.equals("history")) { if (target != null) openHistory(viewer, target); return; }
        if (action.equals("back")) { openPlayers(viewer, 0); return; }
        if (!monitor.canManage(viewer)) {
            viewer.sendMessage(color("&cKamu hanya memiliki izin melihat Admin Manager."));
            return;
        }
        if (action.equals("server")) { openServerControls(viewer); return; }
        if (action.equals("vanish")) { toggleVanish(viewer); openPlayers(viewer, holder.page); return; }
        if (action.equals("backtp")) { Location back=backs.get(viewer.getUniqueId()); if(back==null) viewer.sendMessage(color("&cBelum ada lokasi kembali.")); else viewer.teleport(back); return; }
        if (action.equals("server_back")) { openPlayers(viewer, 0); return; }
        if (action.equals("chatmute")) { globalChatMuted = !globalChatMuted; saveState(); viewer.sendMessage(color("&aGlobal chat: &f" + (globalChatMuted ? "dimute" : "dibuka"))); openServerControls(viewer); return; }
        if (action.equals("silentclear")) { for (Player p : Bukkit.getOnlinePlayers()) for (int i=0;i<120;i++) p.sendMessage(" "); viewer.sendMessage(color("&aChat dibersihkan.")); return; }
        if (action.equals("day")) { viewer.getWorld().setTime(1000); viewer.sendMessage(color("&aWaktu diubah ke siang.")); return; }
        if (action.equals("night")) { viewer.getWorld().setTime(13000); viewer.sendMessage(color("&aWaktu diubah ke malam.")); return; }
        if (action.equals("clearweather")) { viewer.getWorld().setStorm(false); viewer.getWorld().setThundering(false); viewer.sendMessage(color("&aCuaca dibersihkan.")); return; }
        if (action.equals("storm")) { viewer.getWorld().setStorm(true); viewer.sendMessage(color("&aHujan diaktifkan.")); return; }
        if (action.equals("easy")) { viewer.getWorld().setDifficulty(org.bukkit.Difficulty.EASY); viewer.sendMessage(color("&aDifficulty diubah ke Easy.")); return; }
        if (action.equals("normal")) { viewer.getWorld().setDifficulty(org.bukkit.Difficulty.NORMAL); viewer.sendMessage(color("&aDifficulty diubah ke Normal.")); return; }
        if (action.equals("hard")) { viewer.getWorld().setDifficulty(org.bukkit.Difficulty.HARD); viewer.sendMessage(color("&aDifficulty diubah ke Hard.")); return; }
        if (action.equals("confirm_killall") || action.equals("confirm_killhostile") || action.equals("confirm_killpassive")) { openConfirm(viewer, "server", action.substring("confirm_".length())); return; }
        if (target == null) return;
        if (action.startsWith("choose_")) { openReasonPicker(viewer, target, action.substring("choose_".length())); return; }
        if (action.startsWith("reason:")) {
            String[] parts = action.split(":", 3);
            if (parts.length == 3) {
                if (parts[1].equals("tempban") || parts[1].equals("tempmute")) openDurationPicker(viewer, target, parts[1], parts[2]);
                else openConfirm(viewer, target, parts[1] + ":" + parts[2]);
            }
            return;
        }
        if (action.startsWith("confirm_")) { openConfirm(viewer, target, action.substring("confirm_".length())); return; }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(target);
        if (action.equals("teleport")) {
            Player online = Bukkit.getPlayerExact(target);
            if (online == null) viewer.sendMessage(color("&cPemain sedang tidak online.")); else { backs.put(viewer.getUniqueId(), viewer.getLocation().clone()); viewer.teleport(online); }
            return;
        }
        if (action.equals("spawn")) { Player online=Bukkit.getPlayerExact(target); if(online==null){viewer.sendMessage(color("&cPemain sedang tidak online."));}else {backs.put(viewer.getUniqueId(),viewer.getLocation().clone());viewer.teleport(online.getWorld().getSpawnLocation());} return; }
        if (action.equals("inventory")) { openInventoryEditor(viewer, target, false, true); return; }
        if (action.equals("ender")) { openInventoryEditor(viewer, target, true, event.isShiftClick()); return; }
        if (action.equals("inspect")) { inspect(viewer, target); return; }
        if (action.equals("freeze")) {
            if (!offline.isOnline()) { viewer.sendMessage(color("&cFreeze hanya dapat dilakukan pada pemain online.")); return; }
            if (!frozen.add(offline.getUniqueId())) frozen.remove(offline.getUniqueId());
            viewer.sendMessage(color("&aStatus freeze " + target + ": &f" + (frozen.contains(offline.getUniqueId()) ? "aktif" : "nonaktif")));
            openProfile(viewer, target);
            return;
        }
        if (!action.startsWith("execute_")) return;
        String execute = action.substring("execute_".length());
        String[] actionParts=execute.split(":",3); String kind=actionParts[0]; String reason=actionParts.length>=2?actionParts[1]:"STAFF"; String duration=actionParts.length>=3?actionParts[2]:"";
        String readableReason=reasonText(reason);
        switch (kind) {
            case "kick" -> { Player online = Bukkit.getPlayerExact(target); if (online != null) online.kickPlayer(color("&cDikeluarkan oleh staf.")); }
            case "ban" -> Bukkit.getBanList(BanList.Type.NAME).addBan(target, readableReason, null, viewer.getName());
            case "tempban" -> Bukkit.getBanList(BanList.Type.NAME).addBan(target, readableReason, new Date(System.currentTimeMillis() + durationMillis(duration)), viewer.getName());
            case "unban" -> Bukkit.getBanList(BanList.Type.NAME).pardon(target);
            case "tohere" -> { Player online=Bukkit.getPlayerExact(target); if(online==null){viewer.sendMessage(color("&cPemain sedang tidak online."));return;} backs.put(online.getUniqueId(),online.getLocation().clone()); online.teleport(viewer.getLocation()); }
            case "mute" -> { mutedUntil.put(offline.getUniqueId(), Long.MAX_VALUE); saveState(); }
            case "tempmute" -> { mutedUntil.put(offline.getUniqueId(), System.currentTimeMillis()+durationMillis(duration)); saveState(); }
            case "unmute" -> { mutedUntil.remove(offline.getUniqueId()); saveState(); }
            case "warn" -> { Player online=Bukkit.getPlayerExact(target); if(online!=null) online.sendMessage(color("&c[Peringatan] &f"+readableReason)); }
            case "killall", "killhostile", "killpassive" -> { int removed=killMobs(viewer.getWorld(),execute); viewer.sendMessage(color("&aBerhasil membersihkan &f"+removed+" &amob dari world ini.")); openServerControls(viewer); return; }
            default -> { return; }
        }
        Player online = Bukkit.getPlayerExact(target);
        if ((kind.equals("ban") || kind.equals("tempban")) && online != null) online.kickPlayer(color("&cAkun kamu telah diblokir oleh staf.\n&7Alasan: &f"+readableReason));
        recordModeration(offline.getUniqueId(), viewer.getName(), kind, readableReason, duration);
        viewer.sendMessage(color("&aTindakan berhasil: &f" + displayAction(kind) + " &ake " + target + " &7("+readableReason+")."));
        monitor.command(viewer, "/adminmanager " + kind + " " + target + " " + reason);
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

    @EventHandler(ignoreCancelled = true) public void onBreak(BlockBreakEvent event) { if (frozen.contains(event.getPlayer().getUniqueId())) { event.setCancelled(true); event.getPlayer().sendActionBar(net.kyori.adventure.text.Component.text("Kamu sedang di-freeze oleh staf.")); } }
    @EventHandler(ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String[] parts = event.getMessage().substring(1).trim().split("\\s+");
        if (parts.length < 2) return;
        String root = parts[0].toLowerCase(java.util.Locale.ROOT);
        if (!Set.of("msg", "tell", "w", "whisper").contains(root)) return;
        Player target = Bukkit.getPlayerExact(parts[1]);
        if (target != null && vanished.contains(target.getUniqueId()) && !event.getPlayer().hasPermission("veliorasuite.adminmonitor.admin")) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(color("&cPemain tersebut tidak ditemukan."));
        }
    }
    @EventHandler(ignoreCancelled = true) public void onChat(AsyncPlayerChatEvent event) {
        UUID id=event.getPlayer().getUniqueId(); long until=mutedUntil.getOrDefault(id,0L);
        if(until>0 && (until==Long.MAX_VALUE || until>System.currentTimeMillis())) {event.setCancelled(true);event.getPlayer().sendMessage(color("&cKamu sedang dimute oleh staf."));return;}
        if(until>0) {
            mutedUntil.remove(id);
            // AsyncPlayerChatEvent may run outside the server thread. Building a
            // YAML snapshot here would race normal GUI actions, so schedule it.
            Bukkit.getScheduler().runTask(plugin, this::saveState);
        }
        if(vanished.contains(id)) {event.setCancelled(true);event.getPlayer().sendMessage(color("&7Chat dimatikan saat vanish aktif. Command tetap boleh."));return;}
        if(globalChatMuted && !event.getPlayer().hasPermission("veliorasuite.adminmonitor.admin")){event.setCancelled(true);event.getPlayer().sendMessage(color("&cGlobal chat sedang dimute."));}
    }
    @EventHandler public void onJoin(PlayerJoinEvent event) {
        for(Player hidden:Bukkit.getOnlinePlayers()) if(vanished.contains(hidden.getUniqueId())) event.getPlayer().hidePlayer(plugin,hidden);
        if(vanished.contains(event.getPlayer().getUniqueId())) {
            event.joinMessage(null);
            for(Player other:Bukkit.getOnlinePlayers()) if(!other.hasPermission("veliorasuite.adminmonitor.admin")) other.hidePlayer(plugin,event.getPlayer());
        }
    }
    @EventHandler public void onQuit(PlayerQuitEvent event) { if(vanished.contains(event.getPlayer().getUniqueId())) event.quitMessage(null); frozen.remove(event.getPlayer().getUniqueId()); backs.remove(event.getPlayer().getUniqueId()); }
    @EventHandler public void onDeath(PlayerDeathEvent event) { backs.put(event.getPlayer().getUniqueId(),event.getPlayer().getLocation().clone()); }

    @EventHandler public void onEditorClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof MenuHolder holder) || !holder.type.startsWith("editor:")) return;
        if (!holder.type.endsWith(":edit")) return;
        Player target = Bukkit.getPlayerExact(holder.target); if (target == null) return;
        Inventory edited = event.getInventory();
        if (holder.type.startsWith("editor:ender")) {
            for (int i=0;i<27;i++) target.getEnderChest().setItem(i, edited.getItem(i));
        } else {
            for (int i=0;i<36;i++) target.getInventory().setItem(i, edited.getItem(i));
            target.getInventory().setHelmet(edited.getItem(36)); target.getInventory().setChestplate(edited.getItem(37));
            target.getInventory().setLeggings(edited.getItem(38)); target.getInventory().setBoots(edited.getItem(39)); target.getInventory().setItemInOffHand(edited.getItem(40));
        }
        event.getPlayer().sendMessage(color("&aPerubahan inventory " + target.getName() + " disimpan live."));
    }

    /** Drag can bypass InventoryClickEvent, so every custom Admin Manager menu blocks it. */
    @EventHandler public void onMenuDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof MenuHolder) event.setCancelled(true);
    }

    /**
     * Safe editor semantics: an admin can replace a target slot with an item from
     * their cursor, or right-click an empty cursor to delete. Target items are
     * never transferred into the viewer's inventory, so this cannot become a
     * staff item-stealing GUI.
     */
    private void handleEditorClick(InventoryClickEvent event, Player viewer, MenuHolder holder) {
        event.setCancelled(true);
        if (!monitor.canManage(viewer)) {
            viewer.sendMessage(color("&cKamu tidak memiliki izin mengedit inventory pemain."));
            return;
        }
        if (holder.type.endsWith(":view") || event.getClickedInventory() != event.getView().getTopInventory()) return;
        int editableSlots = holder.type.startsWith("editor:ender") ? 27 : 41;
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= editableSlots || event.isShiftClick() || event.getHotbarButton() >= 0) return;
        ItemStack cursor = event.getCursor();
        if (cursor == null || cursor.getType().isAir()) {
            if (event.isRightClick()) {
                event.getView().getTopInventory().setItem(slot, null);
                viewer.sendActionBar(net.kyori.adventure.text.Component.text("Slot target dikosongkan."));
            } else {
                ItemStack held = viewer.getInventory().getItemInMainHand();
                if (held == null || held.getType().isAir()) {
                    viewer.sendActionBar(net.kyori.adventure.text.Component.text("Pegang item di tangan lalu klik slot; klik kanan kosong untuk menghapus."));
                    return;
                }
                event.getView().getTopInventory().setItem(slot, held.clone());
                viewer.getInventory().setItemInMainHand(null);
                viewer.sendActionBar(net.kyori.adventure.text.Component.text("Item dari tangan dipindahkan ke slot target."));
            }
            return;
        }
        ItemStack replacement = cursor.clone();
        if (event.isRightClick()) replacement.setAmount(1);
        event.getView().getTopInventory().setItem(slot, replacement);
        ItemStack remaining = cursor.clone();
        remaining.setAmount(cursor.getAmount() - replacement.getAmount());
        viewer.setItemOnCursor(remaining.getAmount() <= 0 ? null : remaining);
        viewer.sendActionBar(net.kyori.adventure.text.Component.text("Item target diganti secara aman."));
    }

    private void openServerControls(Player viewer) {
        MenuHolder holder = new MenuHolder("server", null, 0);
        Inventory inv = Bukkit.createInventory(holder, 54, color("&8Admin Manager &7| Server"));
        holder.inventory = inv;
        prepareMenu(inv, "&8Kontrol Server");
        inv.setItem(4, item(Material.COMPARATOR, "&bKontrol Server", List.of("&7Susunan: chat • world • difficulty • pembersihan.", "&7Semua tindakan berisiko meminta konfirmasi."), null, null));
        inv.setItem(10, item(Material.PAPER, globalChatMuted ? "&cBuka Global Chat" : "&eMute Global Chat", List.of("&7Status: " + (globalChatMuted ? "&cDimute" : "&aAktif"), "&7Klik untuk mengubah status."), "chatmute", null));
        inv.setItem(12, item(Material.FEATHER, "&fBersihkan Chat", List.of("&7Mengirim baris kosong tanpa broadcast."), "silentclear", null));
        inv.setItem(14, item(Material.SUNFLOWER, "&eWaktu Siang", List.of("&7Mengubah waktu world kamu ke siang."), "day", null));
        inv.setItem(16, item(Material.CLOCK, "&9Waktu Malam", List.of("&7Mengubah waktu world kamu ke malam."), "night", null));
        inv.setItem(19, item(Material.WATER_BUCKET, "&bCuaca Cerah", List.of("&7Menghentikan hujan dan petir."), "clearweather", null));
        inv.setItem(21, item(Material.LIGHTNING_ROD, "&7Hujan", List.of("&7Mengaktifkan hujan di world kamu."), "storm", null));
        inv.setItem(23, item(Material.LIME_DYE, "&aDifficulty Easy", List.of("&7Atur difficulty world ini."), "easy", null));
        inv.setItem(25, item(Material.YELLOW_DYE, "&eDifficulty Normal", List.of("&7Atur difficulty world ini."), "normal", null));
        inv.setItem(27, item(Material.RED_DYE, "&cDifficulty Hard", List.of("&7Atur difficulty world ini."), "hard", null));
        inv.setItem(31, item(Material.COMPARATOR, "&bStatus World", List.of("&7Entity world ini: &f" + viewer.getWorld().getEntities().size(), "&7Player online: &f" + Bukkit.getOnlinePlayers().size(), "&7Tidak melakukan scan berulang."), null, null));
        inv.setItem(37, item(Material.TNT, "&cBersihkan Semua Mob", List.of("&7Hanya world saat ini.", "&cButuh konfirmasi."), "confirm_killall", null));
        inv.setItem(40, item(Material.IRON_SWORD, "&6Bersihkan Mob Hostile", List.of("&7Hanya monster, bukan pet/player.", "&cButuh konfirmasi."), "confirm_killhostile", null));
        inv.setItem(43, item(Material.WHEAT, "&aBersihkan Mob Passive", List.of("&7Hanya hewan, bukan player.", "&cButuh konfirmasi."), "confirm_killpassive", null));
        inv.setItem(49, item(Material.ARROW, "&eKembali ke Daftar Pemain", List.of("&7Tidak ada perubahan."), "server_back", null));
        viewer.openInventory(inv);
    }
    private void toggleVanish(Player player) {
        boolean enabled=!vanished.contains(player.getUniqueId());
        if(enabled){
            vanished.add(player.getUniqueId());
            for(Player other:Bukkit.getOnlinePlayers()) if(!other.hasPermission("veliorasuite.adminmonitor.admin")) { other.hidePlayer(plugin,player); other.sendMessage(color("&e"+player.getName()+" &7keluar dari server.")); }
            player.sendActionBar(net.kyori.adventure.text.Component.text("Vanish aktif — kamu terlihat keluar bagi pemain biasa."));
        } else {
            vanished.remove(player.getUniqueId());
            for(Player other:Bukkit.getOnlinePlayers()) if(!other.hasPermission("veliorasuite.adminmonitor.admin")) { other.showPlayer(plugin,player); other.sendMessage(color("&e"+player.getName()+" &7bergabung ke server.")); }
            player.sendActionBar(net.kyori.adventure.text.Component.text("Vanish dinonaktifkan."));
        }
        saveState();
    }

    private void loadState() {
        YamlConfiguration yaml=YamlConfiguration.loadConfiguration(stateFile);
        globalChatMuted = yaml.getBoolean("global-chat-muted", false);
        for(String raw:yaml.getStringList("vanished")) try { vanished.add(UUID.fromString(raw)); } catch(IllegalArgumentException ignored) { }
        org.bukkit.configuration.ConfigurationSection muted=yaml.getConfigurationSection("muted");
        if(muted!=null) for(String raw:muted.getKeys(false)) try { long until=muted.getLong(raw); if(until==Long.MAX_VALUE||until>System.currentTimeMillis()) mutedUntil.put(UUID.fromString(raw),until); } catch(IllegalArgumentException ignored) { }
        org.bukkit.configuration.ConfigurationSection history=yaml.getConfigurationSection("history");
        if(history!=null) for(String raw:history.getKeys(false)) try { moderationHistory.put(UUID.fromString(raw), new ArrayList<>(history.getStringList(raw))); } catch(IllegalArgumentException ignored) { }
    }
    /** Queues a complete state snapshot; disk I/O itself never runs on the server thread. */
    private void saveState() {
        pendingStateSnapshot.set(createStateSnapshot());
        flushStateAsync();
    }

    private String createStateSnapshot() {
        YamlConfiguration yaml=new YamlConfiguration(); yaml.set("global-chat-muted",globalChatMuted); yaml.set("vanished",vanished.stream().map(UUID::toString).toList());
        for(Map.Entry<UUID,Long> entry:mutedUntil.entrySet()) yaml.set("muted."+entry.getKey(),entry.getValue());
        for(Map.Entry<UUID,List<String>> entry:moderationHistory.entrySet()) yaml.set("history."+entry.getKey(),entry.getValue());
        return yaml.saveToString();
    }

    private void flushStateAsync() {
        if (!stateWriteRunning.compareAndSet(false, true)) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String snapshot = pendingStateSnapshot.getAndSet(null);
            if (snapshot != null) {
                try {
                    File parent = stateFile.getParentFile();
                    if (parent != null) Files.createDirectories(parent.toPath());
                    Files.writeString(stateFile.toPath(), snapshot, StandardCharsets.UTF_8);
                } catch (IOException exception) {
                    plugin.getLogger().warning("AdminManager: gagal menyimpan mute/vanish: " + exception.getMessage());
                    pendingStateSnapshot.compareAndSet(null, snapshot);
                }
            }
            stateWriteRunning.set(false);
            if (pendingStateSnapshot.get() != null) Bukkit.getScheduler().runTask(plugin, this::flushStateAsync);
        });
    }

    /** Final synchronous save is only used during plugin shutdown, after gameplay has stopped. */
    public void shutdown() {
        String snapshot = createStateSnapshot();
        pendingStateSnapshot.set(null);
        try {
            File parent = stateFile.getParentFile();
            if (parent != null) Files.createDirectories(parent.toPath());
            Files.writeString(stateFile.toPath(), snapshot, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            plugin.getLogger().warning("AdminManager: gagal menyimpan state saat shutdown: " + exception.getMessage());
        }
    }

    private void recordModeration(UUID target, String staff, String action, String reason, String duration) {
        if (!Set.of("kick", "ban", "tempban", "mute", "tempmute", "warn", "unban", "unmute").contains(action)) return;
        String durationLabel = duration.isBlank() ? "" : " (" + durationLabel(duration) + ")";
        List<String> entries = moderationHistory.computeIfAbsent(target, ignored -> new ArrayList<>());
        entries.add(0, new java.text.SimpleDateFormat("dd/MM HH:mm").format(new Date()) + " | " + displayAction(action) + durationLabel + " | " + reason + " | oleh " + staff);
        if (entries.size() > 15) entries.subList(15, entries.size()).clear();
        saveState();
    }
    private void openInventoryEditor(Player viewer,String targetName,boolean ender,boolean edit) {
        Player target=Bukkit.getPlayerExact(targetName); if(target==null){viewer.sendMessage(color("&cInventory hanya dapat dibuka saat target online."));return;}
        int size=ender?27:54; String kind=ender?"ender":"inventory"; MenuHolder holder=new MenuHolder("editor:"+kind+":"+(edit?"edit":"view"),targetName,0); Inventory inv=Bukkit.createInventory(holder,size,color("&8"+(ender?"Ender Chest: ":"Inventory: ")+"&f"+targetName));holder.inventory=inv;
        if(ender) for(int i=0;i<27;i++)inv.setItem(i,target.getEnderChest().getItem(i));
        else { for(int i=0;i<36;i++)inv.setItem(i,target.getInventory().getItem(i)); inv.setItem(36,target.getInventory().getHelmet());inv.setItem(37,target.getInventory().getChestplate());inv.setItem(38,target.getInventory().getLeggings());inv.setItem(39,target.getInventory().getBoots());inv.setItem(40,target.getInventory().getItemInOffHand()); for(int i=41;i<54;i++)inv.setItem(i,item(Material.BLACK_STAINED_GLASS_PANE,"&8Slot terkunci",List.of("&7Bukan bagian dari inventory target."),null,null)); }
        viewer.openInventory(inv);
    }
    private void inspect(Player viewer,String targetName) { Player target=Bukkit.getPlayerExact(targetName); if(target==null){viewer.sendMessage(color("&cPemain sedang offline."));return;} Location l=target.getLocation(); viewer.sendMessage(color("&8[&bInspeksi&8] &f"+target.getName()+" &7HP: &c"+(int)target.getHealth()+"&7/&c"+(int)target.getMaxHealth()+" &7Food: &e"+target.getFoodLevel()+" &7Mode: &f"+target.getGameMode()+" &7Lokasi: &f"+l.getWorld().getName()+" "+l.getBlockX()+", "+l.getBlockY()+", "+l.getBlockZ())); }
    private int killMobs(World world,String type) { int removed=0; for(Entity e:new ArrayList<>(world.getEntities())) { boolean match=type.equals("killall")?(e instanceof Monster||e instanceof Animals):type.equals("killhostile")?e instanceof Monster:e instanceof Animals; if(match){e.remove();removed++;} } return removed; }

    private ItemStack skull(OfflinePlayer owner, String name, List<String> lore, String action, String target) {
        ItemStack stack = item(Material.PLAYER_HEAD, name, lore, action, target);
        SkullMeta skull = (SkullMeta) stack.getItemMeta();
        // Do not call setOwningPlayer for offline accounts. Paper may then ask Mojang to
        // resolve every texture in this 45-slot page, causing HTTP 429 and noisy logs.
        // An online profile already has its signed texture in memory, so it is safe to use.
        Player online = owner.getPlayer();
        if (online != null) skull.setOwnerProfile(online.getPlayerProfile());
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
    private void fillEmpty(Inventory inventory, Material material, String name) {
        for (int slot = 0; slot < inventory.getSize(); slot++) if (inventory.getItem(slot) == null) inventory.setItem(slot, item(material, name, List.of(), null, null));
    }
    private void prepareMenu(Inventory inventory, String label) {
        GuiLayout.decorateMenu(inventory);
    }
    private String displayAction(String action) {
        String kind = action.contains(":") ? action.substring(0, action.indexOf(':')) : action;
        return switch (kind) { case "kick" -> "Kick"; case "ban" -> "Ban"; case "tempban" -> "Tempban"; case "unban" -> "Unban"; case "mute" -> "Mute"; case "tempmute" -> "Tempmute"; case "unmute" -> "Unmute"; case "warn" -> "Peringatan"; case "tohere" -> "Tarik pemain"; case "killall" -> "Bersihkan semua mob"; case "killhostile" -> "Bersihkan mob hostile"; case "killpassive" -> "Bersihkan mob passive"; default -> kind; };
    }
    private String reasonText(String reason) {
        return switch (reason) { case "CHEAT" -> "Cheat"; case "XRAY" -> "X-Ray"; case "BUG_ABUSE" -> "Menyalahgunakan bug"; case "MOD_ABUSE" -> "Menyalahgunakan mod"; case "RUSUH" -> "Merusuh"; case "SPAM" -> "Spam"; default -> "Keputusan staf"; };
    }
    private long durationMillis(String duration) {
        return switch (duration) { case "1h" -> 60L * 60L * 1000L; case "7d" -> 7L * 24L * 60L * 60L * 1000L; default -> 24L * 60L * 60L * 1000L; };
    }
    private String durationLabel(String duration) { return switch (duration) { case "1h" -> "1 jam"; case "7d" -> "7 hari"; default -> "1 hari"; }; }
    private String color(String text) { return ChatColor.translateAlternateColorCodes('&', text); }
    private String timeAgo(long time) { return time <= 0 ? "belum ada data" : formatSeconds(Math.max(0, (System.currentTimeMillis() - time) / 1000L)) + " lalu"; }
    private String formatSeconds(long seconds) { long hours = seconds / 3600L; return hours + " jam " + ((seconds % 3600L) / 60L) + " menit"; }
    private static final class MenuHolder implements InventoryHolder {
        private final String type; private final String target; private final int page; private Inventory inventory;
        private MenuHolder(String type, String target, int page) { this.type = type; this.target = target; this.page = page; }
        @Override public Inventory getInventory() { return inventory; }
    }
}
