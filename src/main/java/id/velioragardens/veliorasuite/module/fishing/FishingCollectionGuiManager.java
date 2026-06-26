package id.velioragardens.veliorasuite.module.fishing;

import id.velioragardens.veliorasuite.module.fishing.model.FishDefinition;
import id.velioragardens.veliorasuite.module.fishing.model.FishingCollectionEntry;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class FishingCollectionGuiManager implements Listener {

    private final FishingManager manager;
    private final Map<UUID, Integer> pages = new HashMap<>();

    public FishingCollectionGuiManager(FishingManager manager) {
        this.manager = manager;
    }

    public void open(Player player) {
        open(player, pages.getOrDefault(player.getUniqueId(), 0));
    }

    private void open(Player player, int page) {
        int size = manager.getConfigManager().getCollectionSize();
        int maxItems = Math.max(1, size - 9);
        List<FishDefinition> fish = new ArrayList<>(manager.getConfigManager().getFishDefinitions().values());
        fish.sort(Comparator.comparingInt((FishDefinition definition) -> definition.rarity().power()).thenComparing(FishDefinition::name));
        int maxPage = Math.max(0, (fish.size() - 1) / maxItems);
        int safePage = Math.max(0, Math.min(page, maxPage));
        pages.put(player.getUniqueId(), safePage);

        Inventory inventory = Bukkit.createInventory(null, size, manager.getConfigManager().color(manager.getConfigManager().getCollectionTitle()));
        int start = safePage * maxItems;
        for (int i = 0; i < maxItems && start + i < fish.size(); i++) {
            inventory.setItem(i, collectionItem(player, fish.get(start + i)));
        }
        inventory.setItem(size - 9, button(Material.ARROW, "&aBack", List.of("&7Kembali ke menu fishing.")));
        inventory.setItem(size - 6, button(Material.PAPER, "&ePrevious Page", List.of("&7Halaman sebelumnya.")));
        inventory.setItem(size - 5, button(Material.MAP, "&fPage " + (safePage + 1) + "/" + (maxPage + 1), List.of("&7Total ikan: &f" + fish.size())));
        inventory.setItem(size - 4, button(Material.PAPER, "&eNext Page", List.of("&7Halaman berikutnya.")));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().equals(manager.getConfigManager().color(manager.getConfigManager().getCollectionTitle()))) return;
        event.setCancelled(true);
        int size = manager.getConfigManager().getCollectionSize();
        int page = pages.getOrDefault(player.getUniqueId(), 0);
        if (event.getRawSlot() == size - 9) manager.openMainGui(player);
        else if (event.getRawSlot() == size - 6) open(player, page - 1);
        else if (event.getRawSlot() == size - 4) open(player, page + 1);
    }

    private ItemStack collectionItem(Player player, FishDefinition definition) {
        boolean unlocked = manager.getCollectionDataManager().isUnlocked(player, definition.id());
        if (!unlocked) return lockedItem();
        FishingCollectionEntry entry = manager.getCollectionDataManager().get(player, definition.id());
        ItemStack item = new ItemStack(definition.material() == null ? Material.COD : definition.material());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(manager.getConfigManager().color(definition.rarity().color() + definition.name()));
            meta.setLore(List.of(
                    manager.getConfigManager().color("&7Rarity: " + definition.rarity().color() + definition.rarity().displayName()),
                    manager.getConfigManager().color("&7Origin: &f" + definition.origin()),
                    manager.getConfigManager().color("&7Region: &f" + definition.region()),
                    manager.getConfigManager().color("&7Total caught: &f" + entry.getTotalCaught()),
                    manager.getConfigManager().color("&7Best weight: &f" + manager.getItemFactory().formatWeight(entry.getBestWeight()))
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack lockedItem() {
        ItemStack item = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(manager.getConfigManager().color("&8???"));
            meta.setLore(List.of(manager.getConfigManager().color("&7Ikan ini belum ditemukan.")));
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
