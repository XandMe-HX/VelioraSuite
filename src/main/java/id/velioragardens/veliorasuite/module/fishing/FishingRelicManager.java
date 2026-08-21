package id.velioragardens.veliorasuite.module.fishing;

import id.velioragardens.veliorasuite.module.fishing.model.CaughtFish;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
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

    public FishingRelicManager(FishingManager manager) {
        this.manager = manager;
        relicKey = new NamespacedKey(manager.getConfigManager().getPlugin(), "fishing_relic");
        rodTierKey = new NamespacedKey(manager.getConfigManager().getPlugin(), "fishing_rod_tier");
        enchantKey = new NamespacedKey(manager.getConfigManager().getPlugin(), "fishing_relic_enchant");
    }

    public void rollDrop(Player player, CaughtFish fish) {
        double chance = switch (fish.rarity()) {
            case SECRET -> 0.35D;
            case MITOLOGI -> 0.18D;
            case LEGENDARY -> 0.08D;
            case EPIC -> 0.025D;
            default -> 0.0025D;
        };
        if (ThreadLocalRandom.current().nextDouble() > chance) return;

        String type = fish.rarity().power() >= 6 && ThreadLocalRandom.current().nextDouble() < 0.18D
                ? "EXALTED" : fish.rarity().power() >= 5 && ThreadLocalRandom.current().nextDouble() < 0.35D
                ? "TWISTED" : "ENCHANT";
        ItemStack relic = create(type);
        manager.giveItemSafely(player, relic);
        player.sendMessage(manager.getConfigManager().color(manager.getConfigManager().getPrefix()
                + "&dKamu menemukan " + display(type) + "&d! Seret relic ke Fishing Rod."));
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 0.9F, 1.35F);
    }

    @EventHandler(ignoreCancelled = true)
    public void onApply(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack relic = event.getCursor();
        ItemStack rod = event.getCurrentItem();
        String type = relicType(relic);
        if (type == null || rod == null || rod.getType() != Material.FISHING_ROD || !rod.hasItemMeta()) return;

        ItemMeta meta = rod.getItemMeta();
        if (!meta.getPersistentDataContainer().has(rodTierKey, PersistentDataType.INTEGER)) return;
        event.setCancelled(true);
        if (meta.getPersistentDataContainer().has(enchantKey, PersistentDataType.STRING)) {
            player.sendMessage(manager.getConfigManager().color(manager.getConfigManager().getPrefix()
                    + "&eRod ini sudah memiliki Relic Enchant."));
            return;
        }

        String enchant = randomEnchant(type);
        int bonus = type.equals("EXALTED") ? 3 : type.equals("TWISTED") ? 2 : 1;
        meta.getPersistentDataContainer().set(enchantKey, PersistentDataType.STRING, enchant);
        meta.addEnchant(Enchantment.LUCK_OF_THE_SEA,
                Math.min(10, meta.getEnchantLevel(Enchantment.LUCK_OF_THE_SEA) + bonus), true);
        meta.addEnchant(Enchantment.LURE, Math.min(10, meta.getEnchantLevel(Enchantment.LURE) + bonus), true);
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        lore.add(Component.text("Relic Enchant: " + enchant, TextColor.color(0xD87CFF)));
        meta.lore(lore);
        rod.setItemMeta(meta);

        ItemStack remainder = relic.clone();
        remainder.setAmount(relic.getAmount() - 1);
        event.setCursor(remainder.getAmount() <= 0 ? null : remainder);
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0F, 1.2F);
        player.sendMessage(manager.getConfigManager().color(manager.getConfigManager().getPrefix()
                + "&aRelic berhasil diterapkan: &d" + enchant));
    }

    private ItemStack create(String type) {
        ItemStack item = new ItemStack(type.equals("EXALTED") ? Material.ECHO_SHARD
                : type.equals("TWISTED") ? Material.PRISMARINE_CRYSTALS : Material.AMETHYST_SHARD);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(display(type), TextColor.color(type.equals("EXALTED") ? 0xFF70D8 : 0x7EE8FF)));
        meta.lore(List.of(
                Component.text("Relic langka dari hasil memancing.", TextColor.color(0xB8C4D2)),
                Component.text("Seret ke Veliora Fishing Rod.", TextColor.color(0x70E0C0)),
                Component.text("Satu relic hanya dapat dipakai sekali.", TextColor.color(0x8391A5))));
        meta.getPersistentDataContainer().set(relicKey, PersistentDataType.STRING, type);
        item.setItemMeta(meta);
        return item;
    }

    private String relicType(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(relicKey, PersistentDataType.STRING);
    }

    private String randomEnchant(String type) {
        String[] normal = {"Tidal Fortune I", "Deep Lure I", "Weight Hunter I"};
        String[] twisted = {"Abyssal Fortune II", "Storm Lure II", "Mutation Seeker II"};
        String[] exalted = {"Leviathan Blessing III", "Ocean Sovereign III", "Mythic Hunter III"};
        String[] pool = type.equals("EXALTED") ? exalted : type.equals("TWISTED") ? twisted : normal;
        return pool[ThreadLocalRandom.current().nextInt(pool.length)];
    }

    private String display(String type) {
        return switch (type) {
            case "EXALTED" -> "Exalted Fishing Relic";
            case "TWISTED" -> "Twisted Fishing Relic";
            default -> "Fishing Enchant Relic";
        };
    }
}
