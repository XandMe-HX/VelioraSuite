package id.velioragardens.veliorasuite.placeholder;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.module.quest.QuestModule;
import id.velioragardens.veliorasuite.module.quest.model.PlayerCategoryProgress;
import id.velioragardens.veliorasuite.module.skills.SkillsModule;
import id.velioragardens.veliorasuite.module.team.TeamModule;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;

import java.util.Locale;

/** PlaceholderAPI internal expansion; no eCloud download is needed. */
public final class VelioraPlaceholderExpansion extends PlaceholderExpansion {
    private final VelioraSuite plugin;

    public VelioraPlaceholderExpansion(VelioraSuite plugin) {
        this.plugin = plugin;
    }

    @Override public String getIdentifier() { return "veliorasuite"; }
    @Override public String getAuthor() { return "Veliora Gardens"; }
    @Override public String getVersion() { return plugin.getDescription().getVersion(); }
    @Override public boolean persist() { return true; }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        if (player == null || params == null) return "";
        return switch (params.toLowerCase(Locale.ROOT)) {
            case "level" -> String.valueOf(level(player));
            case "mana" -> String.valueOf(mana(player, false));
            case "mana_max" -> String.valueOf(mana(player, true));
            case "team_tag" -> teamTag(player);
            case "playtime" -> playtime(player);
            default -> null;
        };
    }

    private int level(Player player) {
        QuestModule module = module("quest", QuestModule.class);
        if (module == null || module.getQuestManager() == null) return 1;
        int total = 0, count = 0;
        for (PlayerCategoryProgress progress : module.getQuestManager().getDataManager()
                .getOrCreate(player).getCategories().values()) {
            total += progress.getLevel();
            count++;
        }
        return count == 0 ? 1 : Math.max(1, Math.round((float) total / count));
    }

    private int mana(Player player, boolean max) {
        SkillsModule module = module("skills", SkillsModule.class);
        if (module == null || module.getApi() == null) return 0;
        return max ? module.getApi().getMaxMana(player) : module.getApi().getMana(player);
    }

    private String teamTag(Player player) {
        TeamModule module = module("team", TeamModule.class);
        if (module == null || module.getTeamManager() == null || module.getTeamManager().getTagManager() == null) return "-";
        String tag = module.getTeamManager().getTagManager().getTeamName(player.getUniqueId());
        return tag == null || tag.isBlank() ? "-" : tag;
    }

    private String playtime(Player player) {
        long seconds = Math.max(0L, player.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20L);
        long days = seconds / 86400L;
        long hours = (seconds % 86400L) / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        return days > 0 ? days + "h " + hours + "j" : hours > 0 ? hours + "j " + minutes + "m" : minutes + "m";
    }

    private <T> T module(String name, Class<T> type) {
        return plugin.getModuleManager().getModule(name)
                .filter(type::isInstance).map(type::cast).orElse(null);
    }
}
