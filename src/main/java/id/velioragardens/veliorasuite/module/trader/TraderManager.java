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
        this.campManager = new TraderCampManager(configManager);
        this.npcManager = new TraderNpcManager(configManager, this);
        this.guiManager = new TraderGuiManager(configManager, this, itemFactory);
        this.spawnManager = new TraderSpawnManager(plugin, configManager, dataManager, this);
    }

    public void load() {
        configManager.load();
        dataManager.load();
    }

    public void enable() {
        spawnManager.start();
    }

    public void disable() {
        spawnManager.stop();
        despawn(false);
        dataManager.flushAll();
    }

    public void reload() {
        configManager.load();
        spawnManager.reload();
    }

    public boolean isActive() { return activeLocation != null; }
    public Location getActiveLocation() { return activeLocation; }
    public long getDespawnAt() { return despawnAt; }
    public List<TraderTradeItem> getActiveItems() { return Collections.unmodifiableList(activeItems); }
    public TraderPurchaseManager getPurchaseManager() { return purchaseManager; }
    public TraderNpcManager getNpcManager() { return npcManager; }
    public TraderGuiManager getGuiManager() { return guiManager; }
    public TraderSpawnManager getSpawnManager() { return spawnManager; }
    public TraderItemFactory getItemFactory() { return itemFactory; }

    public void spawn(Location location) {
        if (location == null || location.getWorld() == null || isActive()) return;
        activeLocation = location.clone();
        despawnAt = System.currentTimeMillis() + configManager.getActiveMinutes() * 60_000L;
        activeItems.clear();
        List<TraderTradeItem> pool = new ArrayList<>(configManager.getTradePool());
        Collections.shuffle(pool);
        int amount = Math.min(configManager.getRandomItemsPerSpawn(), pool.size());
        for (int i = 0; i < amount; i++) activeItems.add(pool.get(i));
        purchaseManager.resetForNewTrader();
        campManager.build(activeLocation);
        npcManager.spawn(activeLocation);
        dataManager.saveActive(activeLocation, despawnAt);
        if (configManager.isAnnounceSpawn()) broadcast("trader-spawn", "%prefix% &6Veliora Trader muncul di &f%world% %x% %y% %z%&6.");
    }

    public void despawn(boolean announce) {
        if (!isActive()) return;
        npcManager.remove();
        campManager.restore();
        activeItems.clear();
        activeLocation = null;
        despawnAt = 0L;
        dataManager.clearActive();
        if (announce && configManager.isAnnounceDespawn()) Bukkit.broadcastMessage(configManager.color(configManager.message("trader-despawn", "%prefix% &eVeliora Trader telah pergi.")));
    }

    public void openGui(Player player) {
        if (!isActive()) {
            send(player, "trader-next", "%prefix% &eTrader belum muncul. Spawn berikutnya dalam &f%time%&e.", Map.of("%time%", timeLeft(spawnManager.getNextSpawnAt())));
            return;
        }
        guiManager.open(player);
    }

    public void buy(Player player, String itemId) {
        if (!isActive()) return;
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
        if (item.getPaymentType().name().equals("FISH")) send(player, "buy-success-fish", "%prefix% &aBerhasil menukar ikan untuk &f%item%&a.", Map.of("%item%", itemName));
        else send(player, "buy-success-money", "%prefix% &aBerhasil membeli &f%item% &adengan harga &f%money%&a.", Map.of("%item%", itemName, "%money%", String.valueOf(item.getMoney())));
    }

    public void sendStatus(CommandSender sender) {
        if (isActive()) {
            send(sender, "trader-active", "%prefix% &aTrader sedang aktif di &f%world% %x% %y% %z%&a. Despawn dalam &f%time%&a.", placeholders(activeLocation, timeLeft(despawnAt)));
        } else {
            send(sender, "trader-next", "%prefix% &eTrader belum muncul. Spawn berikutnya dalam &f%time%&e.", Map.of("%time%", timeLeft(spawnManager.getNextSpawnAt())));
        }
    }

    public void sendReloadSuccess(CommandSender sender) {
        send(sender, "reload-success", "%prefix% &aVelioraTrader berhasil direload.", Map.of());
    }

    public void sendNoPermission(CommandSender sender) {
        sender.sendMessage(configManager.color(configManager.getPrefix() + "&cKamu tidak punya izin."));
    }

    private void broadcast(String path, String fallback) {
        if (activeLocation == null) return;
        Bukkit.broadcastMessage(configManager.color(apply(configManager.message(path, fallback), placeholders(activeLocation, timeLeft(despawnAt)))));
    }

    private Map<String, String> placeholders(Location location, String time) {
        return Map.of(
                "%world%", location.getWorld() == null ? "world" : location.getWorld().getName(),
                "%x%", String.valueOf(location.getBlockX()),
                "%y%", String.valueOf(location.getBlockY()),
                "%z%", String.valueOf(location.getBlockZ()),
                "%time%", time
        );
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
        long minutes = seconds / 60L;
        long remainingSeconds = seconds % 60L;
        return minutes + "m " + remainingSeconds + "s";
    }
}
