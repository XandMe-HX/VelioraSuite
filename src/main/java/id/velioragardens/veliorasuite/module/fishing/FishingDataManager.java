package id.velioragardens.veliorasuite.module.fishing;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.core.storage.BufferedYamlWriter;
import id.velioragardens.veliorasuite.module.fishing.model.CaughtFish;
import id.velioragardens.veliorasuite.module.fishing.model.FishRarity;
import id.velioragardens.veliorasuite.module.fishing.model.PlayerFishingStats;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class FishingDataManager {

    private final VelioraSuite plugin;
    private File file;
    private FileConfiguration data;
    private BufferedYamlWriter writer;

    public FishingDataManager(VelioraSuite plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.createFolder("data");
        file = new File(plugin.getDataFolder(), "data/fishing.yml");
        if (!file.exists()) {
            try { file.createNewFile(); } catch (IOException exception) { plugin.getLogger().warning("Gagal membuat data/fishing.yml"); }
        }
        data = YamlConfiguration.loadConfiguration(file);
        writer = new BufferedYamlWriter(plugin, file, data, "data/fishing.yml");
        writer.start();
    }

    public PlayerFishingStats getOrCreate(OfflinePlayer player) {
        UUID uuid = player.getUniqueId();
        String path = "players." + uuid;
        PlayerFishingStats stats = new PlayerFishingStats(uuid, player.getName());
        stats.setName(data.getString(path + ".name", player.getName()));
        stats.setTotalCatches(data.getInt(path + ".total-catches", 0));
        stats.setTotalSold(data.getInt(path + ".total-sold", 0));
        stats.setTotalMoneyEarned(data.getLong(path + ".total-money-earned", 0L));
        stats.setBestRarity(FishRarity.fromKey(data.getString(path + ".best-rarity", "TRASH")));
        stats.setBestFishName(data.getString(path + ".best-fish-name", "-"));
        stats.setBestFishWeight(data.getDouble(path + ".best-fish-weight", 0.0D));
        stats.setBestFishPrice(data.getInt(path + ".best-fish-price", 0));
        if (player.getName() != null) stats.setName(player.getName());
        save(stats);
        return stats;
    }

    public void recordCatch(OfflinePlayer player, CaughtFish fish) {
        PlayerFishingStats stats = getOrCreate(player);
        stats.addCatch();
        if (isBetter(fish, stats)) {
            stats.setBestRarity(fish.rarity());
            stats.setBestFishName(fish.name());
            stats.setBestFishWeight(fish.weight());
            stats.setBestFishPrice(fish.price());
        }
        save(stats);
    }

    public void recordSale(OfflinePlayer player, int soldAmount, long earned) {
        PlayerFishingStats stats = getOrCreate(player);
        stats.addSold(soldAmount);
        stats.addMoneyEarned(earned);
        save(stats);
    }

    public List<PlayerFishingStats> top(int limit) {
        List<PlayerFishingStats> result = new ArrayList<>();
        ConfigurationSection players = data.getConfigurationSection("players");
        if (players == null) return result;
        for (String key : players.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                result.add(getOrCreate(plugin.getServer().getOfflinePlayer(uuid)));
            } catch (IllegalArgumentException ignored) { }
        }
        result.sort(Comparator
                .comparingInt(PlayerFishingStats::getTotalCatches).reversed()
                .thenComparing(Comparator.comparingInt((PlayerFishingStats stats) -> stats.getBestRarity().power()).reversed())
                .thenComparing(Comparator.comparingDouble(PlayerFishingStats::getBestFishWeight).reversed()));
        return result.stream().limit(limit).toList();
    }

    public void save(PlayerFishingStats stats) {
        String path = "players." + stats.getUuid();
        data.set(path + ".name", stats.getName());
        data.set(path + ".total-catches", stats.getTotalCatches());
        data.set(path + ".total-sold", stats.getTotalSold());
        data.set(path + ".total-money-earned", stats.getTotalMoneyEarned());
        data.set(path + ".best-rarity", stats.getBestRarity().name());
        data.set(path + ".best-fish-name", stats.getBestFishName());
        data.set(path + ".best-fish-weight", stats.getBestFishWeight());
        data.set(path + ".best-fish-price", stats.getBestFishPrice());
        writer.markDirty();
    }

    public void flush() {
        if (writer != null) writer.shutdown();
    }

    private boolean isBetter(CaughtFish fish, PlayerFishingStats stats) {
        if (fish.rarity().power() > stats.getBestRarity().power()) return true;
        if (fish.rarity().power() < stats.getBestRarity().power()) return false;
        return fish.weight() > stats.getBestFishWeight();
    }
}
