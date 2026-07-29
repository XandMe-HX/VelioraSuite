package id.velioragardens.veliorasuite.module.trader;

import id.velioragardens.veliorasuite.module.trader.model.TraderTradeItem;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class TraderPurchaseManager {

    private final TraderConfigManager configManager;
    private final TraderDataManager dataManager;
    private final Map<String, Integer> sold = new HashMap<>();
    private final Map<UUID, Map<String, Integer>> perPlayer = new HashMap<>();

    public TraderPurchaseManager(TraderConfigManager configManager, TraderDataManager dataManager) {
        this.configManager = configManager;
        this.dataManager = dataManager;
    }

    public void resetForNewTrader() {
        sold.clear();
        perPlayer.clear();
        dataManager.purchases().set("active", null);
        dataManager.flushPurchases();
    }

    public boolean isSoldOut(Player player, TraderTradeItem item) {
        if (item == null) return true;
        if (sold.getOrDefault(item.getId(), 0) >= item.getStock()) return true;
        int playerBought = perPlayer.getOrDefault(player.getUniqueId(), Map.of()).getOrDefault(item.getId(), 0);
        return playerBought >= configManager.getPerPlayerLimit();
    }

    public void markPurchased(Player player, TraderTradeItem item) {
        sold.put(item.getId(), sold.getOrDefault(item.getId(), 0) + 1);
        perPlayer.computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>()).merge(item.getId(), 1, Integer::sum);
        dataManager.purchases().set("active.global." + item.getId(), sold.get(item.getId()));
        dataManager.purchases().set("active.players." + player.getUniqueId() + "." + item.getId(), perPlayer.get(player.getUniqueId()).get(item.getId()));
        dataManager.flushPurchases();
    }

    public int getSold(TraderTradeItem item) {
        return sold.getOrDefault(item.getId(), 0);
    }
}
