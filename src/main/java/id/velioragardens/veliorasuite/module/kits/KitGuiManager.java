package id.velioragardens.veliorasuite.module.kits;

import id.velioragardens.veliorasuite.module.kits.model.Kit;
import id.velioragardens.veliorasuite.module.kits.model.KitGuiItem;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.enchantments.Enchantment;

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
        decorateInventory(inventory, holder);

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

            String status = kitsManager.getStatusKey(player, kit);
            List<String> lore = new ArrayList<>();
            lore.addAll(guiItem.getLore());
            lore.add("&8&m------------------------");
            lore.addAll(configManager.getStatusLore(status));

            List<String> finalLore = new ArrayList<>();
            for (String line : lore) {
                finalLore.add(configManager.color(kitsManager.applyKitPlaceholders(line, player, kit)));
            }

            meta.setLore(finalLore);
            if (status.equals("available") || status.equals("bought")) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            item.setItemMeta(meta);
        }

        return item;
    }

    private void decorateInventory(Inventory inventory, KitsGuiHolder holder) {
        ItemStack header = item(Material.NETHER_STAR, "&b&lVELIORA KITS", List.of(
                "&7Pilih kit sesuai kebutuhanmu.",
                "&8Klik kiri &7untuk claim • &8Klik kanan &7untuk preview.",
                "&8Shift + klik &7untuk membeli kit one-time."
        ));
        inventory.setItem(4, header);
        inventory.setItem(1, item(Material.GRASS_BLOCK, "&a&lSURVIVAL", List.of("&7Starter, Food, dan Build Kit.")));
        inventory.setItem(7, item(Material.AMETHYST_SHARD, "&d&lPREMIUM", List.of("&7Kit rank Premium I sampai V.")));
        inventory.setItem(46, item(Material.CLOCK, "&e&lINFO", List.of(
                "&7Hijau berkilau: siap diambil.",
                "&cMerah: masih cooldown atau terkunci.",
                "&fKlik kanan kit untuk melihat isi."
        )));
        inventory.setItem(49, item(Material.BOOK, "&b&lBANTUAN", List.of("&7Klik untuk melihat command dan cara pakai.")));
        inventory.setItem(53, item(Material.BARRIER, "&c&lTUTUP", List.of("&7Tutup menu kit.")));
        holder.setAction(49, "help");
        holder.setAction(53, "close");
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
        private final Map<Integer, String> actions = new HashMap<>();
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

        private void setAction(int slot, String action) {
            actions.put(slot, action);
        }

        public String getAction(int slot) {
            return actions.get(slot);
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
