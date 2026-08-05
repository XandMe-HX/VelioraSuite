package id.velioragardens.veliorasuite.module.skills;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.core.storage.BufferedYamlWriter;
import id.velioragardens.veliorasuite.module.skills.model.PlayerManaData;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ManaDataManager {

    private final VelioraSuite plugin;
    private final SkillsConfigManager configManager;
    private File file;
    private FileConfiguration data;
    private BufferedYamlWriter writer;
    private final Map<UUID, PlayerManaData> cache = new HashMap<>();

    public ManaDataManager(VelioraSuite plugin, SkillsConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    public void load() {
        plugin.createFolder("data");
        file = new File(plugin.getDataFolder(), "data/skills.yml");
        if (!file.exists()) {
            try { file.createNewFile(); } catch (IOException exception) { plugin.getLogger().warning("Gagal membuat data/skills.yml"); }
        }
        data = YamlConfiguration.loadConfiguration(file);
        cache.clear();
        writer = new BufferedYamlWriter(plugin, file, data, "data/skills.yml");
        writer.start();
    }

    public PlayerManaData getOrCreate(OfflinePlayer player) {
        UUID uuid = player.getUniqueId();
        PlayerManaData cached = cache.get(uuid);
        if (cached != null) {
            if (player.getName() != null && !player.getName().equals(cached.getName())) cached.setName(player.getName());
            return cached;
        }
        String path = "players." + uuid;
        if (!data.isConfigurationSection(path)) {
            PlayerManaData created = new PlayerManaData(uuid, player.getName(), configManager.getDefaultMana(), configManager.getDefaultMaxMana(), today(), 0, 0);
            cache.put(uuid, created);
            save(created);
            return created;
        }
        PlayerManaData result = new PlayerManaData(
                uuid,
                data.getString(path + ".name", player.getName()),
                data.getInt(path + ".mana", configManager.getDefaultMana()),
                data.getInt(path + ".max-mana", configManager.getDefaultMaxMana()),
                data.getString(path + ".last-reset-date", today()),
                data.getInt(path + ".total-mana-earned", 0),
                data.getInt(path + ".total-mana-spent", 0)
        );
        if (player.getName() != null && !player.getName().equals(result.getName())) result.setName(player.getName());
        cache.put(uuid, result);
        return result;
    }

    public PlayerManaData findByName(String name) {
        if (name == null || name.isBlank()) return null;
        ConfigurationSection players = data.getConfigurationSection("players");
        if (players == null) return null;
        for (String key : players.getKeys(false)) {
            String stored = data.getString("players." + key + ".name", "");
            if (stored.equalsIgnoreCase(name)) {
                try { return getOrCreate(Bukkit.getOfflinePlayer(UUID.fromString(key))); } catch (IllegalArgumentException ignored) { return null; }
            }
        }
        return null;
    }

    public void save(PlayerManaData manaData) {
        cache.put(manaData.getUuid(), manaData);
        String path = "players." + manaData.getUuid();
        data.set(path + ".name", manaData.getName());
        data.set(path + ".mana", manaData.getMana());
        data.set(path + ".max-mana", manaData.getMaxMana());
        data.set(path + ".last-reset-date", manaData.getLastResetDate());
        data.set(path + ".total-mana-earned", manaData.getTotalManaEarned());
        data.set(path + ".total-mana-spent", manaData.getTotalManaSpent());
        writer.markDirty();
    }

    public void flush() {
        if (writer != null) writer.shutdown();
    }

    public String today() {
        return LocalDate.now().toString();
    }

    public int countPlayers() {
        ConfigurationSection players = data.getConfigurationSection("players");
        return players == null ? 0 : players.getKeys(false).size();
    }
}
