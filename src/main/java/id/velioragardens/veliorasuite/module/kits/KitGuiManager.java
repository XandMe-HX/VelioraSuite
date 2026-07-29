package id.velioragardens.veliorasuite.module.kits;

import id.velioragardens.veliorasuite.module.kits.model.Kit;
import id.velioragardens.veliorasuite.module.kits.model.KitGuiItem;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class KitGuiManager {

    private final KitsManager kitsManager;
    private final KitsConfigManager configManager;

    public KitGuiManager(KitsManager kitsManager, KitsConfigManager configManager) {
        this.kitsManager = kitsManager;
        this.configManager = configManager;
    }

    public void open(Player player) {
        KitsGuiHolder holder = new KitsGuiHolder();
        Inventory inventory = Bukkit.createInventory(holder, configManager.getGuiSize(), configManager.getGuiTitle());
        holder.setInventory(inventory);

        fillInventory(inventory);

        for (Kit kit : configManager.getEnabledKits()) {
            KitGuiItem guiItem = kit.getGuiItem();
            int slot = guiItem.getSlot();

            if (slot < 0 || slot >= inventory.getSize()) {
                continue;
            }

            inventory.setItem(slot, buildKitIcon(player, kit));
            holder.setKit(slot, kit.getId());
        }

        player.openInventory(inventory);
    }

    public ItemStack buildKitIcon(Player player, Kit kit) {
        KitGuiItem guiItem = kit.getGuiItem();
        ItemStack item = new ItemStack(guiItem.getMaterial());
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(configManager.color(kitsManager.applyKitPlaceholders(guiItem.getName(), player, kit)));

            List<String> lore = new ArrayList<>();
            lore.addAll(guiItem.getLore());
            lore.addAll(configManager.getStatusLore(kitsManager.getStatusKey(player, kit)));

            List<String> finalLore = new ArrayList<>();
            for (String line : lore) {
                finalLore.add(configManager.color(kitsManager.applyKitPlaceholders(line, player, kit)));
            }

            meta.setLore(finalLore);
            item.setItemMeta(meta);
        }

        return item;
    }

    private void fillInventory(Inventory inventory) {
        if (!configManager.isFillerEnabled()) {
            return;
        }

        Material material = configManager.getFillerMaterial();
        ItemStack filler = new ItemStack(material);
        ItemMeta meta = filler.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(configManager.getFillerName());
            meta.setLore(configManager.getFillerLore());
            filler.setItemMeta(meta);
        }

        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, filler);
        }
    }

    public static final class KitsGuiHolder implements InventoryHolder {

        private final Map<Integer, String> slots = new HashMap<>();
        private Inventory inventory;

        private void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        private void setKit(int slot, String kitId) {
            slots.put(slot, kitId);
        }

        public String getKitId(int slot) {
            return slots.get(slot);
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
