package id.velioragardens.veliorasuite.module.fishing;

import id.velioragardens.veliorasuite.VelioraSuite;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

public final class FishingRodDataManager {

    private final VelioraSuite plugin;
    private File file;
    private FileConfiguration data;

    public FishingRodDataManager(VelioraSuite plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.createFolder("data");
        file = new File(plugin.getDataFolder(), "data/fishing-rods.yml");
        if (!file.exists()) {
            try { file.createNewFile(); } catch (IOException exception) {
                plugin.getLogger().warning("Gagal membuat data/fishing-rods.yml");
            }
        }
        data = YamlConfiguration.loadConfiguration(file);
    }

    public boolean has(UUID uuid, int tier) {
        return data != null && data.getBoolean(path(uuid, tier), false);
    }

    public int highest(UUID uuid, int maximumTier) {
        for (int tier = maximumTier; tier >= 1; tier--) if (has(uuid, tier)) return tier;
        return 0;
    }

    public void unlock(UUID uuid, int tier) {
        if (data == null || file == null) return;
        data.set(path(uuid, tier), true);
        save();
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException exception) {
            plugin.getLogger().warning("Gagal menyimpan data Fishing Rod.");
        }
    }

    private String path(UUID uuid, int tier) {
        return "players." + uuid + ".unlocked.tier-" + tier;
    }
}
