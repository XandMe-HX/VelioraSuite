package id.velioragardens.veliorasuite.module.kits.model;

import org.bukkit.inventory.ItemStack;

public final class KitItem {

    private final ItemStack itemStack;

    public KitItem(ItemStack itemStack) {
        this.itemStack = itemStack == null ? null : itemStack.clone();
    }

    public ItemStack toItemStack() {
        return itemStack == null ? null : itemStack.clone();
    }
}
