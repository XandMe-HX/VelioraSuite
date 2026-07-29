package id.velioragardens.veliorasuite.module.fishing;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.module.fishing.model.CaughtFish;
import id.velioragardens.veliorasuite.module.fishing.model.FishingCollectionEntry;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

public final class FishingCollectionDataManager {

    private final VelioraSuite plugin;
    private File file;
    private FileConfiguration data;

    public FishingCollectionDataManager(VelioraSuite plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.createFolder("data");
        file = new File(plugin.getDataFolder(), "data/fishing-collection.yml");
        if (!file.exists()) {
            try { file.createNewFile(); } catch (IOException exception) { plugin.getLogger().warning("Gagal membuat data/fishing-collection.yml"); }
        }
        data = YamlConfiguration.loadConfiguration(file);
    }

    public void unlock(OfflinePlayer player, CaughtFish fish) {
        if (player == null || fish == null) return;
        String path = path(player, fish.id());
        data.set(path + ".unlocked", true);
        data.set(path + ".total-caught", data.getInt(path + ".total-caught", 0) + 1);
        data.set(path + ".best-weight", Math.max(data.getDouble(path + ".best-weight", 0.0D), fish.weight()));
        data.set(path + ".last-name", fish.name());
        data.set(path + ".last-rarity", fish.rarity().name());
        data.set(path + ".last-origin", fish.origin());
        data.set(path + ".last-region", fish.region());
        flush();
    }

    public boolean isUnlocked(OfflinePlayer player, String fishId) {
        return data.getBoolean(path(player, fishId) + ".unlocked", false);
    }

    public FishingCollectionEntry get(OfflinePlayer player, String fishId) {
        String path = path(player, fishId);
        return new FishingCollectionEntry(fishId, data.getInt(path + ".total-caught", 0), data.getDouble(path + ".best-weight", 0.0D));
    }

    public void flush() {
        try { data.save(file); } catch (IOException exception) { plugin.getLogger().warning("Gagal menyimpan data/fishing-collection.yml"); }
    }

    private String path(OfflinePlayer player, String fishId) {
        return "players." + player.getUniqueId() + ".fish." + fishId;
    }
}
