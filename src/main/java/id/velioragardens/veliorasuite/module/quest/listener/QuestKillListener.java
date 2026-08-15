package id.velioragardens.veliorasuite.module.quest.listener;

import id.velioragardens.veliorasuite.module.quest.QuestManager;
import id.velioragardens.veliorasuite.module.quest.model.QuestCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

public final class QuestKillListener implements Listener {

    private final QuestManager manager;

    public QuestKillListener(QuestManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;
        if (event.getEntity().getSpawnReason() == org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.SPAWNER
                || event.getEntity().getSpawnReason() == org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.SPAWNER_EGG) {
            return;
        }
        if (manager.getConfigManager().getEntities(QuestCategory.MONSTER_HUNTER).contains(event.getEntityType())) {
            manager.addProgress(killer, QuestCategory.MONSTER_HUNTER, 1);
        }
        if (manager.getConfigManager().getEntities(QuestCategory.ANIMAL_HUNTER).contains(event.getEntityType())) {
            manager.addProgress(killer, QuestCategory.ANIMAL_HUNTER, 1);
        }
    }
}
