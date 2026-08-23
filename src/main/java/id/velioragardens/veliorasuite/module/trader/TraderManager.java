package id.velioragardens.veliorasuite.module.trader;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.module.trader.model.TraderTradeItem;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class TraderManager {

    private final VelioraSuite plugin;
    private final TraderConfigManager configManager;
    private final TraderDataManager dataManager;
    private final TraderItemFactory itemFactory;
    private final TraderPaymentManager paymentManager;
    private final TraderPurchaseManager purchaseManager;
    private final TraderCampManager campManager;
    private final TraderNpcManager npcManager;
    private final TraderGuiManager guiManager;
    private final TraderSpawnManager spawnManager;
    private final List<TraderTradeItem> activeItems = new ArrayList<>();
    private Location activeLocation;
    private long despawnAt;

    public TraderManager(VelioraSuite plugin) {
        this.plugin = plugin;
        this.configManager = new TraderConfigManager(plugin);
        this.dataManager = new TraderDataManager(plugin);
        this.itemFactory = new TraderItemFactory(plugin, configManager);
        this.paymentManager = new TraderPaymentManager(plugin, configManager);
        this.purchaseManager = new TraderPurchaseManager(configManager, dataManager);
        this.campManager = new TraderCampManager(configManager, dataManager);
        this.npcManager = new TraderNpcManager(configManager, this);
        this.guiManager = new TraderGuiManager(configManager, this, itemFactory);
        this.spawnManager = new TraderSpawnManager(plugin, configManager, dataManager, this);
    }

    public void load() {
        configManager.load();
        dataManager.load();
        cleanupPersistedActiveState();
        if (configManager.isGuiOnly()) {
            activeItems.clear();
            activeItems.addAll(configManager.getTradePool());
        }
    }

    public void enable() { if (!configManager.isGuiOnly()) spawnManager.start(); }

    public void disable() {
        spawnManager.stop();
        cleanupActiveTrader(true);
        dataManager.flushAll();
    }

    public void reload() {
        configManager.load();
        if (configManager.isGuiOnly()) {
            spawnManager.stop();
            activeItems.clear();
            activeItems.addAll(configManager.getTradePool());
        } else spawnManager.reload();
    }

    public boolean isActive() { return activeLocation != null; }
    public Location getActiveLocation() { return activeLocation; }
    public long getDespawnAt() { return despawnAt; }
    public List<TraderTradeItem> getActiveItems() { return Collections.unmodifiableList(activeItems); }
    public TraderConfigManager getConfigManager() { return configManager; }
    public TraderPurchaseManager getPurchaseManager() { return purchaseManager; }
    public TraderNpcManager getNpcManager() { return npcManager; }
    public TraderGuiManager getGuiManager() { return guiManager; }
    public TraderSpawnManager getSpawnManager() { return spawnManager; }
    public TraderItemFactory getItemFactory() { return itemFactory; }

    public boolean spawn(Location requestedLocation) {
        if (isActive()) return false;
        Location location = spawnManager.prepareSpawnLocation(requestedLocation);
        if (location == null || location.getWorld() == null) return false;
        location.getChunk().load(true);
        List<TraderTradeItem> selected = selectRandomItems();
        if (selected.isEmpty()) return false;

        purchaseManager.resetForNewTrader();
        // Camp/tent generation was intentionally removed. The configured location stays untouched.
        if (!npcManager.spawn(location)) return false;

        activeLocation = location.clone();
        despawnAt = System.currentTimeMillis() + configManager.getActiveMinutes() * 60_000L;
        activeItems.clear();
        activeItems.addAll(selected);
        dataManager.saveActive(activeLocation, despawnAt);
        spawnManager.resetReminderClock();
        if (configManager.isAnnounceSpawn()) broadcast("trader-spawn", "%prefix% &6Veliora Trader muncul di &f%world% %x% %y% %z%&6.");
        if (configManager.isDebugSpawn()) plugin.getLogger().info("VelioraTrader debug: active=true despawnAt=" + despawnAt + " location=" + activeLocation);
        return true;
    }

    public boolean forceSpawn(CommandSender sender) {
        cleanupActiveTrader(true);
        Location location = spawnManager.findSpawnLocation();
        boolean spawned = spawn(location);
        if (spawned) send(sender, "force-spawn-success", "%prefix% &aTrader berhasil dispawn untuk test.", Map.of());
        else sender.sendMessage(configManager.color(configManager.getPrefix() + "&cGagal spawn trader. Tidak ada lokasi aman."));
        return spawned;
    }

    public boolean riset(CommandSender sender) {
        cleanupActiveTrader(true);
        Location location = spawnManager.findSpawnLocation();
        boolean spawned = spawn(location);
        if (spawned) send(sender, "riset-success", "%prefix% &aTrader berhasil diriset, trader lama dibersihkan dan trader baru dispawn.", Map.of());
        else send(sender, "riset-failed", "%prefix% &cGagal riset trader. Cek console.", Map.of());
        return spawned;
    }

    public void despawn(boolean announce) {
        boolean wasActive = isActive();
        cleanupActiveTrader(true);
        spawnManager.scheduleNextFromNow();
        if (wasActive && announce && configManager.isAnnounceDespawn()) Bukkit.broadcastMessage(configManager.color(configManager.message("trader-despawn", "%prefix% &eVeliora Trader telah pergi.")));
    }

    public void openGui(Player player) {
        if (!configManager.isGuiOnly() && !isActive()) {
            send(player, "trader-next", "%prefix% &eTrader belum muncul. Spawn berikutnya dalam &f%time%&e.", Map.of("%time%", timeLeft(spawnManager.getNextSpawnAt())));
            return;
        }
        guiManager.open(player);
    }

    public void buy(Player player, String itemId) {
        if (!configManager.isGuiOnly() && !isActive()) return;
        TraderTradeItem item = activeItems.stream().filter(trade -> trade.getId().equalsIgnoreCase(itemId)).findFirst().orElse(null);
        if (item == null) return;
        if (purchaseManager.isSoldOut(player, item)) {
            send(player, "sold-out", "%prefix% &cItem ini sudah sold out.", Map.of());
            return;
        }
        TraderPaymentManager.PaymentResult result = paymentManager.takePayment(player, item);
        if (!result.success()) {
            switch (result.reason()) {
                case "vault" -> send(player, "vault-missing", "%prefix% &cVault economy tidak aktif.", Map.of());
                case "money" -> send(player, "not-enough-money", "%prefix% &cUang kamu tidak cukup.", Map.of());
                case "fish", "fish-module" -> send(player, "not-enough-fish", "%prefix% &cIkan yang dibutuhkan belum cukup.", Map.of());
                default -> send(player, "not-enough-money", "%prefix% &cTransaksi gagal.", Map.of());
            }
            return;
        }
        purchaseManager.markPurchased(player, item);
        give(player, itemFactory.createReward(item));
        String itemName = configManager.color(item.getName());
        if (item.getPaymentType().name().equals("FISH")) {
            send(player, "buy-success-fish", "%prefix% &aBerhasil menukar ikan untuk &f%item%&a.", Map.of("%item%", itemName));
            broadcastPurchase("trader-purchase-fish", "%prefix% &e%player% &aberhasil menukar ikan untuk &f%item%&a.", player, itemName);
        } else {
            send(player, "buy-success-money", "%prefix% &aBerhasil membeli &f%item% &adengan harga &f%money%&a.", Map.of("%item%", itemName, "%money%", String.valueOf(item.getMoney())));
            broadcastPurchase("trader-purchase-money", "%prefix% &e%player% &aberhasil membeli &f%item% &adari Veliora Trader.", player, itemName);
        }
    }

    public void sendStatus(CommandSender sender) {
        if (isActive()) send(sender, "trader-active", "%prefix% &aTrader sedang aktif di &f%world% %x% %y% %z%&a. Hilang dalam &f%time%&a.", placeholders(activeLocation, timeLeft(despawnAt)));
        else send(sender, "trader-next", "%prefix% &eTrader belum muncul. Spawn berikutnya dalam &f%time%&e.", Map.of("%time%", timeLeft(spawnManager.getNextSpawnAt())));
    }

    public void broadcastReminder() {
        if (!isActive()) return;
        broadcast("trader-reminder", "%prefix% &6Veliora Trader masih ada di &f%world% %x% %y% %z%&6. Hilang dalam &f%time%&6.");
    }

    public void sendReloadSuccess(CommandSender sender) { send(sender, "reload-success", "%prefix% &aVelioraTrader berhasil direload.", Map.of()); }
    public void sendNoPermission(CommandSender sender) { sender.sendMessage(configManager.color(configManager.getPrefix() + "&cKamu tidak punya izin.")); }

    private void cleanupPersistedActiveState() {
        if (!dataManager.hasActive() && dataManager.loadCampBackup().isEmpty()) return;
        cleanupActiveTrader(true);
    }

    private void cleanupActiveTrader(boolean restoreBlocks) {
        Location cleanupLocation = activeLocation != null ? activeLocation : dataManager.getActiveLocation();
        npcManager.removeNear(cleanupLocation);
        if (restoreBlocks) campManager.restore();
        activeItems.clear();
        activeLocation = null;
        despawnAt = 0L;
        purchaseManager.resetForNewTrader();
        dataManager.clearActive();
    }

    private List<TraderTradeItem> selectRandomItems() {
        List<TraderTradeItem> pool = new ArrayList<>(configManager.getTradePool());
        if (pool.isEmpty()) return List.of();

        List<TraderTradeItem> selected = new ArrayList<>();
        selectCategory(pool, selected, List.of("builder_supply", "mining_supply", "explorer_supply", "experience_supply", "ender_supply", "diamond_pack_x64"), 2);
        selectCategory(pool, selected, List.of("enchanted_shulker", "guardian_shield", "ocean_crown", "windwalker_boots", "silk_touch_relic", "aether_pickaxe"), 2);
        selectCategory(pool, selected, List.of("excalibur", "angel_of_death_bow", "trisula_poseidon", "kapak_leviathan", "ancient_mace", "ryujin_no_tsuri", "skybound_wings"), 1);

        Collections.shuffle(pool);
        for (TraderTradeItem item : pool) {
            if (selected.size() >= Math.min(5, configManager.getRandomItemsPerSpawn())) break;
            if (!selected.contains(item)) selected.add(item);
        }
        return List.copyOf(selected);
    }

    private void selectCategory(List<TraderTradeItem> pool, List<TraderTradeItem> selected, List<String> ids, int amount) {
        List<TraderTradeItem> category = new ArrayList<>();
        for (TraderTradeItem item : pool) if (ids.contains(item.getId())) category.add(item);
        Collections.shuffle(category);
        for (TraderTradeItem item : category) {
            if (amount-- <= 0) break;
            selected.add(item);
        }
    }

    private void broadcast(String path, String fallback) {
        if (activeLocation == null) return;
        Bukkit.broadcastMessage(configManager.color(apply(configManager.message(path, fallback), placeholders(activeLocation, timeLeft(despawnAt)))));
    }

    private void broadcastPurchase(String path, String fallback, Player player, String itemName) {
        if (!configManager.isAnnouncePurchase()) return;
        Bukkit.broadcastMessage(configManager.color(apply(configManager.message(path, fallback), Map.of("%player%", player.getName(), "%item%", itemName))));
    }

    private Map<String, String> placeholders(Location location, String time) {
        return Map.of("%world%", location.getWorld() == null ? "world" : location.getWorld().getName(), "%x%", String.valueOf(location.getBlockX()), "%y%", String.valueOf(location.getBlockY()), "%z%", String.valueOf(location.getBlockZ()), "%time%", time);
    }

    private void send(CommandSender sender, String path, String fallback, Map<String, String> placeholders) {
        sender.sendMessage(configManager.color(apply(configManager.message(path, fallback), placeholders)));
    }

    private String apply(String input, Map<String, String> placeholders) {
        String result = input == null ? "" : input;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) result = result.replace(entry.getKey(), entry.getValue());
        return result;
    }

    private void give(Player player, ItemStack item) {
        player.getInventory().addItem(item).values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
    }

    private String timeLeft(long target) {
        long seconds = Math.max(0L, (target - System.currentTimeMillis()) / 1000L);
        return (seconds / 60L) + "m " + (seconds % 60L) + "s";
    }
}
