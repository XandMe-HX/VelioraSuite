package id.velioragardens.veliorasuite.module.fishing;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class FishingSellGuiManager implements Listener {

    private final FishingManager manager;
    private final Set<UUID> openSellGuis = new HashSet<>();

    public FishingSellGuiManager(FishingManager manager) {
        this.manager = manager;
    }

    public void open(Player player) {
        Inventory inventory = Bukkit.createInventory(null, manager.getConfigManager().getSellGuiSize(), manager.getConfigManager().color(manager.getConfigManager().getSellGuiTitle()));
        openSellGuis.add(player.getUniqueId());
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!openSellGuis.remove(player.getUniqueId())) return;

        List<ItemStack> sellable = new ArrayList<>();
        List<ItemStack> rejected = new ArrayList<>();
        for (ItemStack item : event.getInventory().getContents()) {
            if (item == null || item.getType() == Material.AIR) continue;
            if (isSellable(item)) sellable.add(item.clone());
            else rejected.add(item.clone());
        }

        boolean sold = manager.sell(player, sellable);
        if (!sold) {
            for (ItemStack item : sellable) returnItem(player, item);
        }
        for (ItemStack item : rejected) returnItem(player, item);

        if (!sold) {
            String path = sellable.isEmpty() ? "sell-empty" : "sell-failed-returned";
            String fallback = sellable.isEmpty()
                    ? "%prefix% &eTidak ada ikan yang bisa dijual."
                    : "%prefix% &eSell dibatalkan. Ikan sudah dikembalikan ke inventory.";
            player.sendMessage(manager.getConfigManager().color(manager.getConfigManager().message(path, fallback)));
        }
    }

    private boolean isSellable(ItemStack item) {
        return manager.getItemFactory().isCustomFish(item)
                || (manager.getConfigManager().isVanillaFishSellAllowed() && manager.getConfigManager().isVanillaFish(item.getType()));
    }

    private void returnItem(Player player, ItemStack item) {
        player.getInventory().addItem(item).values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
    }
}
