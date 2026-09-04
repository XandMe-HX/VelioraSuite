package id.velioragardens.veliorasuite.module.fishing;

import id.velioragardens.veliorasuite.core.gui.GuiLayout;
import id.velioragardens.veliorasuite.module.fishing.model.CaughtFish;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Lightweight relic drops and safe rod enhancement without external enchant dependencies. */
public final class FishingRelicManager implements Listener {

    private final FishingManager manager;
    private final NamespacedKey relicKey;
    private final NamespacedKey rodTierKey;
    private final NamespacedKey enchantKey;
    private final NamespacedKey relicRollKey;

    public FishingRelicManager(FishingManager manager) {
        this.manager = manager;
        relicKey = new NamespacedKey(manager.getConfigManager().getPlugin(), "fishing_relic");
        rodTierKey = new NamespacedKey(manager.getConfigManager().getPlugin(), "fishing_rod_tier");
        enchantKey = new NamespacedKey(manager.getConfigManager().getPlugin(), "fishing_relic_enchant");
        relicRollKey = new NamespacedKey(manager.getConfigManager().getPlugin(), "fishing_relic_roll");
    }

    public void rollDrop(Player player, CaughtFish fish) {
        double chance = switch (fish.rarity()) {
            case SECRET -> 0.35D;
            case MITOLOGI -> 0.18D;
            case LEGENDARY -> 0.08D;
            case EPIC -> 0.025D;
            default -> 0.0025D;
        };
        chance *= 1 + PatientAnglerHook.enchantBonus(player,"relic_seeker");
        if (ThreadLocalRandom.current().nextDouble() > chance) return;

        String type = fish.rarity().power() >= 6 && ThreadLocalRandom.current().nextDouble() < 0.18D
                ? "EXALTED" : fish.rarity().power() >= 5 && ThreadLocalRandom.current().nextDouble() < 0.35D
                ? "TWISTED" : "ENCHANT";
        ItemStack relic = create(type);
        manager.giveItemSafely(player, relic);
        player.sendMessage(manager.getConfigManager().color(manager.getConfigManager().getPrefix()
                + "&dKamu menemukan " + display(type) + "&d! Gabungkan dengan Fishing Rod di anvil."));
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 0.9F, 1.35F);
    }

    public void openGuide(Player player) {
        Inventory inventory = Bukkit.createInventory(null, 27, manager.getConfigManager().color("&8Fishing Relic Guide"));
        GuiLayout.decorateMenu(inventory, Material.BLACK_STAINED_GLASS_PANE, Material.PURPLE_STAINED_GLASS_PANE);
        inventory.setItem(10, create("ENCHANT"));
        inventory.setItem(12, create("TWISTED"));
        inventory.setItem(14, create("EXALTED"));
        inventory.setItem(16, guideItem());
        inventory.setItem(26, guideCloseItem());
        player.openInventory(inventory);
    }

    @EventHandler
    public void onGuideClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(manager.getConfigManager().color("&8Fishing Relic Guide"))) return;
        event.setCancelled(true);
        if (event.getRawSlot() == 26 && event.getWhoClicked() instanceof Player player) player.closeInventory();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        ItemStack rod = event.getInventory().getFirstItem();
        ItemStack relic = event.getInventory().getSecondItem();
        String type = relicType(relic);
        if (type == null || !isFishingRod(rod)) return;
        ItemStack result = rod.clone();
        if (!apply(result, type, selectedEnchant(type, relic))) return;
        event.setResult(result);
        // Custom result must not depend on creative/OP level bypass. The click handler below
        // performs the transaction server-side so Geyser's anvil UI receives the same result.
        event.getView().setRepairCost(0);
        // Invalid vanilla combinations overwrite cost AFTER PrepareAnvilEvent with -1.
        // Restore only the same, still-valid recipe next tick, never a stale result.
        ItemStack expectedRod = rod.clone();
        ItemStack expectedRelic = relic.clone();
        Bukkit.getScheduler().runTask(manager.getConfigManager().getPlugin(), () -> {
            if (event.getView().getPlayer().getOpenInventory() != event.getView()) return;
            if (!expectedRod.equals(event.getInventory().getFirstItem())
                    || !expectedRelic.equals(event.getInventory().getSecondItem())) return;
            event.getInventory().setResult(result.clone());
            event.getView().setRepairCost(0);
            if (event.getView().getPlayer() instanceof Player viewer) viewer.updateInventory();
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onTakeAnvil(InventoryClickEvent event) {
        if (event.getInventory().getType() != org.bukkit.event.inventory.InventoryType.ANVIL || event.getRawSlot() != 2) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack rod = event.getInventory().getItem(0);
        ItemStack relic = event.getInventory().getItem(1);
        String type = relicType(relic);
        if (type == null || !isFishingRod(rod)) return;
        ItemStack result = rod.clone();
        result.setAmount(1);
        if (!apply(result, type, selectedEnchant(type, relic))) return;

        event.setCancelled(true);
        if (!event.isLeftClick() && !event.isRightClick()) return;
        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage(manager.getConfigManager().color("&cKosongkan satu slot inventory dahulu. Relic belum dipakai."));
            return;
        }
        event.getInventory().setItem(0, decrement(rod));
        event.getInventory().setItem(1, decrement(relic));
        event.getInventory().setItem(2, null);
        player.getInventory().addItem(result);
        player.updateInventory();
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0F, 1.2F);
        player.sendMessage(manager.getConfigManager().color(manager.getConfigManager().getPrefix()
                + "&aRelic berhasil diterapkan ke Fishing Rod."));
    }

    private ItemStack decrement(ItemStack source) {
        if (source == null || source.getAmount() <= 1) return null;
        ItemStack remaining = source.clone();
        remaining.setAmount(source.getAmount() - 1);
        return remaining;
    }

    private ItemStack create(String type) {
        ItemStack item = new ItemStack(type.equals("EXALTED") ? Material.ECHO_SHARD
                : type.equals("TWISTED") ? Material.PRISMARINE_CRYSTALS : Material.AMETHYST_SHARD);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(display(type), TextColor.color(type.equals("EXALTED") ? 0xFF70D8 : 0x7EE8FF)));
        meta.lore(List.of(
                Component.text("Relic langka dari hasil memancing.", TextColor.color(0xB8C4D2)),
                Component.text("Pasang: Rod di slot kiri, Relic di slot kanan anvil.", TextColor.color(0x70E0C0)),
                Component.text("Satu relic hanya dapat dipakai sekali.", TextColor.color(0x8391A5))));
        meta.getPersistentDataContainer().set(relicKey, PersistentDataType.STRING, type);
        meta.getPersistentDataContainer().set(relicRollKey, PersistentDataType.STRING, java.util.UUID.randomUUID().toString());
        item.setItemMeta(meta);
        return item;
    }

    private boolean isFishingRod(ItemStack item) {
        return item != null && item.getType() == Material.FISHING_ROD && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(rodTierKey, PersistentDataType.INTEGER);
    }

    private boolean apply(ItemStack rod, String type, String enchant) {
        if (!isFishingRod(rod)) return false;
        ItemMeta meta = rod.getItemMeta();
        if (meta.getPersistentDataContainer().has(enchantKey, PersistentDataType.STRING)) return false;
        int bonus = type.equals("EXALTED") ? 3 : type.equals("TWISTED") ? 2 : 1;
        meta.getPersistentDataContainer().set(enchantKey, PersistentDataType.STRING, enchant);
        meta.addEnchant(Enchantment.LUCK_OF_THE_SEA, Math.min(10, meta.getEnchantLevel(Enchantment.LUCK_OF_THE_SEA) + bonus), true);
        meta.addEnchant(Enchantment.LURE, Math.min(10, meta.getEnchantLevel(Enchantment.LURE) + bonus), true);
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        lore.add(Component.text("Relic Enchant: " + enchant, TextColor.color(0xD87CFF)));
        meta.lore(lore);
        rod.setItemMeta(meta);
        return true;
    }


    private ItemStack guideItem() {
        ItemStack item = new ItemStack(Material.ANVIL);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(manager.getConfigManager().color("&bCara Memasang"));
        meta.setLore(List.of("&71. Taruh Veliora Fishing Rod di kiri.", "&72. Taruh satu Relic di kanan.", "&73. Ambil hasil enchant di anvil.").stream().map(manager.getConfigManager()::color).toList());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack guideCloseItem() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(manager.getConfigManager().color("&cTutup Panduan"));
        meta.setLore(List.of(manager.getConfigManager().color("&7Kembali ke menu Fishing.")));
        item.setItemMeta(meta);
        return item;
    }

    private String relicType(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return null;
        String type = item.getItemMeta().getPersistentDataContainer().get(relicKey, PersistentDataType.STRING);
        return type != null && java.util.Set.of("ENCHANT", "TWISTED", "EXALTED").contains(type) ? type : null;
    }

    private String selectedEnchant(String type, ItemStack relic) {
        String[] normal = {"Tidal Fortune I", "Deep Lure I", "Weight Hunter I"};
        String[] twisted = {"Abyssal Fortune II", "Storm Lure II", "Mutation Seeker II"};
        String[] exalted = {"Leviathan Blessing III", "Ocean Sovereign III", "Mythic Hunter III"};
        String[] pool = type.equals("EXALTED") ? exalted : type.equals("TWISTED") ? twisted : normal;
        String roll = relic != null && relic.hasItemMeta()
                ? relic.getItemMeta().getPersistentDataContainer().get(relicRollKey, PersistentDataType.STRING) : null;
        int hash = roll == null ? (type + ":legacy").hashCode() : roll.hashCode();
        return pool[Math.floorMod(hash, pool.length)];
    }

    private String display(String type) {
        return switch (type) {
            case "EXALTED" -> "Exalted Fishing Relic";
            case "TWISTED" -> "Twisted Fishing Relic";
            default -> "Fishing Enchant Relic";
        };
    }
}
