package id.velioragardens.veliorasuite.module.clearlag;

import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class ClearLagItemCleaner {

    private final ClearLagConfigManager configManager;

    public ClearLagItemCleaner(ClearLagConfigManager configManager) {
        this.configManager = configManager;
    }

    public int clear(World world) {
        if (world == null || !configManager.isItemCleanerEnabled() || !configManager.isRemoveDroppedItems()) return 0;
        int removed = 0;
        for (Item item : world.getEntitiesByClass(Item.class)) {
            if (shouldKeep(item)) continue;
            item.remove();
            removed++;
        }
        return removed;
    }

    private boolean shouldKeep(Item item) {
        if (item == null || item.isDead() || !item.isValid()) return true;
        if (configManager.isIgnorePluginMetadataItems() && (!item.getMetadata("VelioraSuite").isEmpty() || !item.getScoreboardTags().isEmpty())) return true;
        ItemStack stack = item.getItemStack();
        if (stack == null || stack.getType().isAir()) return true;
        if (configManager.getIgnoredMaterials().contains(stack.getType())) return true;

        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return false;
        if (configManager.isIgnoreNamedItems() && (meta.hasDisplayName() || item.getCustomName() != null)) return true;
        if (configManager.isIgnoreLoreItems() && meta.hasLore()) return true;
        return configManager.isIgnoreEnchantedItems() && meta.hasEnchants();
    }
}
