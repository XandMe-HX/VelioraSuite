package id.velioragardens.veliorasuite.module.kits.model;

import org.bukkit.Material;

import java.util.List;

public final class KitGuiItem {

    private final int slot;
    private final Material material;
    private final String name;
    private final List<String> lore;

    public KitGuiItem(int slot, Material material, String name, List<String> lore) {
        this.slot = Math.max(0, slot);
        this.material = material == null ? Material.CHEST : material;
        this.name = name == null ? "&fKit" : name;
        this.lore = lore == null ? List.of() : List.copyOf(lore);
    }

    public int getSlot() {
        return slot;
    }

    public Material getMaterial() {
        return material;
    }

    public String getName() {
        return name;
    }

    public List<String> getLore() {
        return lore;
    }
}
