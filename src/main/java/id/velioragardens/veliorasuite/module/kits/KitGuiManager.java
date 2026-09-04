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
        open(player, false);
    }
    public void open(Player player, boolean premium) {
        KitsGuiHolder holder = new KitsGuiHolder();
        Inventory inventory = Bukkit.createInventory(holder, 54, premium ? configManager.color("&8Veliora Kits | Premium") : configManager.getGuiTitle());
        holder.setInventory(inventory);

        fillInventory(inventory);
        decorateInventory(inventory, holder, premium);

        int index=0;
        java.util.Set<Integer> used = new java.util.HashSet<>();
        for (Kit kit : configManager.getEnabledKits().stream()
                .sorted(java.util.Comparator.comparingInt(Kit::getPremiumLevel).thenComparing(Kit::getId)).toList()) {
            if((kit.getPremiumLevel()>0)!=premium)continue;
            KitGuiItem guiItem = kit.getGuiItem();
            int slot = KitMenuLayout.slot(kit.getId(), premium, premium ? 20+index++ : guiItem.getSlot(), used);

            if (slot < 0 || slot >= inventory.getSize()) {
                continue;
            }

            inventory.setItem(slot, buildKitIcon(player, kit));
            used.add(slot);
            holder.setKit(slot, kit.getId());
        }
        if(premium) {
            inventory.setItem(48,item(Material.ARROW,"&e&lKEMBALI",List.of("&7Kembali ke kit umum.")));
            holder.setAction(48,"main");
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

    private void decorateInventory(Inventory inventory, KitsGuiHolder holder, boolean premium) {
        ItemStack header = item(Material.NETHER_STAR, "&b&lVELIORA KITS", List.of(
                "&7Pilih kit sesuai kebutuhanmu.",
                "&7Klik kit untuk melihat isi dan harganya.",
                "&7Ambil melalui tombol konfirmasi di preview."
        ));
        inventory.setItem(4, header);
        if (!premium) {
            inventory.setItem(31, item(Material.NETHERITE_CHESTPLATE, "&d&lKIT PREMIUM", List.of(
                    "&7Buka pilihan Premium I sampai V.",
                    "&7Pengambilan sesuai izin rank premium.",
                    "&eKlik untuk melihat semua kit premium.")));
            holder.setAction(31, "premium");
        }
        inventory.setItem(45, item(Material.CLOCK, "&e&lINFO", List.of(
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
