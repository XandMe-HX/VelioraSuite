package id.velioragardens.veliorasuite.module.fishing;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.module.fishing.model.CaughtFish;
import id.velioragardens.veliorasuite.module.fishing.model.FishRarity;
import id.velioragardens.veliorasuite.module.fishing.model.FishingBagEntry;
import id.velioragardens.veliorasuite.module.fishing.model.PlayerFishingStats;
import id.velioragardens.veliorasuite.module.adventure.AdventureModule;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class FishingManager {

    private final VelioraSuite plugin;
    private final FishingConfigManager configManager;
    private final FishingDataManager dataManager;
    private final FishingBagDataManager bagDataManager;
    private final FishingCollectionDataManager collectionDataManager;
    private final FishingRodDataManager rodDataManager;
    private final FishGenerator generator;
    private final FishItemFactory itemFactory;
    private final FishingEffectManager effectManager;
    private FishingMainGuiManager mainGuiManager;
    private FishingSellGuiManager sellGuiManager;
    private FishingBagGuiManager bagGuiManager;
    private FishingCollectionGuiManager collectionGuiManager;
    private FishingRodManager rodManager;
    private FishingMinigameManager minigameManager;
    private FishingRelicManager relicManager;
    private FishingPotionManager potionManager;
    private SecureTradeManager tradeManager;

    public FishingManager(VelioraSuite plugin) {
        this.plugin = plugin;
        this.configManager = new FishingConfigManager(plugin);
        this.dataManager = new FishingDataManager(plugin);
        this.bagDataManager = new FishingBagDataManager(plugin);
        this.collectionDataManager = new FishingCollectionDataManager(plugin);
        this.rodDataManager = new FishingRodDataManager(plugin);
        this.generator = new FishGenerator(configManager);
        this.itemFactory = new FishItemFactory(plugin, configManager);
        this.effectManager = new FishingEffectManager(configManager, itemFactory);
    }

    public void load() {
        configManager.load();
        dataManager.load();
        bagDataManager.load();
        collectionDataManager.load();
        rodDataManager.load();
        mainGuiManager = new FishingMainGuiManager(this);
        sellGuiManager = new FishingSellGuiManager(this);
        bagGuiManager = new FishingBagGuiManager(this);
        collectionGuiManager = new FishingCollectionGuiManager(this);
        rodManager = new FishingRodManager(this);
        minigameManager = new FishingMinigameManager(plugin, this);
        relicManager = new FishingRelicManager(this);
        potionManager = new FishingPotionManager(this);
        tradeManager = new SecureTradeManager(plugin, this);
    }

    public void reload() {
        configManager.load();
        if (rodManager != null) rodManager.reload();
    }

    public void shutdown() {
        if (minigameManager != null) minigameManager.clear();
        if (tradeManager != null) tradeManager.shutdown();
        dataManager.flush();
        bagDataManager.flush();
        collectionDataManager.flush();
    }

    public FishingConfigManager getConfigManager() { return configManager; }
    public FishingDataManager getDataManager() { return dataManager; }
    public FishingBagDataManager getBagDataManager() { return bagDataManager; }
    public FishingCollectionDataManager getCollectionDataManager() { return collectionDataManager; }
    public FishingRodDataManager getRodDataManager() { return rodDataManager; }
    public FishGenerator getGenerator() { return generator; }
    public FishItemFactory getItemFactory() { return itemFactory; }
    public FishingMainGuiManager getMainGuiManager() { return mainGuiManager; }
    public FishingSellGuiManager getSellGuiManager() { return sellGuiManager; }
    public FishingBagGuiManager getBagGuiManager() { return bagGuiManager; }
    public FishingCollectionGuiManager getCollectionGuiManager() { return collectionGuiManager; }
    public FishingRodManager getRodManager() { return rodManager; }
    public FishingMinigameManager getMinigameManager() { return minigameManager; }
    public FishingRelicManager getRelicManager() { return relicManager; }
    public FishingPotionManager getPotionManager() { return potionManager; }
    public SecureTradeManager getTradeManager() { return tradeManager; }

    public void giveGeneratedFish(Player player, FishGenerator.GeneratedFish generatedFish) {
        CaughtFish fish = generatedFish.fish();
        dataManager.recordCatch(player, fish);
        collectionDataManager.unlock(player, fish);
        AdventureModule adventure = plugin.getModuleManager().getModule("adventure")
                .filter(AdventureModule.class::isInstance).map(AdventureModule.class::cast).orElse(null);
        if (adventure != null && adventure.getManager() != null) adventure.getManager().addFishingProgress(player, 1);
        effectManager.play(player, fish);
        relicManager.rollDrop(player, fish);

        if (shouldAutoStore(fish) && bagDataManager.add(player, fish, 1)) {
            send(player, "bag-auto-store", "%prefix% &b%fish% &7masuk ke Fish Bag.", fishPlaceholders(fish));
        } else {
            ItemStack caught = itemFactory.create(generatedFish.definition(), fish);
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(caught);
            if (!leftovers.isEmpty()) {
                if (configManager.isBagEnabled() && bagDataManager.add(player, fish, 1)) {
                    send(player, "bag-inventory-full", "%prefix% &eInventory penuh. &b%fish% &7otomatis masuk Fish Bag.", fishPlaceholders(fish));
                } else {
                    leftovers.values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
                    send(player, "bag-full", "%prefix% &cFish Bag penuh. Ikan dijatuhkan dengan aman di dekatmu.", Map.of());
                }
            }
        }
        FishRarity minimumCatchMessage = configManager.getCatchMessageMinRarity();
        if (minimumCatchMessage != null && fish.rarity().power() >= minimumCatchMessage.power()) {
            send(player, "catch-success", "%prefix% &aKamu mendapatkan %rarity_color%%fish% &7(&f%weight%&7) senilai &a%price%&7.", fishPlaceholders(fish));
        }
    }

    public void openMainGui(Player player) { mainGuiManager.open(player); }
    public void openSellGui(Player player) { sellGuiManager.open(player); }
    public void openBagGui(Player player) { bagGuiManager.open(player); }
    public void openCollectionGui(Player player) { collectionGuiManager.open(player); }
    public void openRodShop(Player player) { rodManager.open(player); }
    public void openQuestRodShop(Player player) { rodManager.openQuest(player); }
    public void openPotionShop(Player player) { potionManager.open(player); }

    public void giveItemSafely(Player player, ItemStack item) { giveItem(player, item); }

    public boolean withdrawRodCost(Player player, int amount) {
        return amount <= 0 || dataManager.withdrawCoins(player, amount);
    }

    public long coins(Player player) { return dataManager.getCoins(player); }
    public String formattedCoins(Player player) { return configManager.formatCoins(coins(player)); }

    public void withdrawFromBag(Player player, FishingBagEntry entry, int amount) {
        if (player == null || entry == null || amount <= 0) return;
        int moved = Math.min(amount, entry.getAmount());
        for (int i = 0; i < moved; i++) giveItem(player, itemFactory.create(entry.getFish()));
        bagDataManager.remove(player, entry.getKey(), moved);
    }

    public int storeItemToBag(Player player, ItemStack item, int amount) {
        if (player == null || item == null || item.getType().isAir() || amount <= 0) return 0;
        if (!configManager.isBagEnabled()) {
            send(player, "bag-disabled", "%prefix% &cFish Bag sedang dimatikan.", Map.of());
            return 0;
        }
        CaughtFish fish = itemFactory.read(item);
        if (fish == null) {
            send(player, "bag-not-fish", "%prefix% &eItem itu bukan ikan yang bisa disimpan.", Map.of());
            return 0;
        }
        int moved = Math.min(amount, item.getAmount());
        if (!bagDataManager.add(player, fish, moved)) {
            send(player, "bag-full", "%prefix% &cFish Bag sudah mencapai batas 5 halaman.", Map.of());
            return 0;
        }
        item.setAmount(item.getAmount() - moved);
        send(player, "bag-store-success", "%prefix% &aBerhasil menyimpan &f%amount% &aikan ke Fish Bag.", Map.of("%amount%", String.valueOf(moved)));
        return moved;
    }

    public int storeAllInventoryFish(Player player) {
        if (player == null) return 0;
        if (!configManager.isBagEnabled()) {
            send(player, "bag-disabled", "%prefix% &cFish Bag sedang dimatikan.", Map.of());
            return 0;
        }

        ItemStack[] contents = player.getInventory().getStorageContents();
        int moved = 0;
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (item == null || item.getType().isAir() || !itemFactory.isStorableFish(item)) continue;
            CaughtFish fish = itemFactory.read(item);
            if (fish == null) continue;
            int amount = item.getAmount();
            if (!bagDataManager.add(player, fish, amount)) continue;
            contents[slot] = null;
            moved += amount;
        }
        player.getInventory().setStorageContents(contents);
        player.updateInventory();

        if (moved <= 0) send(player, "bag-no-fish", "%prefix% &eTidak ada ikan di inventory untuk disimpan.", Map.of());
        else send(player, "bag-store-success", "%prefix% &aBerhasil menyimpan &f%amount% &aikan ke Fish Bag.", Map.of("%amount%", String.valueOf(moved)));
        return moved;
    }

    public void sellFromBag(Player player, FishingBagEntry entry, int amount) {
        if (player == null || entry == null || amount <= 0) return;
        int sold = Math.min(amount, entry.getAmount());
        int total = entry.getFish().price() * sold;
        if (!depositSale(player, sold, total)) return;
        bagDataManager.remove(player, entry.getKey(), sold);
    }

    public void sellAllBag(Player player) {
        if (player == null) return;
        List<FishingBagEntry> entries = bagDataManager.entries(player);
        int sold = 0;
        int total = 0;
        for (FishingBagEntry entry : entries) {
            sold += entry.getAmount();
            total += entry.getFish().price() * entry.getAmount();
        }
        if (sold <= 0) {
            send(player, "bag-empty", "%prefix% &eFish Bag kamu kosong.", Map.of());
            return;
        }
        if (!depositSale(player, sold, total)) return;
        for (FishingBagEntry entry : entries) bagDataManager.remove(player, entry.getKey(), entry.getAmount());
    }

    public void sendTop(CommandSender sender) {
        List<PlayerFishingStats> top = dataManager.top(configManager.getTopLimit());
        sender.sendMessage(configManager.color(configManager.message("top-header", "%prefix% &bTop Fishing")));
        if (top.isEmpty()) {
            sender.sendMessage(configManager.color(configManager.message("top-empty", "%prefix% &eBelum ada data fishing.")));
            return;
        }
        int index = 1;
        for (PlayerFishingStats stats : top) {
            sender.sendMessage(configManager.color("&f" + index + ". &b" + stats.getName()
                    + " &7- &f" + stats.getTotalCatches() + " tangkapan"
                    + " &7- &f" + stats.getBestRarity().name() + ": &e" + stats.getBestFishName()
                    + " &7- &f" + itemFactory.formatWeight(stats.getBestFishWeight())));
            index++;
        }
    }

    public void sendHelp(CommandSender sender) {
        for (String line : configManager.messageList("help", List.of(
                "&8&m--------------------------------",
                "&b&lVelioraFishing",
                "&f/fish &7- Buka GUI fishing.",
                "&f/fish bag &7- Buka Fish Bag.",
                "&f/fish sell &7- Jual ikan.",
                "&f/fish collection &7- Buka koleksi ikan.",
                "&f/fish top &7- Leaderboard fishing.",
                "&f/fish reload &7- Reload config.",
                "&8&m--------------------------------"
        ))) sender.sendMessage(configManager.color(line));
    }

    public void sendReloadSuccess(CommandSender sender) { send(sender, "reload-success", "%prefix% &aVelioraFishing berhasil direload.", Map.of()); }
    public void sendNoPermission(CommandSender sender) { send(sender, "no-permission", "%prefix% &cKamu tidak punya izin.", Map.of()); }
    public void sendPlayerOnly(CommandSender sender) { send(sender, "player-only", "%prefix% &cCommand ini hanya bisa digunakan oleh player.", Map.of()); }

    public boolean sell(Player player, List<ItemStack> items) {
        int sold = 0;
        int total = 0;
        for (ItemStack item : items) {
            if (item == null || item.getType().isAir()) continue;
            int amount = item.getAmount();
            if (itemFactory.isCustomFish(item)) {
                total += itemFactory.price(item) * amount;
                sold += amount;
                continue;
            }
            if (configManager.isVanillaFishSellAllowed() && configManager.isVanillaFish(item.getType())) {
                for (int i = 0; i < amount; i++) total += configManager.randomPrice(FishRarity.VANILLA);
                sold += amount;
            }
        }
        if (sold <= 0) return false;
        return depositSale(player, sold, total);
    }

    private boolean depositSale(Player player, int sold, int total) {
        dataManager.depositCoins(player, total);
        dataManager.recordSale(player, sold, total);
        send(player, "sell-success", "%prefix% &aBerhasil menjual &f%amount% &aikan seharga &6%money% Koin&a.",
                Map.of("%amount%", String.valueOf(sold), "%money%", configManager.formatCoins(total)));
        return true;
    }

    private boolean shouldAutoStore(CaughtFish fish) {
        return configManager.isBagEnabled() && configManager.isBagAutoStoreEnabled() && configManager.getBagAutoStoreRarities().contains(fish.rarity());
    }

    private void giveItem(Player player, ItemStack item) {
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        leftover.values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
    }

    private Map<String, String> fishPlaceholders(CaughtFish fish) {
        Map<String, String> map = new HashMap<>();
        map.put("%fish%", fish.name());
        map.put("%rarity%", fish.rarity().name());
        map.put("%rarity_display%", fish.rarity().displayName());
        map.put("%rarity_color%", fish.rarity().color());
        map.put("%weight%", itemFactory.formatWeight(fish.weight()));
        map.put("%price%", configManager.formatCoins(fish.price()));
        map.put("%mutation%", fish.mutation());
        return map;
    }

    private void send(CommandSender sender, String path, String fallback, Map<String, String> placeholders) {
        sender.sendMessage(configManager.color(apply(configManager.message(path, fallback), placeholders)));
    }

    private String apply(String input, Map<String, String> placeholders) {
        String result = input == null ? "" : input;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) result = result.replace(entry.getKey(), entry.getValue());
        return result;
    }
}
