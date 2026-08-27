package id.velioragardens.veliorasuite.placeholder;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.module.adventure.AdventureModule;
import id.velioragardens.veliorasuite.module.team.TeamModule;
import id.velioragardens.veliorasuite.module.fishing.FishingModule;
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
            case "team_tag" -> teamTag(player);
            case "playtime" -> playtime(player);
            case "fishing_coins" -> String.valueOf(fishingCoins(player));
            case "fishing_coins_formatted" -> fishingCoinsFormatted(player);
            case "integration_war" -> integration("VelioraWar");
            case "integration_gacha" -> integration("VelioraGacha");
            case "integration_ftb" -> integration("VelioraFTB");
            case "integrations_online" -> String.valueOf(integrationsOnline());
            case "adventure_rank", "rank_petualang" -> adventure(player) == null ? "F" : adventure(player).rank(player);
            case "adventure_rank_plain", "rank_petualang_plain" -> adventure(player) == null ? "F" : adventure(player).rankPlain(player);
            case "adventure_exp", "petualang_exp" -> String.valueOf(adventure(player) == null ? 0 : adventure(player).exp(player));
            case "adventure_exp_next", "petualang_exp_next" -> String.valueOf(adventure(player) == null ? 2500 : adventure(player).rankNextExp(player));
            case "adventure_exp_remaining", "petualang_exp_remaining" -> String.valueOf(adventure(player) == null ? 2500 : adventure(player).rankRemainingExp(player));
            case "adventure_rank_next", "rank_petualang_next" -> adventure(player) == null ? "E" : adventure(player).nextRank(player);
            case "adventure_level", "petualang_level" -> String.valueOf(adventure(player) == null ? 1 : adventure(player).level(player));
            case "adventure_level_exp" -> String.valueOf(adventure(player) == null ? 0 : adventure(player).levelCurrentExp(player));
            case "adventure_level_exp_required" -> String.valueOf(adventure(player) == null ? 0 : adventure(player).levelRequiredExp(player));
            case "adventure_quests_completed" -> String.valueOf(adventure(player) == null ? 0 : adventure(player).completed(player));
            case "guild_level" -> String.valueOf(adventure(player) == null ? 0 : adventure(player).guildLevel(player));
            case "guild_exp" -> String.valueOf(adventure(player) == null ? 0 : adventure(player).guildExp(player));
            case "guild_quests_completed" -> String.valueOf(adventure(player) == null ? 0 : adventure(player).guildCompleted(player));
            default -> null;
        };
    }

    private id.velioragardens.veliorasuite.module.adventure.AdventureManager adventure(Player player) {
        AdventureModule module = module("adventure", AdventureModule.class);
        return module == null ? null : module.getManager();
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

    private long fishingCoins(Player player) {
        FishingModule module = module("fishing", FishingModule.class);
        return module == null || module.getFishingManager() == null ? 0L : module.getFishingManager().coins(player);
    }

    private String fishingCoinsFormatted(Player player) {
        FishingModule module = module("fishing", FishingModule.class);
        return module == null || module.getFishingManager() == null ? "0" : module.getFishingManager().formattedCoins(player);
    }

    private String integration(String name) {
        return plugin.getHookManager().hasHook(name) ? "ON" : "OFF";
    }

    private int integrationsOnline() {
        int online = 0;
        for (String name : java.util.List.of("VelioraWar", "VelioraGacha", "VelioraFTB")) {
            if (plugin.getHookManager().hasHook(name)) online++;
        }
        return online;
    }

    private <T> T module(String name, Class<T> type) {
        return plugin.getModuleManager().getModule(name)
                .filter(type::isInstance).map(type::cast).orElse(null);
    }
}
