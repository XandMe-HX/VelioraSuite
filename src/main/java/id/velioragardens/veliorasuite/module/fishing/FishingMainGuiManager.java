package id.velioragardens.veliorasuite.module.fishing;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public final class FishingMainGuiManager implements Listener {

    private final FishingManager manager;

    public FishingMainGuiManager(FishingManager manager) {
        this.manager = manager;
    }

    public void open(Player player) {
        Inventory inventory = Bukkit.createInventory(null, 27, manager.getConfigManager().color("&8VelioraFishing"));
        inventory.setItem(11, item(Material.BARREL, "&bJual Ikan", List.of("&7Klik untuk membuka sell GUI.", "&f/fish sell")));
        inventory.setItem(15, item(Material.OAK_SIGN, "&eTop Fishing", List.of("&7Klik untuk melihat leaderboard.", "&f/fish top")));
        inventory.setItem(13, item(Material.FISHING_ROD, "&aVelioraFishing", List.of("&7Custom fishing, minigame,", "&7sell GUI, dan leaderboard.")));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().equals(manager.getConfigManager().color("&8VelioraFishing"))) return;
        event.setCancelled(true);
        if (event.getRawSlot() == 11) {
            player.closeInventory();
            manager.openSellGui(player);
        } else if (event.getRawSlot() == 15) {
            player.closeInventory();
            manager.sendTop(player);
        }
    }

    private ItemStack item(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(manager.getConfigManager().color(name));
            meta.setLore(lore.stream().map(manager.getConfigManager()::color).toList());
            item.setItemMeta(meta);
        }
        return item;
    }
}
