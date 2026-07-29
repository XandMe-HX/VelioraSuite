package id.velioragardens.veliorasuite.module.trader;

import id.velioragardens.veliorasuite.module.trader.model.TraderTradeItem;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class TraderGuiManager implements Listener {

    private final TraderConfigManager configManager;
    private final TraderManager traderManager;
    private final TraderItemFactory itemFactory;
    private final Map<UUID, Map<Integer, String>> slotItems = new HashMap<>();

    public TraderGuiManager(TraderConfigManager configManager, TraderManager traderManager, TraderItemFactory itemFactory) {
        this.configManager = configManager;
        this.traderManager = traderManager;
        this.itemFactory = itemFactory;
    }

    public void open(Player player) {
        Inventory inventory = Bukkit.createInventory(null, configManager.getGuiSize(), configManager.color(configManager.getGuiTitle()));
        Map<Integer, String> map = new HashMap<>();
        List<Integer> slots = configManager.getTradeSlots();
        List<TraderTradeItem> activeItems = traderManager.getActiveItems();
        for (int i = 0; i < activeItems.size() && i < slots.size(); i++) {
            TraderTradeItem item = activeItems.get(i);
            int slot = slots.get(i);
            boolean soldOut = traderManager.getPurchaseManager().isSoldOut(player, item);
            inventory.setItem(slot, itemFactory.createTradeDisplay(item, soldOut));
            map.put(slot, item.getId());
        }
        inventory.setItem(configManager.getCloseSlot(), closeButton());
        slotItems.put(player.getUniqueId(), map);
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().equals(configManager.color(configManager.getGuiTitle()))) return;
        event.setCancelled(true);
        if (event.getRawSlot() == configManager.getCloseSlot()) {
            player.closeInventory();
            return;
        }
        String itemId = slotItems.getOrDefault(player.getUniqueId(), Map.of()).get(event.getRawSlot());
        if (itemId == null) return;
        traderManager.buy(player, itemId);
        open(player);
    }

    private ItemStack closeButton() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(configManager.color("&cClose"));
            meta.setLore(List.of(configManager.color("&7Tutup menu trader.")));
            item.setItemMeta(meta);
        }
        return item;
    }
}
