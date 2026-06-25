package id.velioragardens.veliorasuite.module.quest.listener;

import id.velioragardens.veliorasuite.module.quest.QuestManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;

public final class QuestCommandTrackListener implements Listener {

    private final QuestManager manager;

    public QuestCommandTrackListener(QuestManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        manager.getStarterManager().ensureStarter(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        manager.getStarterManager().trackCommand(event.getPlayer(), event.getMessage());
    }
}
