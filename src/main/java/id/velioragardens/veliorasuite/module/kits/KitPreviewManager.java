package id.velioragardens.veliorasuite.module.kits;

import id.velioragardens.veliorasuite.module.kits.model.Kit;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class KitPreviewManager {

    private final KitsConfigManager configManager;

    public KitPreviewManager(KitsConfigManager configManager) {
        this.configManager = configManager;
    }

    public void openPreview(Player player, Kit kit) {
        int size = Math.max(9, Math.min(54, ((kit.getItems().size() + 8) / 9) * 9));
        String title = configManager.getPreviewTitle(kit.getDisplayName());
        Inventory inventory = Bukkit.createInventory(null, size, title);

        for (ItemStack item : kit.getItems()) {
            inventory.addItem(item.clone());
        }

        player.openInventory(inventory);
    }
}
