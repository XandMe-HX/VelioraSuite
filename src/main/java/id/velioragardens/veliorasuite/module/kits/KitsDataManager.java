package id.velioragardens.veliorasuite.module.kits;

import id.velioragardens.veliorasuite.VelioraSuite;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

public final class KitsDataManager {

    private final VelioraSuite plugin;
    private File file;
    private FileConfiguration data;

    public KitsDataManager(VelioraSuite plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.createFolder("data");
        this.file = new File(plugin.getDataFolder(), "data/kits-data.yml");

        if (!file.exists()) {
            try {
                boolean created = file.createNewFile();
                if (!created) {
                    plugin.getLogger().warning("VelioraKits: kits-data.yml sudah ada atau gagal dibuat.");
                }
            } catch (IOException exception) {
                plugin.getLogger().severe("VelioraKits: gagal membuat kits-data.yml: " + exception.getMessage());
            }
        }

        this.data = YamlConfiguration.loadConfiguration(file);
    }

    public void save() {
        if (data == null || file == null) {
            return;
        }

        try {
            data.save(file);
        } catch (IOException exception) {
            plugin.getLogger().severe("VelioraKits: gagal menyimpan kits-data.yml: " + exception.getMessage());
        }
    }

    public long getLastClaim(UUID uuid, String kitId) {
        return data.getLong(playerPath(uuid) + ".claims." + kitId + ".last", 0L);
    }

    public void setLastClaim(UUID uuid, String kitId, long time) {
        data.set(playerPath(uuid) + ".claims." + kitId + ".last", time);
        incrementTotalClaim(uuid, kitId);
        save();
    }

    public boolean hasPurchased(UUID uuid, String kitId) {
        return data.getStringList(playerPath(uuid) + ".purchased").contains(kitId.toLowerCase());
    }

    public void setPurchased(UUID uuid, String kitId) {
        String path = playerPath(uuid) + ".purchased";
        List<String> purchased = data.getStringList(path);
        String normalized = kitId.toLowerCase();

        if (!purchased.contains(normalized)) {
            purchased.add(normalized);
            data.set(path, purchased);
            save();
        }
    }

    public boolean isFirstJoinGiven(UUID uuid) {
        return data.getBoolean(playerPath(uuid) + ".first-join-given", false);
    }

    public void setFirstJoinGiven(UUID uuid, boolean given) {
        data.set(playerPath(uuid) + ".first-join-given", given);
        save();
    }

    public int getTotalClaim(UUID uuid, String kitId) {
        return data.getInt(playerPath(uuid) + ".claims." + kitId + ".total", 0);
    }

    private void incrementTotalClaim(UUID uuid, String kitId) {
        String path = playerPath(uuid) + ".claims." + kitId + ".total";
        data.set(path, data.getInt(path, 0) + 1);
    }

    private String playerPath(UUID uuid) {
        return "players." + uuid;
    }
}
