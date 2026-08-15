package id.velioragardens.veliorasuite.module.quest.listener;

import id.velioragardens.veliorasuite.module.quest.QuestManager;
import id.velioragardens.veliorasuite.module.quest.model.QuestCategory;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

public final class QuestFarmListener implements Listener {

    private final QuestManager manager;

    public QuestFarmListener(QuestManager manager) {
        this.manager = manager;
    }

    @EventHandler(ignoreCancelled = true)
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
        manager.addProgress(event.getPlayer(), QuestCategory.FARMER, 1);
    }
}
