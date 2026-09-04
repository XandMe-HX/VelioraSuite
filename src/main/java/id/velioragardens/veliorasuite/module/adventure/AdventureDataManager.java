package id.velioragardens.veliorasuite.module.adventure;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.core.storage.VelioraDatabase;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.EnumMap;

public final class AdventureDataManager {
    private final VelioraSuite plugin;
    private final Map<UUID, PlayerData> players = new HashMap<>();
    private final Map<Integer, GuildData> guilds = new HashMap<>();
    private File file;
    private YamlConfiguration yaml;
    private BukkitTask flushTask;
    private boolean dirty;
    private boolean databaseBacked;

    public AdventureDataManager(VelioraSuite plugin) { this.plugin = plugin; }

    public void load() {
        flush();
        if (flushTask != null) flushTask.cancel();
        plugin.createFolder("data");
        file = new File(plugin.getDataFolder(), "data/adventure.yml");
        yaml = YamlConfiguration.loadConfiguration(file);
        VelioraDatabase database = plugin.getDatabase();
        databaseBacked = database != null && database.isAvailable();
        if (databaseBacked) {
            String snapshot = database.loadModuleStateNow("adventure");
            if (snapshot == null || snapshot.isBlank()) {
                // First migration: SQLite bootstrap already copied every legacy
                // YAML file into database/backups before this snapshot is saved.
                database.saveModuleStateNow("adventure", yaml.saveToString());
            } else {
                yaml = YamlConfiguration.loadConfiguration(new StringReader(snapshot));
            }
        }
        players.clear();
        guilds.clear();
        loadPlayers();
        loadGuilds();
        dirty = false;
        flushTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::flush, 100L, 100L);
    }

    private void loadPlayers() {
        ConfigurationSection section = yaml.getConfigurationSection("players");
        if (section == null) return;
        for (String key : section.getKeys(false)) try {
            UUID uuid = UUID.fromString(key);
            String path = "players." + key;
            players.put(uuid, new PlayerData(
                    uuid,
                    yaml.getString(path + ".name", "Unknown"),
                    Math.max(0L, yaml.getLong(path + ".exp", 0L)),
                    Math.max(0, yaml.getInt(path + ".completed", 0)),
                    yaml.getString(path + ".custom-rank", ""),
                    professions(path)
            ));
        } catch (IllegalArgumentException ignored) { }
    }

    private void loadGuilds() {
        ConfigurationSection section = yaml.getConfigurationSection("guilds");
        if (section == null) return;
        for (String key : section.getKeys(false)) try {
            int id = Integer.parseInt(key);
            String path = "guilds." + key;
            GuildData guild = new GuildData(id);
            guild.exp = Math.max(0L, yaml.getLong(path + ".exp", 0L));
            guild.completed = Math.max(0, yaml.getInt(path + ".completed", 0));
            guild.dailyDate = yaml.getString(path + ".daily-date", "");
            guild.dailyIds.addAll(yaml.getStringList(path + ".daily-quests"));
            guild.activeQuest = yaml.getString(path + ".active.id", "");
            guild.activeProgress = Math.max(0, yaml.getInt(path + ".active.progress", 0));
            guild.activeTarget = Math.max(0, yaml.getInt(path + ".active.target", 0));
            guild.activeExpires = Math.max(0L, yaml.getLong(path + ".active.expires", 0L));
            // Old active quests have no activity value; give them one fresh 15-minute
            // grace window after the server first loads this version.
            guild.activeLastActivity = Math.max(0L, yaml.getLong(path + ".active.last-activity", guild.activeQuest.isBlank() ? 0L : System.currentTimeMillis()));
            guild.activeX = yaml.getInt(path + ".active.x", 0);
            guild.activeZ = yaml.getInt(path + ".active.z", 0);
            guild.ready = yaml.getBoolean(path + ".active.ready", false);
            guild.mobsSpawned = yaml.getBoolean(path + ".active.mobs-spawned", false);
            ConfigurationSection contributions = yaml.getConfigurationSection(path + ".active.contributions");
            if (contributions != null) for (String uuid : contributions.getKeys(false)) try {
                guild.contributions.put(UUID.fromString(uuid), contributions.getInt(uuid, 0));
            } catch (IllegalArgumentException ignored) { }
            guilds.put(id, guild);
        } catch (NumberFormatException ignored) { }
    }

    public PlayerData player(UUID uuid, String name) {
        String safeName = name == null || name.isBlank() ? "Unknown" : name;
        PlayerData data = players.computeIfAbsent(uuid, key -> new PlayerData(key, safeName, 0L, 0, "", new EnumMap<>(AdventureProfession.class)));
        if (name != null && !name.isBlank()) data.name = name;
        return data;
    }

    public GuildData guild(int id) { return guilds.computeIfAbsent(id, GuildData::new); }
    public Collection<GuildData> guilds() { return List.copyOf(guilds.values()); }
    public java.util.Set<UUID> playerIds() { return java.util.Set.copyOf(players.keySet()); }

    public boolean resetTier(UUID uuid) {
        PlayerData profile = players.get(uuid);
        if (profile == null || (profile.exp() == 0 && profile.customRank().isBlank())) return false;
        profile.setExp(0);
        profile.customRank("");
        save();
        return true;
    }

    public void save() {
        dirty = true;
    }

    private Map<AdventureProfession, Long> professions(String path) {
        Map<AdventureProfession, Long> result = new EnumMap<>(AdventureProfession.class);
        for (AdventureProfession profession : AdventureProfession.values()) {
            result.put(profession, Math.max(0L, yaml.getLong(path + ".professions." + profession.name(), 0L)));
        }
        return result;
    }

    public void flush() {
        if (!dirty || yaml == null || file == null) return;
        yaml.set("players", null);
        for (PlayerData data : players.values()) {
            String path = "players." + data.uuid;
            yaml.set(path + ".name", data.name);
            yaml.set(path + ".exp", data.exp);
            yaml.set(path + ".completed", data.completed);
            yaml.set(path + ".custom-rank", data.customRank);
            for (AdventureProfession profession : AdventureProfession.values()) yaml.set(path + ".professions." + profession.name(), data.professionExp(profession));
        }
        yaml.set("guilds", null);
        for (GuildData guild : guilds.values()) {
            String path = "guilds." + guild.id;
            yaml.set(path + ".exp", guild.exp);
            yaml.set(path + ".completed", guild.completed);
            yaml.set(path + ".daily-date", guild.dailyDate);
            yaml.set(path + ".daily-quests", guild.dailyIds);
            yaml.set(path + ".active.id", guild.activeQuest);
            yaml.set(path + ".active.progress", guild.activeProgress);
            yaml.set(path + ".active.target", guild.activeTarget);
            yaml.set(path + ".active.expires", guild.activeExpires);
            yaml.set(path + ".active.last-activity", guild.activeLastActivity);
            yaml.set(path + ".active.x", guild.activeX);
            yaml.set(path + ".active.z", guild.activeZ);
            yaml.set(path + ".active.ready", guild.ready);
            yaml.set(path + ".active.mobs-spawned", guild.mobsSpawned);
            yaml.set(path + ".active.contributions", null);
            for (Map.Entry<UUID, Integer> entry : guild.contributions.entrySet()) {
                yaml.set(path + ".active.contributions." + entry.getKey(), entry.getValue());
            }
        }
        if (databaseBacked) {
            // The mutable Bukkit data was serialized on the server thread;
            // SQLite I/O itself runs on the dedicated database worker.
            String snapshot = yaml.saveToString();
            dirty = false;
            plugin.getDatabase().saveModuleStateAsync("adventure", snapshot).exceptionally(error -> {
                plugin.getServer().getScheduler().runTask(plugin, () -> dirty = true);
                plugin.getLogger().warning("VelioraPetualang: SQLite sedang gagal menulis, akan dicoba ulang.");
                return null;
            });
            return;
        }
        try { yaml.save(file); dirty = false; }
        catch (IOException exception) { plugin.getLogger().severe("VelioraPetualang: gagal menyimpan data: " + exception.getMessage()); }
    }

    public void shutdown() {
        save();
        if (databaseBacked && yaml != null) {
            // Existing flush serialization is reused, then the final snapshot
            // is written synchronously before the database worker shuts down.
            yaml.set("players", null);
            for (PlayerData data : players.values()) {
                String path = "players." + data.uuid;
                yaml.set(path + ".name", data.name); yaml.set(path + ".exp", data.exp);
                yaml.set(path + ".completed", data.completed); yaml.set(path + ".custom-rank", data.customRank);
                for (AdventureProfession profession : AdventureProfession.values()) yaml.set(path + ".professions." + profession.name(), data.professionExp(profession));
            }
            yaml.set("guilds", null);
            for (GuildData guild : guilds.values()) {
                String path = "guilds." + guild.id;
                yaml.set(path + ".exp", guild.exp); yaml.set(path + ".completed", guild.completed);
                yaml.set(path + ".daily-date", guild.dailyDate); yaml.set(path + ".daily-quests", guild.dailyIds);
                yaml.set(path + ".active.id", guild.activeQuest); yaml.set(path + ".active.progress", guild.activeProgress);
                yaml.set(path + ".active.target", guild.activeTarget); yaml.set(path + ".active.expires", guild.activeExpires);
                yaml.set(path + ".active.last-activity", guild.activeLastActivity);
                yaml.set(path + ".active.x", guild.activeX); yaml.set(path + ".active.z", guild.activeZ);
                yaml.set(path + ".active.ready", guild.ready); yaml.set(path + ".active.mobs-spawned", guild.mobsSpawned);
                yaml.set(path + ".active.contributions", null);
                for (Map.Entry<UUID, Integer> entry : guild.contributions.entrySet()) yaml.set(path + ".active.contributions." + entry.getKey(), entry.getValue());
            }
            plugin.getDatabase().saveModuleStateNow("adventure", yaml.saveToString());
            dirty = false;
        } else flush();
        if (flushTask != null) flushTask.cancel();
        flushTask = null;
    }

    public static final class PlayerData {
        private final UUID uuid;
        private String name;
        private long exp;
        private int completed;
        private String customRank;
        private final Map<AdventureProfession, Long> professions;

        private PlayerData(UUID uuid, String name, long exp, int completed, String customRank, Map<AdventureProfession, Long> professions) {
            this.uuid = uuid; this.name = name; this.exp = exp; this.completed = completed;
            this.customRank = customRank == null ? "" : customRank;
            this.professions = new EnumMap<>(AdventureProfession.class);
            this.professions.putAll(professions);
        }
        public long exp() { return exp; }
        public int completed() { return completed; }
        public String customRank() { return customRank; }
        public void addExp(long value) { exp = Math.max(0L, exp + value); }
        public void setExp(long value) { exp = Math.max(0L, value); }
        public void complete() { completed++; }
        public void customRank(String value) { customRank = value == null ? "" : value.trim(); }
        public long professionExp(AdventureProfession profession) { return professions.getOrDefault(profession, 0L); }
        public void addProfessionExp(AdventureProfession profession, long value) { professions.merge(profession, Math.max(0L, value), Long::sum); }
    }

    public static final class GuildData {
        private final int id;
        private long exp;
        private int completed;
        private String dailyDate = "";
        private final List<String> dailyIds = new ArrayList<>();
        private String activeQuest = "";
        private int activeProgress;
        private int activeTarget;
        private long activeExpires;
        private long activeLastActivity;
        private int activeX;
        private int activeZ;
        private boolean ready;
        private boolean mobsSpawned;
        private final Map<UUID, Integer> contributions = new LinkedHashMap<>();

        private GuildData(int id) { this.id = id; }
        public int id() { return id; }
        public long exp() { return exp; }
        public int completed() { return completed; }
        public String dailyDate() { return dailyDate; }
        public List<String> dailyIds() { return dailyIds; }
        public String activeQuest() { return activeQuest; }
        public int activeProgress() { return activeProgress; }
        public int activeTarget() { return activeTarget; }
        public long activeExpires() { return activeExpires; }
        public long activeLastActivity() { return activeLastActivity; }
        public int activeX() { return activeX; }
        public int activeZ() { return activeZ; }
        public boolean ready() { return ready; }
        public boolean mobsSpawned() { return mobsSpawned; }
        public Map<UUID, Integer> contributions() { return contributions; }
        public void daily(String date, List<String> ids) { dailyDate = date; dailyIds.clear(); dailyIds.addAll(ids); clearActive(); }
        public void start(AdventureQuestTemplate quest, long expires, int x, int z) {
            activeQuest = quest.id(); activeProgress = 0; activeTarget = quest.amount(); activeExpires = expires;
            activeLastActivity = System.currentTimeMillis();
            activeX = x; activeZ = z; ready = false; mobsSpawned = false; contributions.clear();
        }
        public void addProgress(UUID player, int amount) {
            if (amount <= 0 || ready) return;
            int applied = Math.min(amount, Math.max(0, activeTarget - activeProgress));
            activeProgress += applied;
            activeLastActivity = System.currentTimeMillis();
            contributions.merge(player, applied, Integer::sum);
            ready = activeProgress >= activeTarget;
        }
        public void setMobsSpawned() { mobsSpawned = true; }
        public void complete(long rewardExp) { exp += Math.max(0L, rewardExp); completed++; clearActive(); }
        public void clearActive() {
            activeQuest = ""; activeProgress = 0; activeTarget = 0; activeExpires = 0L; activeLastActivity = 0L; activeX = 0; activeZ = 0;
            ready = false; mobsSpawned = false; contributions.clear();
        }
    }
}
