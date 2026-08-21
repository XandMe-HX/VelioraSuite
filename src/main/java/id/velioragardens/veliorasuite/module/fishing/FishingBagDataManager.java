package id.velioragardens.veliorasuite.module.fishing;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.core.storage.BufferedYamlWriter;
import id.velioragardens.veliorasuite.module.fishing.model.CaughtFish;
import id.velioragardens.veliorasuite.module.fishing.model.FishRarity;
import id.velioragardens.veliorasuite.module.fishing.model.FishingBagEntry;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class FishingBagDataManager {

    public static final int ITEMS_PER_PAGE = 45;
    public static final int MAX_PAGES = 5;
    public static final int MAX_UNIQUE_ITEMS = ITEMS_PER_PAGE * MAX_PAGES;

    private final VelioraSuite plugin;
    private File file;
    private FileConfiguration data;
    private BufferedYamlWriter writer;

    public FishingBagDataManager(VelioraSuite plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.createFolder("data");
        file = new File(plugin.getDataFolder(), "data/fishing-bag.yml");
        if (!file.exists()) {
            try { file.createNewFile(); } catch (IOException exception) { plugin.getLogger().warning("Gagal membuat data/fishing-bag.yml"); }
        }
        data = YamlConfiguration.loadConfiguration(file);
        writer = new BufferedYamlWriter(plugin, file, data, "data/fishing-bag.yml");
        writer.start();
    }

    public List<FishingBagEntry> entries(OfflinePlayer player) {
        List<FishingBagEntry> result = new ArrayList<>();
        ConfigurationSection section = data.getConfigurationSection(base(player));
        if (section == null) return result;
        for (String key : section.getKeys(false)) {
            String path = base(player) + "." + key;
            CaughtFish fish = new CaughtFish(
                    data.getString(path + ".id", "unknown"),
                    data.getString(path + ".name", "Unknown Fish"),
                    FishRarity.fromKey(data.getString(path + ".rarity", "COMMON")),
                    data.getDouble(path + ".weight", 0.0D),
                    data.getInt(path + ".price", 0),
                    data.getString(path + ".origin", "VelioraFishing"),
                    data.getString(path + ".region", "Veliora"),
                    data.getString(path + ".mutation", "Normal"),
                    data.getDouble(path + ".mutation-multiplier", 1.0D)
            );
            int amount = data.getInt(path + ".amount", 0);
            if (amount > 0) result.add(new FishingBagEntry(key, fish, amount));
        }
        result.sort(Comparator.comparingInt((FishingBagEntry entry) -> entry.getFish().rarity().power()).reversed());
        return result;
    }

    public FishingBagEntry get(OfflinePlayer player, String key) {
        for (FishingBagEntry entry : entries(player)) if (entry.getKey().equals(key)) return entry;
        return null;
    }

    public boolean add(OfflinePlayer player, CaughtFish fish, int amount) {
        if (player == null || fish == null || amount <= 0) return false;
        String key = key(fish);
        String path = base(player) + "." + key;
        if (!data.contains(path) && entries(player).size() >= MAX_UNIQUE_ITEMS) return false;
        data.set(path + ".id", fish.id());
        data.set(path + ".name", fish.name());
        data.set(path + ".rarity", fish.rarity().name());
        data.set(path + ".weight", fish.weight());
        data.set(path + ".price", fish.price());
        data.set(path + ".origin", fish.origin());
        data.set(path + ".region", fish.region());
        data.set(path + ".mutation", fish.mutation());
        data.set(path + ".mutation-multiplier", fish.mutationMultiplier());
        data.set(path + ".amount", data.getInt(path + ".amount", 0) + amount);
        writer.markDirty();
        return true;
    }

    public void remove(OfflinePlayer player, String key, int amount) {
        if (player == null || key == null || amount <= 0) return;
        String path = base(player) + "." + key;
        int current = data.getInt(path + ".amount", 0);
        int left = Math.max(0, current - amount);
        if (left <= 0) data.set(path, null);
        else data.set(path + ".amount", left);
        writer.markDirty();
    }

    public void flush() {
        if (writer != null) writer.shutdown();
    }

    private String base(OfflinePlayer player) {
        return "players." + player.getUniqueId() + ".items";
    }

    private String key(CaughtFish fish) {
        String raw = fish.id() + "|" + fish.rarity().name() + "|" + fish.weight() + "|" + fish.price() + "|" + fish.origin() + "|" + fish.region() + "|" + fish.mutation();
        return Integer.toHexString(raw.hashCode()).replace('-', 'n');
    }
}
