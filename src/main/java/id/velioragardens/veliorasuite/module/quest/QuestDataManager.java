package id.velioragardens.veliorasuite.module.quest;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.module.quest.model.PlayerCategoryProgress;
import id.velioragardens.veliorasuite.module.quest.model.PlayerQuestData;
import id.velioragardens.veliorasuite.module.quest.model.QuestCategory;
import id.velioragardens.veliorasuite.module.quest.model.QuestState;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.UUID;

public final class QuestDataManager {

    private final VelioraSuite plugin;
    private final QuestConfigManager configManager;
    private File file;
    private FileConfiguration data;

    public QuestDataManager(VelioraSuite plugin, QuestConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    public void load() {
        plugin.createFolder("data");
        file = new File(plugin.getDataFolder(), "data/quests.yml");
        if (!file.exists()) {
            try { file.createNewFile(); } catch (IOException exception) { plugin.getLogger().warning("Gagal membuat data/quests.yml"); }
        }
        data = YamlConfiguration.loadConfiguration(file);
    }

    public PlayerQuestData getOrCreate(OfflinePlayer player) {
        UUID uuid = player.getUniqueId();
        String base = "players." + uuid;
        PlayerQuestData result = new PlayerQuestData(uuid, player.getName());
        result.setClaimLand(data.getBoolean(base + ".starter.claim-land", false));
        result.setSetHome(data.getBoolean(base + ".starter.set-home", false));
        result.setStarterKit(data.getBoolean(base + ".starter.starter-kit", false));
        result.setStarterCompleted(data.getBoolean(base + ".starter.completed", false));
        result.setLastReminder(data.getLong(base + ".starter.last-reminder", 0L));

        for (QuestCategory category : QuestCategory.values()) {
            String path = base + ".categories." + category.key();
            int level = Math.max(1, data.getInt(path + ".level", 1));
            int completed = Math.max(0, data.getInt(path + ".completed-count", 0));
            QuestState state = parseState(data.getString(path + ".state", QuestState.NOT_STARTED.name()));
            int target = Math.max(1, data.getInt(path + ".current-target", configManager.calculateTarget(category, level)));
            int progress = Math.max(0, data.getInt(path + ".current-progress", 0));
            int reward = Math.max(0, data.getInt(path + ".current-reward-money", configManager.calculateRewardMoney(level)));
            result.putCategoryProgress(new PlayerCategoryProgress(category, level, completed, state, target, progress, reward));
        }
        if (player.getName() != null) result.setName(player.getName());
        save(result);
        return result;
    }

    public void save(PlayerQuestData playerData) {
        String base = "players." + playerData.getUuid();
        data.set(base + ".name", playerData.getName());
        data.set(base + ".starter.claim-land", playerData.isClaimLand());
        data.set(base + ".starter.set-home", playerData.isSetHome());
        data.set(base + ".starter.starter-kit", playerData.isStarterKit());
        data.set(base + ".starter.completed", playerData.isStarterCompleted() || playerData.isStarterDone());
        data.set(base + ".starter.last-reminder", playerData.getLastReminder());

        for (PlayerCategoryProgress progress : playerData.getCategories().values()) {
            String path = base + ".categories." + progress.getCategory().key();
            data.set(path + ".level", progress.getLevel());
            data.set(path + ".completed-count", progress.getCompletedCount());
            data.set(path + ".state", progress.getState().name());
            data.set(path + ".current-target", progress.getCurrentTarget());
            data.set(path + ".current-progress", progress.getCurrentProgress());
            data.set(path + ".current-reward-money", progress.getCurrentRewardMoney());
        }
        flush();
    }

    public int countPlayers() {
        ConfigurationSection players = data.getConfigurationSection("players");
        return players == null ? 0 : players.getKeys(false).size();
    }

    public void flush() {
        try { data.save(file); } catch (IOException exception) { plugin.getLogger().warning("Gagal menyimpan data/quests.yml"); }
    }

    private QuestState parseState(String raw) {
        if (raw == null) return QuestState.NOT_STARTED;
        try { return QuestState.valueOf(raw.trim().toUpperCase(Locale.ROOT)); } catch (Exception ignored) { return QuestState.NOT_STARTED; }
    }
}
