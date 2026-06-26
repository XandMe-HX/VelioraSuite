package id.velioragardens.veliorasuite.module.boss;

import id.velioragardens.veliorasuite.VelioraSuite;
import org.bukkit.entity.Player;

public final class BossQuestHook {

    private final VelioraSuite plugin;

    public BossQuestHook(VelioraSuite plugin) {
        this.plugin = plugin;
    }

    public void addMonsterHunterProgress(Player player) {
        if (player == null || !plugin.getModuleManager().isModuleActive("quest")) return;
        try {
            plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), "questadmin progress " + player.getName() + " monster_hunter 1");
        } catch (Exception ignored) {
            // Soft hook only: VelioraBoss must keep running even if VelioraQuest has no admin API/command.
        }
    }
}
