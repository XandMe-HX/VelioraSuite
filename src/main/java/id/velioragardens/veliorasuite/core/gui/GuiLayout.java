package id.velioragardens.veliorasuite.core.gui;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Shared visual rules for non-storage Suite menus.
 *
 * <p>All chest menus use a dark background, a contrasting glass border, and
 * reserve the bottom row for navigation/actions. Storage and editor menus must
 * not use this class because their empty slots are intentionally interactive.</p>
 */
public final class GuiLayout {
    private GuiLayout() {
    }

    public static void decorateMenu(Inventory inventory) {
        decorateMenu(inventory, Material.BLACK_STAINED_GLASS_PANE, Material.GRAY_STAINED_GLASS_PANE);
    }

    public static void decorateMenu(Inventory inventory, Material background, Material border) {
        ItemStack backgroundPane = pane(background, " ");
        ItemStack borderPane = pane(border, " ");
        int size = inventory.getSize();
        for (int slot = 0; slot < size; slot++) {
            inventory.setItem(slot, backgroundPane);
        }
        if (size % 9 != 0) return;

        int lastRow = (size / 9) - 1;
        for (int slot = 0; slot < size; slot++) {
            int row = slot / 9;
            int column = slot % 9;
            if (row == 0 || row == lastRow || column == 0 || column == 8) {
                inventory.setItem(slot, borderPane);
            }
        }
    }

    public static ItemStack pane(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            item.setItemMeta(meta);
        }
        return item;
    }
}
