package id.velioragardens.veliorasuite.module.fishing;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.module.fishing.model.CaughtFish;
import id.velioragardens.veliorasuite.module.fishing.model.FishRarity;
import id.velioragardens.veliorasuite.module.fishing.model.PlayerFishingStats;
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
    private final FishGenerator generator;
    private final FishItemFactory itemFactory;
    private final FishingEconomyHook economyHook;
    private final FishingQuestHook questHook;
    private FishingMainGuiManager mainGuiManager;
    private FishingSellGuiManager sellGuiManager;
    private FishingMinigameManager minigameManager;

    public FishingManager(VelioraSuite plugin) {
        this.plugin = plugin;
        this.configManager = new FishingConfigManager(plugin);
        this.dataManager = new FishingDataManager(plugin);
        this.generator = new FishGenerator(configManager);
        this.itemFactory = new FishItemFactory(plugin, configManager);
        this.economyHook = new FishingEconomyHook(plugin);
        this.questHook = new FishingQuestHook(plugin, configManager);
    }

    public void load() {
        configManager.load();
        dataManager.load();
        mainGuiManager = new FishingMainGuiManager(this);
        sellGuiManager = new FishingSellGuiManager(this);
        minigameManager = new FishingMinigameManager(plugin, this);
    }

    public void reload() {
        configManager.load();
    }

    public void shutdown() {
        if (minigameManager != null) minigameManager.clear();
        dataManager.flush();
    }

    public FishingConfigManager getConfigManager() { return configManager; }
    public FishingDataManager getDataManager() { return dataManager; }
    public FishGenerator getGenerator() { return generator; }
    public FishItemFactory getItemFactory() { return itemFactory; }
    public FishingMainGuiManager getMainGuiManager() { return mainGuiManager; }
    public FishingSellGuiManager getSellGuiManager() { return sellGuiManager; }
    public FishingMinigameManager getMinigameManager() { return minigameManager; }

    public void giveGeneratedFish(Player player, FishGenerator.GeneratedFish generatedFish) {
        CaughtFish fish = generatedFish.fish();
        ItemStack item = itemFactory.create(generatedFish.definition(), fish);
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        leftover.values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        dataManager.recordCatch(player, fish);
        questHook.addFishingProgress(player);
        send(player, "catch-success", "%prefix% &aKamu mendapatkan %rarity_color%%fish% &7(&f%weight%&7) senilai &a%price%&7.", fishPlaceholders(fish));
    }

    public void openMainGui(Player player) {
        mainGuiManager.open(player);
    }

    public void openSellGui(Player player) {
        sellGuiManager.open(player);
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
                "&f/fish &7- Buka info fishing.",
                "&f/fish sell &7- Jual ikan.",
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
        economyHook.deposit(player, total);
        dataManager.recordSale(player, sold, total);
        send(player, "sell-success", "%prefix% &aBerhasil menjual &f%amount% &aikan seharga &f%money%&a.", Map.of("%amount%", String.valueOf(sold), "%money%", String.valueOf(total)));
        return true;
    }

    private Map<String, String> fishPlaceholders(CaughtFish fish) {
        Map<String, String> map = new HashMap<>();
        map.put("%fish%", fish.name());
        map.put("%rarity%", fish.rarity().name());
        map.put("%rarity_display%", fish.rarity().displayName());
        map.put("%rarity_color%", fish.rarity().color());
        map.put("%weight%", itemFactory.formatWeight(fish.weight()));
        map.put("%price%", String.valueOf(fish.price()));
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
