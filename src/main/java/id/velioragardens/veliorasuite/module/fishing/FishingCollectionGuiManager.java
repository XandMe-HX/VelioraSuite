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
import java.util.List;

public final class FishingCollectionGuiManager implements Listener {

    private final FishingManager manager;

    public FishingCollectionGuiManager(FishingManager manager) {
        this.manager = manager;
    }

    public void open(Player player) {
        int size = manager.getConfigManager().getCollectionSize();
        Inventory inventory = Bukkit.createInventory(null, size, manager.getConfigManager().color(manager.getConfigManager().getCollectionTitle()));
        List<FishDefinition> fish = new ArrayList<>(manager.getConfigManager().getFishDefinitions().values());
        fish.sort(Comparator.comparing((FishDefinition definition) -> definition.rarity().power()).thenComparing(FishDefinition::name));
        int maxItems = Math.max(0, size - 9);
        for (int i = 0; i < fish.size() && i < maxItems; i++) {
            inventory.setItem(i, collectionItem(player, fish.get(i)));
        }
        inventory.setItem(size - 9, button(Material.ARROW, "&aBack", List.of("&7Kembali ke menu fishing.")));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().equals(manager.getConfigManager().color(manager.getConfigManager().getCollectionTitle()))) return;
        event.setCancelled(true);
        if (event.getRawSlot() == manager.getConfigManager().getCollectionSize() - 9) manager.openMainGui(player);
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
