package id.velioragardens.veliorasuite.module.fishing;

import id.velioragardens.veliorasuite.module.fishing.model.FishingBagEntry;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class FishingBagGuiManager implements Listener {

    private final FishingManager manager;
    private final Map<UUID, Map<Integer, String>> slotKeys = new HashMap<>();

    public FishingBagGuiManager(FishingManager manager) {
        this.manager = manager;
    }

    public void open(Player player) {
        int size = manager.getConfigManager().getBagSize();
        Inventory inventory = Bukkit.createInventory(null, size, manager.getConfigManager().color(manager.getConfigManager().getBagTitle()));
        Map<Integer, String> map = new HashMap<>();
        List<FishingBagEntry> entries = manager.getBagDataManager().entries(player);
        int maxItems = Math.max(0, size - 9);
        for (int i = 0; i < entries.size() && i < maxItems; i++) {
            FishingBagEntry entry = entries.get(i);
            inventory.setItem(i, display(entry));
            map.put(i, entry.getKey());
        }
        inventory.setItem(size - 9, button(Material.ARROW, "&aBack", List.of("&7Kembali ke menu fishing.")));
        inventory.setItem(size - 5, button(Material.EMERALD, "&aSell All Fish Bag", List.of("&7Klik untuk menjual semua ikan di bag.")));
        inventory.setItem(size - 4, button(Material.HOPPER, "&bStore All Fish", List.of("&7Masukkan semua ikan dari inventory", "&7ke Fish Bag.")));
        slotKeys.put(player.getUniqueId(), map);
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().equals(manager.getConfigManager().color(manager.getConfigManager().getBagTitle()))) return;

        int slot = event.getRawSlot();
        int size = manager.getConfigManager().getBagSize();
        event.setCancelled(true);

        if (slot >= size) {
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType().isAir()) return;
            int amount = event.getClick().isShiftClick() ? clicked.getAmount() : 1;
            if (manager.storeItemToBag(player, clicked, amount) > 0) {
                player.updateInventory();
                open(player);
            }
            return;
        }

        if (slot == size - 9) {
            manager.openMainGui(player);
            return;
        }
        if (slot == size - 5) {
            manager.sellAllBag(player);
            open(player);
            return;
        }
        if (slot == size - 4) {
            manager.storeAllInventoryFish(player);
            open(player);
            return;
        }
        String key = slotKeys.getOrDefault(player.getUniqueId(), Map.of()).get(slot);
        if (key == null) return;
        FishingBagEntry entry = manager.getBagDataManager().get(player, key);
        if (entry == null) { open(player); return; }
        ClickType click = event.getClick();
        if (click.isLeftClick()) {
            int amount = click.isShiftClick() ? entry.getAmount() : 1;
            manager.withdrawFromBag(player, entry, amount);
            open(player);
        } else if (click.isRightClick()) {
            int amount = click.isShiftClick() ? entry.getAmount() : 1;
            manager.sellFromBag(player, entry, amount);
            open(player);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!event.getView().getTitle().equals(manager.getConfigManager().color(manager.getConfigManager().getBagTitle()))) return;
        int size = manager.getConfigManager().getBagSize();
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < size) {
                event.setCancelled(true);
                return;
            }
        }
    }

    private ItemStack display(FishingBagEntry entry) {
        ItemStack item = manager.getItemFactory().create(entry.getFish());
        item.setAmount(Math.max(1, Math.min(64, entry.getAmount())));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<String> lore = List.of(
                    manager.getConfigManager().color("&7Nama: &f" + entry.getFish().name()),
                    manager.getConfigManager().color("&7Rarity: " + entry.getFish().rarity().color() + entry.getFish().rarity().displayName()),
                    manager.getConfigManager().color("&7Berat: &f" + manager.getItemFactory().formatWeight(entry.getFish().weight())),
                    manager.getConfigManager().color("&7Harga jual: &a" + entry.getFish().price()),
                    manager.getConfigManager().color("&7Origin: &f" + entry.getFish().origin()),
                    manager.getConfigManager().color("&7Region: &f" + entry.getFish().region()),
                    manager.getConfigManager().color("&7Jumlah ikan: &f" + entry.getAmount()),
                    manager.getConfigManager().color(" "),
                    manager.getConfigManager().color("&aLeft click &7ambil 1"),
                    manager.getConfigManager().color("&aShift left &7ambil semua"),
                    manager.getConfigManager().color("&eRight click &7jual 1"),
                    manager.getConfigManager().color("&eShift right &7jual semua")
            );
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack button(Material material, String name, List<String> lore) {
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
