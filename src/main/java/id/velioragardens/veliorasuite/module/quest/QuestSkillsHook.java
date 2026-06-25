package id.velioragardens.veliorasuite.module.quest;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.api.VelioraModule;
import id.velioragardens.veliorasuite.module.skills.SkillsApi;
import id.velioragardens.veliorasuite.module.skills.SkillsModule;
import org.bukkit.entity.Player;

import java.util.Optional;

public final class QuestSkillsHook {

    private final VelioraSuite plugin;

    public QuestSkillsHook(VelioraSuite plugin) {
        this.plugin = plugin;
    }

    public boolean isAvailable() {
        return api() != null;
    }

    public int getQuestManaCost(int level) {
        SkillsApi api = api();
        return api == null ? fallbackCost(level) : api.getQuestManaCost(level);
    }

    public boolean hasMana(Player player, int amount) {
        SkillsApi api = api();
        return api != null && api.hasMana(player, amount);
    }

    public boolean takeMana(Player player, int amount, String reason) {
        SkillsApi api = api();
        return api != null && api.takeMana(player, amount, reason);
    }

    public void giveMana(Player player, int amount, String reason) {
        SkillsApi api = api();
        if (api != null) api.giveMana(player, amount, reason);
    }

    private SkillsApi api() {
        if (plugin.getModuleManager() == null) return null;
        Optional<VelioraModule> module = plugin.getModuleManager().getModule("skills");
        if (module.isEmpty() || !(module.get() instanceof SkillsModule skillsModule)) return null;
        return skillsModule.getApi();
    }

    private int fallbackCost(int level) {
        if (level <= 4) return 1;
        if (level <= 9) return 2;
        if (level <= 14) return 3;
        return 4;
    }
}
