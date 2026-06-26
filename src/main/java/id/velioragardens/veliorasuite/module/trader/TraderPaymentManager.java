package id.velioragardens.veliorasuite.module.trader;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.module.trader.model.TraderFishRequirement;
import id.velioragardens.veliorasuite.module.trader.model.TraderPaymentType;
import id.velioragardens.veliorasuite.module.trader.model.TraderTradeItem;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class TraderPaymentManager {

    private final VelioraSuite plugin;
    private final TraderConfigManager configManager;
    private final NamespacedKey fishIdKey;
    private final NamespacedKey fishRarityKey;
    private boolean vaultWarned;

    public TraderPaymentManager(VelioraSuite plugin, TraderConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.fishIdKey = new NamespacedKey(plugin, "veliorafishing_id");
        this.fishRarityKey = new NamespacedKey(plugin, "veliorafishing_rarity");
    }

    public PaymentResult takePayment(Player player, TraderTradeItem item) {
        if (item.getPaymentType() == TraderPaymentType.FISH) return takeFish(player, item);
        return takeMoney(player, item.getMoney());
    }

    private PaymentResult takeMoney(Player player, long amount) {
        if (amount <= 0) return PaymentResult.ok();
        try {
            if (Bukkit.getPluginManager().getPlugin("Vault") == null) return PaymentResult.fail("vault");
            Class<?> economyClass = Class.forName("net.milkbowl.vault.economy.Economy");
            @SuppressWarnings({"rawtypes", "unchecked"})
            RegisteredServiceProvider<?> registration = Bukkit.getServicesManager().getRegistration((Class) economyClass);
            if (registration == null) return PaymentResult.fail("vault");
            Object economy = registration.getProvider();
            Method has = economy.getClass().getMethod("has", OfflinePlayer.class, double.class);
            boolean enough = Boolean.TRUE.equals(has.invoke(economy, player, (double) amount));
            if (!enough) return PaymentResult.fail("money");
            Method withdraw = economy.getClass().getMethod("withdrawPlayer", OfflinePlayer.class, double.class);
            withdraw.invoke(economy, player, (double) amount);
            return PaymentResult.ok();
        } catch (Exception exception) {
            if (!vaultWarned) {
                plugin.getLogger().warning("VelioraTrader: Vault economy tidak aktif/valid untuk transaksi money.");
                vaultWarned = true;
            }
            return PaymentResult.fail("vault");
        }
    }

    private PaymentResult takeFish(Player player, TraderTradeItem item) {
        if (!plugin.getModuleManager().isModuleActive("fishing")) return PaymentResult.fail("fish-module");
        if (!hasFish(player, item)) return PaymentResult.fail("fish");
        for (TraderFishRequirement requirement : item.getFishRequirements()) removeFish(player, requirement);
        return PaymentResult.ok();
    }

    private boolean hasFish(Player player, TraderTradeItem item) {
        Map<String, Integer> needed = needs(item);
        Map<String, Integer> found = new HashMap<>();
        for (ItemStack stack : player.getInventory().getContents()) {
            if (!isFish(stack)) continue;
            String id = fishId(stack);
            String rarity = fishRarity(stack);
            for (TraderFishRequirement requirement : item.getFishRequirements()) {
                if (matches(requirement, id, rarity)) {
                    String key = key(requirement);
                    found.put(key, found.getOrDefault(key, 0) + stack.getAmount());
                }
            }
        }
        for (Map.Entry<String, Integer> entry : needed.entrySet()) if (found.getOrDefault(entry.getKey(), 0) < entry.getValue()) return false;
        return true;
    }

    private void removeFish(Player player, TraderFishRequirement requirement) {
        int left = Math.max(0, requirement.amount());
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length && left > 0; slot++) {
            ItemStack stack = contents[slot];
            if (!isFish(stack) || !matches(requirement, fishId(stack), fishRarity(stack))) continue;
            int take = Math.min(left, stack.getAmount());
            stack.setAmount(stack.getAmount() - take);
            left -= take;
            if (stack.getAmount() <= 0 || stack.getType() == Material.AIR) inventory.setItem(slot, null);
            else inventory.setItem(slot, stack);
        }
    }

    private boolean isFish(ItemStack stack) {
        return stack != null && stack.hasItemMeta() && stack.getItemMeta().getPersistentDataContainer().has(fishIdKey, PersistentDataType.STRING);
    }

    private String fishId(ItemStack stack) {
        return value(stack.getItemMeta().getPersistentDataContainer(), fishIdKey);
    }

    private String fishRarity(ItemStack stack) {
        return value(stack.getItemMeta().getPersistentDataContainer(), fishRarityKey);
    }

    private String value(PersistentDataContainer pdc, NamespacedKey key) {
        String value = pdc.get(key, PersistentDataType.STRING);
        return value == null ? "" : value;
    }

    private boolean matches(TraderFishRequirement requirement, String id, String rarity) {
        boolean idMatch = requirement.fishId() == null || requirement.fishId().isBlank() || requirement.fishId().equalsIgnoreCase(id);
        boolean rarityMatch = requirement.rarity() == null || requirement.rarity().isBlank() || requirement.rarity().equalsIgnoreCase(rarity);
        return idMatch && rarityMatch;
    }

    private Map<String, Integer> needs(TraderTradeItem item) {
        Map<String, Integer> map = new HashMap<>();
        for (TraderFishRequirement requirement : item.getFishRequirements()) map.merge(key(requirement), Math.max(1, requirement.amount()), Integer::sum);
        return map;
    }

    private String key(TraderFishRequirement requirement) {
        return (requirement.fishId() == null ? "" : requirement.fishId().toLowerCase(Locale.ROOT)) + "|" + (requirement.rarity() == null ? "" : requirement.rarity().toUpperCase(Locale.ROOT));
    }

    public record PaymentResult(boolean success, String reason) {
        public static PaymentResult ok() {
            return new PaymentResult(true, "");
        }

        public static PaymentResult fail(String reason) {
            return new PaymentResult(false, reason);
        }
    }
}
