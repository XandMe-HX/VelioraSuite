package id.velioragardens.veliorasuite.module.trader;

import id.velioragardens.veliorasuite.VelioraSuite;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public final class TraderFishingHook {

    private TraderFishingHook() {
    }

    public static int getFishingLuckBonus(Player player) {
        if (player == null) return 0;
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || !hand.hasItemMeta()) return 0;
        NamespacedKey key = new NamespacedKey(VelioraSuite.getInstance(), "velioratrader_fishing_luck_bonus");
        Integer value = hand.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.INTEGER);
        return value == null ? 0 : Math.max(0, value);
    }
}
