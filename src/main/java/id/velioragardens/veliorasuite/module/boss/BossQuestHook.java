package id.velioragardens.veliorasuite.module.boss;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.api.VelioraModule;
import id.velioragardens.veliorasuite.module.quest.QuestModule;
import id.velioragardens.veliorasuite.module.quest.model.QuestCategory;
import org.bukkit.entity.Player;

import java.util.Optional;

public final class BossQuestHook {

    private final VelioraSuite plugin;

    public BossQuestHook(VelioraSuite plugin) {
        this.plugin = plugin;
    }

    public void addMonsterHunterProgress(Player player) {
        if (player == null || plugin.getModuleManager() == null || !plugin.getModuleManager().isModuleActive("quest")) return;
        Optional<VelioraModule> module = plugin.getModuleManager().getModule("quest");
        if (module.isEmpty() || !(module.get() instanceof QuestModule questModule) || questModule.getQuestManager() == null) return;
        questModule.getQuestManager().addProgress(player, QuestCategory.MONSTER_HUNTER, 1);
    }
}
