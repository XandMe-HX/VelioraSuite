package id.velioragardens.veliorasuite.module.quest.listener;

import id.velioragardens.veliorasuite.module.quest.QuestManager;
import id.velioragardens.veliorasuite.module.quest.model.QuestCategory;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class QuestFarmListener implements Listener {

    private final QuestManager manager;
    private final Map<BlockKey, Long> plantedAt = new HashMap<>();

    public QuestFarmListener(QuestManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlant(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (manager.getConfigManager().getMaterials(QuestCategory.FARMER, "harvest-materials").contains(block.getType())) {
            plantedAt.put(BlockKey.of(block), System.currentTimeMillis());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHarvest(BlockBreakEvent event) {
        Block block = event.getBlock();
        Material material = block.getType();
        if (!manager.getConfigManager().getMaterials(QuestCategory.FARMER, "harvest-materials").contains(material)) {
            return;
        }

        // Only fully-grown Ageable crops count. Planting, hoeing, and instant break loops do not.
        if (!(block.getBlockData() instanceof Ageable ageable) || ageable.getAge() < ageable.getMaximumAge()) {
            return;
        }
        Long planted = plantedAt.remove(BlockKey.of(block));
        long minimumAge = manager.getConfigManager().getFarmMinimumGrowthSeconds() * 1000L;
        if (planted != null && System.currentTimeMillis() - planted < minimumAge) return;
        manager.addProgress(event.getPlayer(), QuestCategory.FARMER, 1);
    }

    private record BlockKey(UUID world, int x, int y, int z) {
        private static BlockKey of(Block block) {
            return new BlockKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        }
    }
}
