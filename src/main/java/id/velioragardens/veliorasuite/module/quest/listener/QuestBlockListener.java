package id.velioragardens.veliorasuite.module.quest.listener;

import id.velioragardens.veliorasuite.module.quest.QuestManager;
import id.velioragardens.veliorasuite.module.quest.model.QuestCategory;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class QuestBlockListener implements Listener {

    private final QuestManager manager;
    private final Set<BlockKey> playerPlacedBlocks = new HashSet<>();

    public QuestBlockListener(QuestManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        playerPlacedBlocks.add(BlockKey.of(event.getBlockPlaced().getWorld().getUID(),
                event.getBlockPlaced().getX(), event.getBlockPlaced().getY(), event.getBlockPlaced().getZ()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        BlockKey key = BlockKey.of(event.getBlock().getWorld().getUID(),
                event.getBlock().getX(), event.getBlock().getY(), event.getBlock().getZ());
        if (playerPlacedBlocks.remove(key)) return;
        Material type = event.getBlock().getType();
        if (manager.getConfigManager().getMaterials(QuestCategory.WOODCUTTING, "materials").contains(type)) {
            manager.addProgress(event.getPlayer(), QuestCategory.WOODCUTTING, 1);
        }
        if (manager.getConfigManager().getMaterials(QuestCategory.MINING, "materials").contains(type)) {
            manager.addProgress(event.getPlayer(), QuestCategory.MINING, 1);
        }
    }
    private record BlockKey(UUID world, int x, int y, int z) {
        private static BlockKey of(UUID world, int x, int y, int z) {
            return new BlockKey(world, x, y, z);
        }
    }
}
