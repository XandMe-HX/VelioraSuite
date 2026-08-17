package id.velioragardens.veliorasuite.module.chat;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.api.VelioraModule;
import id.velioragardens.veliorasuite.module.quest.QuestModule;
import id.velioragardens.veliorasuite.module.quest.QuestPlaceholderManager;
import id.velioragardens.veliorasuite.module.skills.SkillsModule;
import id.velioragardens.veliorasuite.module.team.TeamModule;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

public final class ChatPlaceholderManager {

    private final VelioraSuite plugin;
    private final ChatConfigManager configManager;

    public ChatPlaceholderManager(VelioraSuite plugin, ChatConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    public String getTeamName(UUID uuid) {
        if (!configManager.isTeamTagPlaceholderEnabled() || uuid == null) return "";
        TeamModule teamModule = getTeamModule();
        if (teamModule == null || teamModule.getTeamManager() == null) return "";
        return teamModule.getTeamManager().getTagManager().getTeamName(uuid);
    }

    public String getTeamTag(UUID uuid) {
        if (!configManager.isTeamTagPlaceholderEnabled() || uuid == null) return configManager.getTeamTagEmpty();
        TeamModule teamModule = getTeamModule();
        if (teamModule == null || teamModule.getTeamManager() == null) return configManager.getTeamTagEmpty();
        String tag = teamModule.getTeamManager().getTagManager().getRawTagPrefix(uuid);
        return tag.isBlank() ? configManager.getTeamTagEmpty() : tag;
    }

    public String getLuckPermsPrefix(Player player) {
        if (player == null || Bukkit.getPluginManager().getPlugin("LuckPerms") == null) return configManager.getLuckPermsPrefixEmpty();
        try {
            Class<?> luckPermsClass = Class.forName("net.luckperms.api.LuckPerms");
            @SuppressWarnings({"rawtypes", "unchecked"})
            RegisteredServiceProvider<?> registration = Bukkit.getServicesManager().getRegistration((Class) luckPermsClass);
            if (registration == null) return configManager.getLuckPermsPrefixEmpty();
            Object luckPerms = registration.getProvider();
            Object userManager = luckPerms.getClass().getMethod("getUserManager").invoke(luckPerms);
            Object user = userManager.getClass().getMethod("getUser", UUID.class).invoke(userManager, player.getUniqueId());
            if (user == null) return configManager.getLuckPermsPrefixEmpty();
            Object cachedData = user.getClass().getMethod("getCachedData").invoke(user);
            Object metaData = cachedData.getClass().getMethod("getMetaData").invoke(cachedData);
            Method getPrefix = metaData.getClass().getMethod("getPrefix");
            Object prefix = getPrefix.invoke(metaData);
            return prefix instanceof String text ? text : configManager.getLuckPermsPrefixEmpty();
        } catch (Exception exception) {
            return configManager.getLuckPermsPrefixEmpty();
        }
    }

    public String getPlaceholder(OfflinePlayer player, String identifier) {
        if (identifier == null) return "";
        try {
            UUID uuid = player == null ? null : player.getUniqueId();
            String lower = identifier.toLowerCase();
            if (lower.startsWith("quest_")) return getQuestPlaceholder(player, lower);
            return switch (lower) {
                case "team_name" -> getTeamName(uuid);
                case "team_tag" -> getTeamTag(uuid);
                case "player_name" -> player == null ? "" : player.getName();
                case "playtime" -> formatPlaytime(player);
                case "level" -> getLevel(player);
                case "mana", "mana_max", "mana_bar", "mana_percent" -> getSkillsPlaceholder(player, identifier);
                default -> "";
            };
        } catch (RuntimeException | LinkageError exception) {
            return "";
        }
    }

    private String formatPlaytime(OfflinePlayer player) {
        if (player == null) return "0m";
        long ticks = Math.max(0L, player.getStatistic(Statistic.PLAY_ONE_MINUTE));
        long minutes = ticks / (20L * 60L);
        long days = minutes / 1440L;
        long hours = (minutes % 1440L) / 60L;
        long remainingMinutes = minutes % 60L;
        if (days > 0) return days + "d " + hours + "h";
        if (hours > 0) return hours + "h " + remainingMinutes + "m";
        return remainingMinutes + "m";
    }

    private String getLevel(OfflinePlayer player) {
        QuestModule questModule = getQuestModule();
        if (player == null || questModule == null || questModule.getQuestManager() == null) return "1";
        int total = 0;
        int count = 0;
        for (var progress : questModule.getQuestManager().getDataManager().getOrCreate(player).getCategories().values()) {
            total += progress.getLevel();
            count++;
        }
        return String.valueOf(count == 0 ? 1 : Math.max(1, Math.round((float) total / count)));
    }

    private String getSkillsPlaceholder(OfflinePlayer player, String identifier) {
        SkillsModule skillsModule = getSkillsModule();
        if (skillsModule == null || skillsModule.getPlaceholderManager() == null) return "";
        return skillsModule.getPlaceholderManager().getPlaceholder(player, identifier);
    }

    private String getQuestPlaceholder(OfflinePlayer player, String identifier) {
        QuestModule questModule = getQuestModule();
        if (questModule == null || questModule.getQuestManager() == null) return "";
        return new QuestPlaceholderManager(questModule.getQuestManager().getDataManager()).getPlaceholder(player, identifier);
    }

    private TeamModule getTeamModule() {
        Optional<VelioraModule> module = plugin.getModuleManager().getModule("team");
        if (module.isEmpty() || !(module.get() instanceof TeamModule teamModule)) return null;
        return teamModule;
    }

    private QuestModule getQuestModule() {
        Optional<VelioraModule> module = plugin.getModuleManager().getModule("quest");
        if (module.isEmpty() || !(module.get() instanceof QuestModule questModule)) return null;
        return questModule;
    }

    private SkillsModule getSkillsModule() {
        Optional<VelioraModule> module = plugin.getModuleManager().getModule("skills");
        if (module.isEmpty() || !(module.get() instanceof SkillsModule skillsModule)) {
            return null;
        }
        return skillsModule;
    }
}
