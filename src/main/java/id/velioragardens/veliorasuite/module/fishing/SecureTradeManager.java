package id.velioragardens.veliorasuite.module.fishing;

import id.velioragardens.veliorasuite.VelioraSuite;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class SecureTradeManager implements Listener, CommandExecutor, TabCompleter {

    private static final int[] LEFT = {10, 11, 12, 19, 20, 21, 28, 29, 30};
    private static final int[] RIGHT = {14, 15, 16, 23, 24, 25, 32, 33, 34};
    private static final Set<Integer> LEFT_SET = Set.of(10, 11, 12, 19, 20, 21, 28, 29, 30);
    private static final String TITLE_PREFIX = "§8Trade • ";

    private final VelioraSuite plugin;
    private final FishingManager manager;
    private final NamespacedKey boundRodOwner;
    private final Map<UUID, Request> requests = new HashMap<>();
    private final Map<UUID, TradeSession> sessions = new HashMap<>();
    private final Set<UUID> safeClosing = new java.util.HashSet<>();

    public SecureTradeManager(VelioraSuite plugin, FishingManager manager) {
        this.plugin = plugin;
        this.manager = manager;
        this.boundRodOwner = new NamespacedKey(plugin, "fishing_rod_owner");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { manager.sendPlayerOnly(sender); return true; }
        if (!player.hasPermission("veliorasuite.fishing.trade") && !player.isOp()) { manager.sendNoPermission(sender); return true; }
        if (!manager.getConfigManager().isTradeEnabled()) {
            message(player, "&eSistem trade sedang dimatikan.");
            return true;
        }
        if (args.length == 0) {
            message(player, "&7Gunakan &f/vtrading <player>&7, &faccept&7, &fdeny&7, atau &fcancel&7.");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "accept" -> accept(player);
            case "deny" -> deny(player);
            case "cancel" -> cancelCommand(player);
            default -> request(player, Bukkit.getPlayerExact(args[0]));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) return List.of();
        String input = args[0].toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>(List.of("accept", "deny", "cancel"));
        Bukkit.getOnlinePlayers().stream().filter(player -> !player.getName().equalsIgnoreCase(sender.getName()))
                .map(Player::getName).forEach(result::add);
        return result.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(input)).toList();
    }

    private void request(Player requester, Player target) {
        if (target == null || !target.isOnline()) { message(requester, "&cPlayer tidak ditemukan."); return; }
        if (requester.equals(target)) { message(requester, "&cKamu tidak bisa trade dengan diri sendiri."); return; }
        if (sessions.containsKey(requester.getUniqueId()) || sessions.containsKey(target.getUniqueId())) {
            message(requester, "&eSalah satu pemain sedang melakukan trade."); return;
        }
        if (!near(requester, target)) { message(requester, "&eKalian harus berada berdekatan untuk trade."); return; }
        long expires = System.currentTimeMillis() + manager.getConfigManager().getTradeRequestTimeoutSeconds() * 1000L;
        requests.put(target.getUniqueId(), new Request(requester.getUniqueId(), expires));
        message(requester, "&aPermintaan trade dikirim kepada &f" + target.getName() + "&a.");
        message(target, "&f" + requester.getName() + " &eingin trade. Gunakan &a/vtrading accept &eatau &c/vtrading deny&e.");
    }

    private void accept(Player target) {
        Request request = requests.remove(target.getUniqueId());
        if (request == null || request.expiresAt < System.currentTimeMillis()) { message(target, "&eTidak ada permintaan trade aktif."); return; }
        Player requester = Bukkit.getPlayer(request.requester);
        if (requester == null || !requester.isOnline() || !near(requester, target)) { message(target, "&cPeminta trade sudah pergi atau terlalu jauh."); return; }
        if (sessions.containsKey(requester.getUniqueId()) || sessions.containsKey(target.getUniqueId())) { message(target, "&eSalah satu pemain sedang trade."); return; }
        TradeSession session = new TradeSession(requester.getUniqueId(), target.getUniqueId());
        sessions.put(requester.getUniqueId(), session);
        sessions.put(target.getUniqueId(), session);
        open(session);
    }

    private void deny(Player player) {
        Request request = requests.remove(player.getUniqueId());
        if (request == null) { message(player, "&eTidak ada permintaan trade aktif."); return; }
        Player requester = Bukkit.getPlayer(request.requester);
        if (requester != null) message(requester, "&c" + player.getName() + " menolak permintaan trade.");
        message(player, "&ePermintaan trade ditolak.");
    }

    private void cancelCommand(Player player) {
        TradeSession session = sessions.get(player.getUniqueId());
        if (session != null) { cancel(session, "Trade dibatalkan oleh " + player.getName() + "."); return; }
        requests.entrySet().removeIf(entry -> entry.getKey().equals(player.getUniqueId()) || entry.getValue().requester.equals(player.getUniqueId()));
        message(player, "&ePermintaan trade dibatalkan.");
    }

    private void open(TradeSession session) {
        Player first = Bukkit.getPlayer(session.first);
        Player second = Bukkit.getPlayer(session.second);
        if (first == null || second == null) { cancel(session, "Trade dibatalkan."); return; }
        first.openInventory(Bukkit.createInventory(null, 54, TITLE_PREFIX + second.getName()));
        second.openInventory(Bukkit.createInventory(null, 54, TITLE_PREFIX + first.getName()));
        render(session);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !event.getView().getTitle().startsWith(TITLE_PREFIX)) return;
        TradeSession session = sessions.get(player.getUniqueId());
        if (session == null) { event.setCancelled(true); player.closeInventory(); return; }
        int raw = event.getRawSlot();
        if (raw >= 54) {
            if (session.locked || event.isShiftClick()) event.setCancelled(true);
            return;
        }
        event.setCancelled(true);
        if (session.locked) return;
        if (raw == 45) { cancel(session, "Trade dibatalkan oleh " + player.getName() + "."); return; }
        if (raw == 48) { setReady(session, player); return; }
        if (!LEFT_SET.contains(raw)) return;
        int index = indexOf(LEFT, raw);
        ItemStack cursor = event.getCursor();
        if (isBound(cursor)) { message(player, "&eFishing Rod terikat tidak dapat ditrade."); return; }
        ItemStack[] own = session.offer(player.getUniqueId());
        ItemStack old = own[index];
        own[index] = empty(cursor) ? null : cursor.clone();
        event.setCursor(old == null ? null : old.clone());
        resetReady(session);
        render(session);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTitle().startsWith(TITLE_PREFIX)) event.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player) || !event.getView().getTitle().startsWith(TITLE_PREFIX)) return;
        if (safeClosing.remove(player.getUniqueId())) return;
        TradeSession session = sessions.get(player.getUniqueId());
        if (session != null) Bukkit.getScheduler().runTask(plugin, () -> cancel(session, "Trade dibatalkan karena GUI ditutup."));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        TradeSession session = sessions.get(event.getPlayer().getUniqueId());
        if (session != null) cancel(session, "Trade dibatalkan karena pemain keluar.");
        requests.entrySet().removeIf(entry -> entry.getKey().equals(event.getPlayer().getUniqueId()) || entry.getValue().requester.equals(event.getPlayer().getUniqueId()));
    }

    public void shutdown() {
        for (TradeSession session : Set.copyOf(sessions.values())) cancel(session, "Trade dibatalkan karena plugin dimatikan.");
        requests.clear();
    }

    private void setReady(TradeSession session, Player player) {
        if (session.first.equals(player.getUniqueId())) session.firstReady = !session.firstReady;
        else session.secondReady = !session.secondReady;
        render(session);
        if (session.firstReady && session.secondReady) startCountdown(session);
    }

    private void startCountdown(TradeSession session) {
        session.locked = true;
        session.seconds = manager.getConfigManager().getTradeCountdownSeconds();
        render(session);
        session.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Player first = Bukkit.getPlayer(session.first);
            Player second = Bukkit.getPlayer(session.second);
            if (first == null || second == null) { cancel(session, "Trade dibatalkan karena pemain keluar."); return; }
            if (!near(first, second)) { cancel(session, "Trade dibatalkan karena pemain terlalu jauh."); return; }
            if (session.seconds <= 0) { complete(session); return; }
            first.sendActionBar("§aTrade terkunci §7• §f" + session.seconds + " detik");
            second.sendActionBar("§aTrade terkunci §7• §f" + session.seconds + " detik");
            first.playSound(first.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.8F, 1.1F);
            second.playSound(second.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.8F, 1.1F);
            session.seconds--;
        }, 0L, 20L);
    }

    private void complete(TradeSession session) {
        Player first = Bukkit.getPlayer(session.first);
        Player second = Bukkit.getPlayer(session.second);
        removeSession(session);
        if (first == null || second == null) { returnOffers(session); return; }
        give(second, session.firstOffer);
        give(first, session.secondOffer);
        closeSafely(first, second);
        message(first, "&aTrade dengan &f" + second.getName() + " &aberhasil.");
        message(second, "&aTrade dengan &f" + first.getName() + " &aberhasil.");
        first.playSound(first.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 1.25F);
        second.playSound(second.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 1.25F);
    }

    private void cancel(TradeSession session, String reason) {
        if (!sessions.containsValue(session)) return;
        removeSession(session);
        returnOffers(session);
        Player first = Bukkit.getPlayer(session.first);
        Player second = Bukkit.getPlayer(session.second);
        closeSafely(first, second);
        if (first != null) message(first, "&e" + reason);
        if (second != null) message(second, "&e" + reason);
    }

    private void returnOffers(TradeSession session) {
        Player first = Bukkit.getPlayer(session.first);
        Player second = Bukkit.getPlayer(session.second);
        if (first != null) give(first, session.firstOffer);
        if (second != null) give(second, session.secondOffer);
    }

    private void removeSession(TradeSession session) {
        if (session.task != null) session.task.cancel();
        sessions.remove(session.first);
        sessions.remove(session.second);
    }

    private void closeSafely(Player... players) {
        for (Player player : players) {
            if (player == null) continue;
            if (!player.getOpenInventory().getTitle().startsWith(TITLE_PREFIX)) continue;
            safeClosing.add(player.getUniqueId());
            player.closeInventory();
        }
    }

    private void render(TradeSession session) {
        renderFor(session, Bukkit.getPlayer(session.first), session.firstOffer, session.secondOffer, session.firstReady, session.secondReady);
        renderFor(session, Bukkit.getPlayer(session.second), session.secondOffer, session.firstOffer, session.secondReady, session.firstReady);
    }

    private void renderFor(TradeSession session, Player viewer, ItemStack[] own, ItemStack[] other, boolean ownReady, boolean otherReady) {
        if (viewer == null || !viewer.getOpenInventory().getTitle().startsWith(TITLE_PREFIX)) return;
        Inventory top = viewer.getOpenInventory().getTopInventory();
        for (int i = 0; i < LEFT.length; i++) {
            top.setItem(LEFT[i], own[i] == null ? null : own[i].clone());
            top.setItem(RIGHT[i], other[i] == null ? null : other[i].clone());
        }
        top.setItem(4, item(Material.PAPER, "§bTRADE AMAN", List.of("§7Item kamu di kiri.", "§7Item teman di kanan.")));
        top.setItem(45, item(Material.BARRIER, "§cBatalkan Trade", List.of("§7Semua item akan dikembalikan.")));
        top.setItem(48, item(ownReady ? Material.LIME_TERRACOTTA : Material.RED_TERRACOTTA,
                ownReady ? "§aKamu Siap" : "§cKlik untuk Siap", List.of("§7Perubahan item mereset status.")));
        top.setItem(50, item(otherReady ? Material.LIME_TERRACOTTA : Material.RED_TERRACOTTA,
                otherReady ? "§aTeman Siap" : "§cTeman Belum Siap", List.of("§7Tunggu persetujuan teman.")));
        if (session.locked) top.setItem(49, item(Material.CLOCK, "§eTrade Terkunci", List.of("§7Item tidak dapat diubah.", "§7Tunggu countdown selesai.")));
        else top.setItem(49, item(Material.GRAY_DYE, "§7Menunggu Persetujuan", List.of("§7Kedua pemain harus siap.")));
    }

    private void resetReady(TradeSession session) {
        if (session.task != null) session.task.cancel();
        session.task = null;
        session.locked = false;
        session.firstReady = false;
        session.secondReady = false;
    }

    private void give(Player player, ItemStack[] items) {
        for (ItemStack item : items) {
            if (empty(item)) continue;
            player.getInventory().addItem(item.clone()).values()
                    .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        }
    }

    private boolean near(Player first, Player second) {
        return first.getWorld().equals(second.getWorld())
                && first.getLocation().distanceSquared(second.getLocation()) <= Math.pow(manager.getConfigManager().getTradeMaxDistance(), 2);
    }

    private boolean isBound(ItemStack item) {
        return !empty(item) && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(boundRodOwner, PersistentDataType.STRING);
    }

    private boolean empty(ItemStack item) { return item == null || item.getType().isAir() || item.getAmount() <= 0; }
    private int indexOf(int[] slots, int slot) { for (int i = 0; i < slots.length; i++) if (slots[i] == slot) return i; return -1; }

    private ItemStack item(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private void message(Player player, String text) {
        player.sendMessage(manager.getConfigManager().color(manager.getConfigManager().getPrefix() + text));
    }

    private record Request(UUID requester, long expiresAt) { }

    private static final class TradeSession {
        private final UUID first;
        private final UUID second;
        private final ItemStack[] firstOffer = new ItemStack[9];
        private final ItemStack[] secondOffer = new ItemStack[9];
        private boolean firstReady;
        private boolean secondReady;
        private boolean locked;
        private int seconds;
        private BukkitTask task;

        private TradeSession(UUID first, UUID second) { this.first = first; this.second = second; }
        private ItemStack[] offer(UUID player) { return first.equals(player) ? firstOffer : secondOffer; }
    }
}
