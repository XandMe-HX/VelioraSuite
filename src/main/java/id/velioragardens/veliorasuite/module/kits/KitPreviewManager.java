package id.velioragardens.veliorasuite.module.kits;

import id.velioragardens.veliorasuite.module.kits.model.Kit;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public final class KitPreviewManager {

    private final KitsConfigManager configManager;

    public KitPreviewManager(KitsConfigManager configManager) {
        this.configManager = configManager;
    }

    public void openPreview(org.bukkit.entity.Player player, Kit kit) {
        // One header row and one navigation row leave up to 36 slots for kit items.
        int size = Math.max(27, Math.min(54, ((kit.getItems().size() + 26) / 9) * 9));
        PreviewHolder holder = new PreviewHolder(kit.getId());
        Inventory inventory = Bukkit.createInventory(holder, size, configManager.getPreviewTitle(kit.getDisplayName()));
        holder.setInventory(inventory);

        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int slot = 0; slot < size; slot++) {
            inventory.setItem(slot, filler);
        }

        inventory.setItem(4, item(Material.CHEST, "&b&lISI KIT", List.of(
                "&7Berikut semua item yang akan kamu terima.",
                "&8Tekan tombol hijau untuk mengambil kit."
        )));

        int slot = 9;
        for (ItemStack kitItem : kit.getItems()) {
            if (slot >= size - 9) {
                break;
            }
            inventory.setItem(slot++, kitItem.clone());
        }

        int claimSlot = size - 7;
        inventory.setItem(claimSlot, item(Material.LIME_WOOL, "&a&lAMBIL KIT", List.of(
                "&7Klik untuk mengambil kit ini.",
                "&eKit berbayar baru dipotong setelah tombol ini ditekan."
        )));
        holder.setClaimSlot(claimSlot);
        int backSlot = size - 3;
        inventory.setItem(backSlot, item(Material.ARROW, "&e&lKEMBALI", List.of("&7Kembali ke menu Veliora Kits.")));
        holder.setBackSlot(backSlot);
        player.openInventory(inventory);
    }

    private ItemStack item(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(configManager.color(name));
            meta.setLore(lore.stream().map(configManager::color).toList());
            item.setItemMeta(meta);
        }
        return item;
    }

    public static final class PreviewHolder implements InventoryHolder {

        private final String kitId;
        private Inventory inventory;
        private int backSlot = -1;
        private int claimSlot = -1;

        private PreviewHolder(String kitId) {
            this.kitId = kitId;
        }

        private void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        private void setBackSlot(int backSlot) {
            this.backSlot = backSlot;
        }

        public boolean isBackSlot(int slot) {
            return slot == backSlot;
        }

        private void setClaimSlot(int claimSlot) { this.claimSlot = claimSlot; }
        public boolean isClaimSlot(int slot) { return slot == claimSlot; }
        public String kitId() { return kitId; }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
