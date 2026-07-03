package id.velioragardens.veliorasuite.module.playtime;

import id.velioragardens.veliorasuite.VelioraSuite;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PlaytimeDataManager {

    private final VelioraSuite plugin;
    private File file;
    private FileConfiguration data;

    public PlaytimeDataManager(VelioraSuite plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.createFolder("data");
        this.file = new File(plugin.getDataFolder(), "data/playtime.yml");
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException exception) {
                plugin.getLogger().severe("VelioraPlaytime: gagal membuat playtime.yml: " + exception.getMessage());
            }
        }
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    public void save() {
        if (file == null || data == null) return;
        try {
            data.save(file);
        } catch (IOException exception) {
            plugin.getLogger().severe("VelioraPlaytime: gagal menyimpan playtime.yml: " + exception.getMessage());
        }
    }

    public Map<UUID, PlaytimePlayerData> loadPlayers() {
        Map<UUID, PlaytimePlayerData> result = new HashMap<>();
        if (data == null) return result;
        ConfigurationSection players = data.getConfigurationSection("players");
        if (players == null) return result;
        for (String key : players.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                String path = "players." + key;
                PlaytimePlayerData playerData = new PlaytimePlayerData(uuid);
                playerData.name = data.getString(path + ".name", "Unknown");
                playerData.bestSessionMillis = data.getLong(path + ".best-session-millis", 0L);
                playerData.lastSessionMillis = data.getLong(path + ".last-session-millis", 0L);
                playerData.lastSeen = data.getLong(path + ".last-seen", 0L);
                playerData.pendingStart = data.getLong(path + ".pending.start", 0L);
                playerData.pendingDisconnectedAt = data.getLong(path + ".pending.disconnected-at", 0L);
                result.put(uuid, playerData);
            } catch (Exception ignored) {
            }
        }
        return result;
    }

    public void savePlayers(Map<UUID, PlaytimePlayerData> players) {
        if (data == null) return;
        data.set("players", null);
        for (PlaytimePlayerData playerData : players.values()) {
            String path = "players." + playerData.uuid;
            data.set(path + ".name", playerData.name);
            data.set(path + ".best-session-millis", playerData.bestSessionMillis);
            data.set(path + ".last-session-millis", playerData.lastSessionMillis);
            data.set(path + ".last-seen", playerData.lastSeen);
            if (playerData.pendingStart > 0L && playerData.pendingDisconnectedAt > 0L) {
                data.set(path + ".pending.start", playerData.pendingStart);
                data.set(path + ".pending.disconnected-at", playerData.pendingDisconnectedAt);
            }
        }
        save();
    }

    public static final class PlaytimePlayerData {
        public final UUID uuid;
        public String name = "Unknown";
        public long bestSessionMillis;
        public long lastSessionMillis;
        public long lastSeen;
        public long pendingStart;
        public long pendingDisconnectedAt;

        public PlaytimePlayerData(UUID uuid) {
            this.uuid = uuid;
        }
    }
}
