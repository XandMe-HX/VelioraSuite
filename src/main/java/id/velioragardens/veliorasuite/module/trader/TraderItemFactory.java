package id.velioragardens.veliorasuite.module.trader;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.module.trader.model.TraderPaymentType;
import id.velioragardens.veliorasuite.module.trader.model.TraderTradeItem;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class TraderItemFactory {

    private final TraderConfigManager configManager;
    private final NamespacedKey itemIdKey;
    private final NamespacedKey unrepairableKey;
    private final NamespacedKey customDamageKey;
    private final NamespacedKey fishingLuckBonusKey;

    public TraderItemFactory(VelioraSuite plugin, TraderConfigManager configManager) {
        this.configManager = configManager;
        this.itemIdKey = new NamespacedKey(plugin, "velioratrader_item_id");
        this.unrepairableKey = new NamespacedKey(plugin, "velioratrader_unrepairable");
        this.customDamageKey = new NamespacedKey(plugin, "velioratrader_custom_damage");
        this.fishingLuckBonusKey = new NamespacedKey(plugin, "velioratrader_fishing_luck_bonus");
    }

    public ItemStack createTradeDisplay(TraderTradeItem item, boolean soldOut) {
        if (soldOut) {
            ItemStack barrier = new ItemStack(Material.BARRIER);
            ItemMeta meta = barrier.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(configManager.color("&cSold Out"));
                meta.setLore(List.of(configManager.color("&cSold Out")));
                barrier.setItemMeta(meta);
            }
            return barrier;
        }
        ItemStack display = createReward(item);
        ItemMeta meta = display.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add(configManager.color(" "));
            if (item.getPaymentType() == TraderPaymentType.MONEY) lore.add(configManager.color("&7Harga: &a" + item.getMoney()));
            else lore.add(configManager.color("&7Harga: &bIkan khusus"));
            lore.add(configManager.color("&7Stock: &f" + item.getStock()));
            lore.add(configManager.color("&eKlik untuk membeli."));
            meta.setLore(lore);
            display.setItemMeta(meta);
        }
        return display;
    }

    public ItemStack createReward(TraderTradeItem item) {
        ItemStack stack = new ItemStack(item.getMaterial(), item.getAmount());
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(configManager.color(item.getName()));
            List<String> lore = new ArrayList<>();
            for (String line : item.getLore()) lore.add(configManager.color(line));
            if (item.isUnrepairable()) lore.add(configManager.color("&cTidak bisa direpair."));
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(itemIdKey, PersistentDataType.STRING, item.getId());
            if (item.isUnrepairable()) pdc.set(unrepairableKey, PersistentDataType.BYTE, (byte) 1);
            if (item.getCustomDamage() > 0) pdc.set(customDamageKey, PersistentDataType.INTEGER, item.getCustomDamage());
            if (item.getFishingLuckBonus() > 0) pdc.set(fishingLuckBonusKey, PersistentDataType.INTEGER, item.getFishingLuckBonus());
            if (meta instanceof Damageable damageable && item.getMaterial().getMaxDurability() > 0) {
                damageable.setDamage(item.getMaterial().getMaxDurability() / 2);
            }
            stack.setItemMeta(meta);
        }
        for (String enchantLine : item.getEnchantments()) applyEnchant(stack, enchantLine);
        return stack;
    }

    public boolean isTraderItem(ItemStack item) {
        return item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(itemIdKey, PersistentDataType.STRING);
    }

    public boolean isUnrepairable(ItemStack item) {
        return item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(unrepairableKey, PersistentDataType.BYTE);
    }

    public int customDamage(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;
        Integer value = item.getItemMeta().getPersistentDataContainer().get(customDamageKey, PersistentDataType.INTEGER);
        return value == null ? 0 : Math.max(0, value);
    }

    public NamespacedKey getFishingLuckBonusKey() { return fishingLuckBonusKey; }
    public NamespacedKey getCustomDamageKey() { return customDamageKey; }
    public NamespacedKey getItemIdKey() { return itemIdKey; }

    private void applyEnchant(ItemStack stack, String enchantLine) {
        if (enchantLine == null || enchantLine.isBlank()) return;
        String[] parts = enchantLine.split(":");
        String name = parts[0].trim().toUpperCase(Locale.ROOT);
        int level = 1;
        if (parts.length > 1) {
            try { level = Integer.parseInt(parts[1].trim()); } catch (NumberFormatException ignored) { level = 1; }
        }
        Enchantment enchantment = Enchantment.getByName(name);
        if (enchantment != null) stack.addUnsafeEnchantment(enchantment, level);
    }
}
