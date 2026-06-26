package id.velioragardens.veliorasuite.module.fishing;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.api.VelioraModule;
import id.velioragardens.veliorasuite.module.quest.QuestModule;
import id.velioragardens.veliorasuite.module.quest.model.QuestCategory;
import org.bukkit.entity.Player;

import java.util.Optional;

public final class FishingQuestHook {

    private final VelioraSuite plugin;
    private final FishingConfigManager configManager;

    public FishingQuestHook(VelioraSuite plugin, FishingConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    public void addFishingProgress(Player player) {
        if (player == null || !configManager.isQuestFishingProgressEnabled() || plugin.getModuleManager() == null) return;
        Optional<VelioraModule> module = plugin.getModuleManager().getModule("quest");
        if (module.isEmpty() || !(module.get() instanceof QuestModule questModule) || questModule.getQuestManager() == null) return;
        questModule.getQuestManager().addProgress(player, QuestCategory.FISHING, 1);
    }
}
