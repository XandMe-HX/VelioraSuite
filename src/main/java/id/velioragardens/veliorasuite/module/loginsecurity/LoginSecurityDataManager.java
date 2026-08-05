package id.velioragardens.veliorasuite.module.loginsecurity;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.core.storage.BufferedYamlWriter;
import id.velioragardens.veliorasuite.module.loginsecurity.model.AuthPlayerData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.UUID;

public final class LoginSecurityDataManager {

    private final VelioraSuite plugin;
    private File file;
    private FileConfiguration data;
    private BufferedYamlWriter writer;

    public LoginSecurityDataManager(VelioraSuite plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.createFolder("data");
        this.file = new File(plugin.getDataFolder(), "data/loginsecurity.yml");

        if (!file.exists()) {
            try {
                if (file.createNewFile()) {
                    this.data = YamlConfiguration.loadConfiguration(file);
                    this.data.createSection("players");
                    save();
                }
            } catch (IOException exception) {
                plugin.getLogger().warning("VelioraLoginSecurity: gagal membuat data/loginsecurity.yml");
            }
        }

        this.data = YamlConfiguration.loadConfiguration(file);
        if (!this.data.isConfigurationSection("players")) {
            this.data.createSection("players");
            save();
        }
        writer = new BufferedYamlWriter(plugin, file, data, "data/loginsecurity.yml");
        writer.start();
    }

    public AuthPlayerData getByName(String playerName) {
        if (playerName == null || playerName.isBlank()) return null;
        return read("players." + key(playerName));
    }

    public AuthPlayerData getByUuid(UUID uuid) {
        if (uuid == null) return null;
        ConfigurationSection players = data.getConfigurationSection("players");
        if (players == null) return null;

        for (String key : players.getKeys(false)) {
            String rawUuid = players.getString(key + ".uuid", "");
            if (rawUuid.equalsIgnoreCase(uuid.toString())) {
                return read("players." + key);
            }
        }
        return null;
    }

    public void savePlayer(AuthPlayerData playerData) {
        if (playerData == null || playerData.getName() == null) return;
        String path = "players." + key(playerData.getName());
        data.set(path + ".uuid", playerData.getUuid() == null ? "" : playerData.getUuid().toString());
        data.set(path + ".name", playerData.getName());
        data.set(path + ".password-hash", playerData.getPasswordHash());
        data.set(path + ".salt", playerData.getSalt());
        data.set(path + ".registered-at", playerData.getRegisteredAt());
        data.set(path + ".last-login", playerData.getLastLogin());
        data.set(path + ".last-ip-hash", playerData.getLastIpHash());
        data.set(path + ".failed-attempts", playerData.getFailedAttempts());
        data.set(path + ".locked-until", playerData.getLockedUntil());
        save();
    }

    public void updateName(AuthPlayerData playerData, String newName) {
        if (playerData == null || newName == null || newName.isBlank()) return;
        String oldName = playerData.getName();
        if (oldName != null && !oldName.equalsIgnoreCase(newName)) {
            deleteByName(oldName);
        }
        playerData.setName(newName);
        savePlayer(playerData);
    }

    public void deleteByName(String playerName) {
        if (playerName == null || playerName.isBlank()) return;
        data.set("players." + key(playerName), null);
        save();
    }

    public int countRegistered() {
        ConfigurationSection players = data.getConfigurationSection("players");
        return players == null ? 0 : players.getKeys(false).size();
    }

    private AuthPlayerData read(String path) {
        if (!data.isConfigurationSection(path)) return null;
        String uuidRaw = data.getString(path + ".uuid", "");
        UUID uuid = null;
        try {
            if (!uuidRaw.isBlank()) uuid = UUID.fromString(uuidRaw);
        } catch (IllegalArgumentException ignored) {
            // Broken UUID should not break login data loading.
        }
        return new AuthPlayerData(
                uuid,
                data.getString(path + ".name", ""),
                data.getString(path + ".password-hash", ""),
                data.getString(path + ".salt", ""),
                data.getString(path + ".registered-at", ""),
                data.getString(path + ".last-login", ""),
                data.getString(path + ".last-ip-hash", ""),
                data.getInt(path + ".failed-attempts", 0),
                data.getLong(path + ".locked-until", 0L)
        );
    }

    private void save() {
        if (writer != null) { writer.markDirty(); return; }
        try {
            data.save(file);
        } catch (IOException exception) {
            plugin.getLogger().warning("VelioraLoginSecurity: gagal menyimpan data loginsecurity.yml");
        }
    }

    public void shutdown() { if (writer != null) writer.shutdown(); }

    private String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
