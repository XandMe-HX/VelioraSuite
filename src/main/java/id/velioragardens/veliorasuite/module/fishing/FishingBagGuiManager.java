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
    private final Map<UUID, Integer> pages = new HashMap<>();

    public FishingBagGuiManager(FishingManager manager) {
        this.manager = manager;
    }

    public void open(Player player) {
        open(player, pages.getOrDefault(player.getUniqueId(), 0));
    }

    public void open(Player player, int requestedPage) {
        int size = 54;
        int page = Math.max(0, Math.min(FishingBagDataManager.MAX_PAGES - 1, requestedPage));
        String title = title(page);
        Inventory inventory = Bukkit.createInventory(null, size, title);
        Map<Integer, String> map = new HashMap<>();
        List<FishingBagEntry> entries = manager.getBagDataManager().entries(player);
        int start = page * FishingBagDataManager.ITEMS_PER_PAGE;
        for (int slot = 0; slot < FishingBagDataManager.ITEMS_PER_PAGE && start + slot < entries.size(); slot++) {
            FishingBagEntry entry = entries.get(start + slot);
            inventory.setItem(slot, display(entry));
            map.put(slot, entry.getKey());
        }
        inventory.setItem(45, button(Material.ARROW, "&eHalaman Sebelumnya", List.of("&7Buka halaman " + Math.max(1, page) + ".")));
        inventory.setItem(46, button(Material.OAK_DOOR, "&aKembali", List.of("&7Kembali ke menu Fishing.")));
        inventory.setItem(48, button(Material.EMERALD, "&aJual Semua", List.of("&7Menjual seluruh isi Fish Bag.")));
        inventory.setItem(49, button(Material.BOOK, "&bFish Bag &f" + (page + 1) + "&7/&f" + FishingBagDataManager.MAX_PAGES,
                List.of("&7Kapasitas: &f" + entries.size() + "&7/&f" + FishingBagDataManager.MAX_UNIQUE_ITEMS + " jenis")));
        inventory.setItem(50, button(Material.HOPPER, "&bSimpan Semua Ikan", List.of("&7Pindahkan seluruh ikan", "&7dari inventory ke Fish Bag.")));
        inventory.setItem(53, button(Material.ARROW, "&eHalaman Berikutnya", List.of("&7Buka halaman " + Math.min(FishingBagDataManager.MAX_PAGES, page + 2) + ".")));
        pages.put(player.getUniqueId(), page);
        slotKeys.put(player.getUniqueId(), map);
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!isBagTitle(event.getView().getTitle())) return;

        int slot = event.getRawSlot();
        int size = 54;
        event.setCancelled(true);

        if (slot >= size) {
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType().isAir()) return;
            int amount = event.getClick().isShiftClick() ? clicked.getAmount() : 1;
            if (manager.storeItemToBag(player, clicked, amount) > 0) {
                player.updateInventory();
                open(player, pages.getOrDefault(player.getUniqueId(), 0));
            }
            return;
        }

        if (slot == 45) {
            open(player, pages.getOrDefault(player.getUniqueId(), 0) - 1);
            return;
        }
        if (slot == 46) {
            manager.openMainGui(player);
            return;
        }
        if (slot == 48) {
            manager.sellAllBag(player);
            open(player, 0);
            return;
        }
        if (slot == 50) {
            manager.storeAllInventoryFish(player);
            open(player, pages.getOrDefault(player.getUniqueId(), 0));
            return;
        }
        if (slot == 53) {
            open(player, pages.getOrDefault(player.getUniqueId(), 0) + 1);
            return;
        }
        String key = slotKeys.getOrDefault(player.getUniqueId(), Map.of()).get(slot);
        if (key == null) return;
        FishingBagEntry entry = manager.getBagDataManager().get(player, key);
        if (entry == null) { open(player, pages.getOrDefault(player.getUniqueId(), 0)); return; }
        ClickType click = event.getClick();
        if (click.isLeftClick()) {
            int amount = click.isShiftClick() ? entry.getAmount() : 1;
            manager.withdrawFromBag(player, entry, amount);
            open(player, pages.getOrDefault(player.getUniqueId(), 0));
        } else if (click.isRightClick()) {
            int amount = click.isShiftClick() ? entry.getAmount() : 1;
            manager.sellFromBag(player, entry, amount);
            open(player, pages.getOrDefault(player.getUniqueId(), 0));
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!isBagTitle(event.getView().getTitle())) return;
        int size = 54;
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < size) {
                event.setCancelled(true);
                return;
            }
        }
    }

    private String title(int page) {
        return manager.getConfigManager().color(manager.getConfigManager().getBagTitle() + " &8- &f" + (page + 1) + "/" + FishingBagDataManager.MAX_PAGES);
    }

    private boolean isBagTitle(String title) {
        return title != null && title.startsWith(manager.getConfigManager().color(manager.getConfigManager().getBagTitle() + " &8- &f"));
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
