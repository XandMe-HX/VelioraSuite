package id.velioragardens.veliorasuite.module.quest.listener;

import id.velioragardens.veliorasuite.module.quest.QuestManager;
import id.velioragardens.veliorasuite.module.quest.model.QuestCategory;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

public final class QuestBlockListener implements Listener {

    private final QuestManager manager;

    public QuestBlockListener(QuestManager manager) {
        this.manager = manager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Material type = event.getBlock().getType();
        if (manager.getConfigManager().getMaterials(QuestCategory.WOODCUTTING, "materials").contains(type)) {
            manager.addProgress(event.getPlayer(), QuestCategory.WOODCUTTING, 1);
        }
        if (manager.getConfigManager().getMaterials(QuestCategory.MINING, "materials").contains(type)) {
            manager.addProgress(event.getPlayer(), QuestCategory.MINING, 1);
        }
    }
}
