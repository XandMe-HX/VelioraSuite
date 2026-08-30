package id.velioragardens.veliorasuite.module.fishing;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class FishingMainGuiManager implements Listener {

    private final FishingManager manager;

    public FishingMainGuiManager(FishingManager manager) {
        this.manager = manager;
    }

    public void open(Player player) {
        Holder holder = new Holder();
        Inventory inventory = Bukkit.createInventory(holder, 36, manager.getConfigManager().color("&8Veliora Fishing"));
        holder.inventory = inventory;
        frame(inventory);
        inventory.setItem(10, item(Material.CHEST, "&b&lTAS IKAN", List.of("&7Buka tas ikan virtual.", "&eKlik untuk membuka.")));
        inventory.setItem(11, item(Material.BARREL, "&a&lJUAL IKAN", List.of("&7Jual hasil tangkapan dengan aman.", "&eKlik untuk membuka toko jual.")));
        inventory.setItem(12, item(Material.FISHING_ROD, "&a&lTOKO ROD", List.of("&7Rod tier 1–16 yang dapat dibeli", "&7menggunakan Koin Fishing.")));
        inventory.setItem(14, item(Material.ENCHANTED_BOOK, "&d&lROD MISI", List.of("&7Rod tier 17–21 untuk pemancing", "&7yang telah menyelesaikan syarat.")));
        inventory.setItem(15, item(Material.BOOK, "&d&lKOLEKSI IKAN", List.of("&7Lihat seluruh ikan yang telah", "&7kamu temukan.")));
        inventory.setItem(16, item(Material.OAK_SIGN, "&e&lPERINGKAT MEMANCING", List.of("&7Lihat leaderboard pemancing.")));
        inventory.setItem(20, item(Material.SUNFLOWER, "&6&lKOIN FISHING", List.of("&7Saldo: &f" + manager.formattedCoins(player) + " Koin", "&8Terpisah dari saldo Vault.")));
        inventory.setItem(22, item(Material.AMETHYST_SHARD, "&d&lRELIC & ENCHANT ROD", List.of("&7Relic didapat saat memancing.", "&7Pelajari jalur upgrade rod.")));
        inventory.setItem(24, item(Material.POTION, "&b&lPOTION MEMANCING", List.of("&7Luck, Mutation, dan Lure Speed.", "&8Boost dibuat tanpa inflasi ekonomi.")));
        inventory.setItem(35, item(Material.BARRIER, "&c&lTUTUP", List.of("&7Tutup menu memancing.")));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder)) return;
        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) return;

        switch (slot) {
            case 10 -> openNextTick(player, () -> manager.openBagGui(player));
            case 11 -> openNextTick(player, () -> manager.openSellGui(player));
            case 12 -> openNextTick(player, () -> manager.openRodShop(player));
            case 14 -> openNextTick(player, () -> manager.openQuestRodShop(player));
            case 15 -> openNextTick(player, () -> manager.openCollectionGui(player));
            case 16 -> openNextTick(player, () -> manager.sendTop(player));
            case 22, 31 -> openNextTick(player, () -> manager.getRelicManager().openGuide(player));
            case 24 -> openNextTick(player, () -> manager.openPotionShop(player));
            case 35 -> player.closeInventory();
            default -> { }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder)) return;
        event.setCancelled(true);
    }

    private void openNextTick(Player player, Runnable action) {
        player.closeInventory();
        Bukkit.getScheduler().runTask(JavaPlugin.getProvidingPlugin(FishingMainGuiManager.class), action);
    }

    private ItemStack item(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(manager.getConfigManager().color(name));
            meta.setLore(lore.stream().map(manager.getConfigManager()::color).toList());
            item.setItemMeta(meta);
        }
        return item;
    }

    private void frame(Inventory inventory) {
        ItemStack pane = item(Material.BLACK_STAINED_GLASS_PANE, " ", List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            int row = slot / 9;
            int column = slot % 9;
            if (row == 0 || row == 3 || column == 0 || column == 8) inventory.setItem(slot, pane);
        }
    }

    private static final class Holder implements InventoryHolder {
        private Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }
}
