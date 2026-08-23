package id.velioragardens.veliorasuite.module.fishing;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
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
        Inventory inventory = Bukkit.createInventory(null, 36, manager.getConfigManager().color("&8VelioraFishing"));
        inventory.setItem(10, item(Material.CHEST, "&bFish Bag", List.of("&7Buka tas ikan virtual.", "&f/fish bag")));
        inventory.setItem(11, item(Material.BARREL, "&aSell Fish", List.of("&7Buka GUI jual ikan.", "&f/fish sell")));
        inventory.setItem(12, item(Material.FISHING_ROD, "&aRod Shop", List.of("&7Rod tier 1-16 yang dapat dibeli", "&7menggunakan Koin Fishing.")));
        inventory.setItem(14, item(Material.ENCHANTED_BOOK, "&dQuest Rods", List.of("&7Rod tier 17-21 untuk pemancing", "&7yang menuntaskan syarat panjang.")));
        inventory.setItem(15, item(Material.BOOK, "&dFish Collection", List.of("&7Lihat ikan yang sudah kamu temukan.", "&f/fish collection")));
        inventory.setItem(16, item(Material.OAK_SIGN, "&eFish Top", List.of("&7Lihat leaderboard gabungan.", "&f/fish top")));
        inventory.setItem(20, item(Material.SUNFLOWER, "&6Fishing Coins", List.of("&7Saldo: &f" + manager.formattedCoins(player) + " Koin", "&7Terpisah aman dari Vault.")));
        inventory.setItem(22, item(Material.AMETHYST_SHARD, "&dRelic & Rod Enchant", List.of("&7Relic dapat ditemukan saat memancing.", "&7Sistem altar enchant tersedia bertahap.")));
        inventory.setItem(24, item(Material.POTION, "&bFishing Potions", List.of("&7Luck, Mutation, dan Lure Speed.", "&7Boost tidak menyentuh ekonomi utama.")));
        inventory.setItem(31, item(Material.ANVIL, "&dRelic Altar", List.of("&7Gabungkan Fishing Rod + Relic", "&7menggunakan anvil vanilla.")));
        inventory.setItem(35, item(Material.BARRIER, "&cClose", List.of("&7Tutup menu.")));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().equals(manager.getConfigManager().color("&8VelioraFishing"))) return;
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
        if (!event.getView().getTitle().equals(manager.getConfigManager().color("&8VelioraFishing"))) return;
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
}
