package id.velioragardens.veliorasuite.module.quest.listener;

import id.velioragardens.veliorasuite.module.quest.QuestManager;
import id.velioragardens.veliorasuite.module.quest.model.QuestCategory;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.FurnaceExtractEvent;

public final class QuestCookingListener implements Listener {

    private final QuestManager manager;

    public QuestCookingListener(QuestManager manager) {
        this.manager = manager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onFurnaceExtract(FurnaceExtractEvent event) {
        if (manager.getConfigManager().getMaterials(QuestCategory.CHEF, "cooked-items").contains(event.getItemType())) {
            manager.addProgress(event.getPlayer(), QuestCategory.CHEF, Math.max(1, event.getItemAmount()));
        }
    }
}
