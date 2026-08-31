package id.velioragardens.veliorasuite.module.adventure;

import org.bukkit.Material;

/** Professions are the safe, lightweight successor to the legacy UJobs roles. */
public enum AdventureProfession {
    MINER("Penambang", Material.DIAMOND_PICKAXE, "&b"),
    FORAGER("Penebang", Material.DIAMOND_AXE, "&a"),
    FARMER("Petani", Material.DIAMOND_HOE, "&e"),
    HUNTER("Pemburu", Material.DIAMOND_SWORD, "&c"),
    FISHER("Nelayan", Material.FISHING_ROD, "&9");

    private final String display; private final Material icon; private final String color;
    AdventureProfession(String display, Material icon, String color) { this.display = display; this.icon = icon; this.color = color; }
    public String display() { return display; }
    public Material icon() { return icon; }
    public String color() { return color; }
}
