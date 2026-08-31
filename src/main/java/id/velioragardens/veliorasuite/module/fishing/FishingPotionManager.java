package id.velioragardens.veliorasuite.module.fishing;

import id.velioragardens.veliorasuite.core.gui.GuiLayout;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Coin sink with restart-safe fishing boosts. Purchases require a second click. */
public final class FishingPotionManager implements Listener {

    private static final String TITLE = "§8Fishing Potions";
    private final FishingManager manager;
    private final Map<UUID, Pending> pending = new HashMap<>();

    public FishingPotionManager(FishingManager manager) {
        this.manager = manager;
    }

    public void open(Player player) {
        Inventory inventory = Bukkit.createInventory(null, 27, TITLE);
        GuiLayout.decorateMenu(inventory, Material.BLACK_STAINED_GLASS_PANE, Material.LIGHT_BLUE_STAINED_GLASS_PANE);
        inventory.setItem(11, potion(player, "luck", Material.POTION, "Luck Potion", "Rare chance +50%"));
        inventory.setItem(13, potion(player, "mutation", Material.SPLASH_POTION, "Mutation Potion", "Mutation chance x2"));
        inventory.setItem(15, potion(player, "lure", Material.LINGERING_POTION, "Lure Speed Potion", "Waktu tunggu -25%"));
        inventory.setItem(22, simple(Material.SUNFLOWER, "Saldo: " + manager.formattedCoins(player) + " Koin",
                List.of("Ekonomi khusus VelioraFishing.")));
        inventory.setItem(26, simple(Material.BARRIER, "Tutup", List.of("Tutup toko potion.")));
        player.openInventory(inventory);
    }

    public boolean active(Player player, String type) {
        Long until = player.getPersistentDataContainer().get(key(type), PersistentDataType.LONG);
        return until != null && until > System.currentTimeMillis();
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !event.getView().getTitle().equals(TITLE)) return;
        event.setCancelled(true);
        if (event.getRawSlot() == 26) { player.closeInventory(); return; }
        String type = switch (event.getRawSlot()) { case 11 -> "luck"; case 13 -> "mutation"; case 15 -> "lure"; default -> null; };
        if (type == null) return;
        long now = System.currentTimeMillis();
        Pending confirmation = pending.get(player.getUniqueId());
        if (confirmation == null || !confirmation.type.equals(type) || confirmation.expiresAt < now) {
            pending.put(player.getUniqueId(), new Pending(type, now + 10_000L));
            player.sendMessage(manager.getConfigManager().color(manager.getConfigManager().getPrefix()
                    + "&eKlik potion yang sama sekali lagi dalam 10 detik untuk konfirmasi."));
            return;
        }
        pending.remove(player.getUniqueId());
        int price = manager.getConfigManager().getPotionPrice(type);
        if (!manager.withdrawRodCost(player, price)) {
            player.sendMessage(manager.getConfigManager().color(manager.getConfigManager().getPrefix() + "&cKoin Fishing tidak cukup."));
            return;
        }
        long duration = manager.getConfigManager().getPotionDurationSeconds(type) * 1000L;
        player.getPersistentDataContainer().set(key(type), PersistentDataType.LONG, now + duration);
        player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_DRINK, 1.0F, 1.15F);
        player.sendMessage(manager.getConfigManager().color(manager.getConfigManager().getPrefix()
                + "&a" + display(type) + " aktif selama &f" + (duration / 60_000L) + " menit&a."));
        open(player);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTitle().equals(TITLE)) event.setCancelled(true);
    }

    private ItemStack potion(Player player, String type, Material material, String name, String effect) {
        int price = manager.getConfigManager().getPotionPrice(type);
        int minutes = manager.getConfigManager().getPotionDurationSeconds(type) / 60;
        return simple(material, name, List.of(effect, "Durasi: " + minutes + " menit", "Harga: "
                + manager.getConfigManager().formatCoins(price) + " Koin", active(player, type) ? "Status: AKTIF" : "Klik dua kali untuk membeli"));
    }

    private ItemStack simple(Material material, String name, List<String> loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, TextColor.color(0x55E6FF)));
        meta.lore(loreLines.stream().map(line -> Component.text(line, TextColor.color(0xB8C4D2))).toList());
        item.setItemMeta(meta);
        return item;
    }

    private NamespacedKey key(String type) {
        return new NamespacedKey(manager.getConfigManager().getPlugin(), "fishing_potion_" + type);
    }

    private String display(String type) {
        return switch (type) { case "mutation" -> "Mutation Potion"; case "lure" -> "Lure Speed Potion"; default -> "Luck Potion"; };
    }

    private record Pending(String type, long expiresAt) { }
}
