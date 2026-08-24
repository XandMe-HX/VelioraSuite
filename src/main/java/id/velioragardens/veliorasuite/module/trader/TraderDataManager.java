package id.velioragardens.veliorasuite.module.trader;

import id.velioragardens.veliorasuite.VelioraSuite;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;

public final class TraderDataManager {

    private final VelioraSuite plugin;
    private File traderFile;
    private File purchasesFile;
    private FileConfiguration traderData;
    private FileConfiguration purchasesData;

    public TraderDataManager(VelioraSuite plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.createFolder("data");
        traderFile = new File(plugin.getDataFolder(), "data/trader.yml");
        purchasesFile = new File(plugin.getDataFolder(), "data/trader-purchases.yml");
        createIfMissing(traderFile);
        createIfMissing(purchasesFile);
        traderData = YamlConfiguration.loadConfiguration(traderFile);
        purchasesData = YamlConfiguration.loadConfiguration(purchasesFile);
    }

    public void saveActive(Location location, long despawnAt) {
        if (location == null || location.getWorld() == null) return;
        traderData.set("active.world", location.getWorld().getName());
        traderData.set("active.x", location.getX());
        traderData.set("active.y", location.getY());
        traderData.set("active.z", location.getZ());
        traderData.set("active.despawn-at", despawnAt);
        flushTrader();
    }

    public boolean hasActive() {
        return traderData.contains("active.world");
    }

    public long getActiveDespawnAt() {
        return traderData.getLong("active.despawn-at", 0L);
    }

    public Location getActiveLocation() {
        String worldName = traderData.getString("active.world", null);
        if (worldName == null) return null;
        World world = plugin.getServer().getWorld(worldName);
        if (world == null) return null;
        return new Location(world, traderData.getDouble("active.x"), traderData.getDouble("active.y"), traderData.getDouble("active.z"));
    }

    public void clearActive() {
        traderData.set("active", null);
        flushTrader();
    }

    public void saveNextSpawnAt(long nextSpawnAt) {
        traderData.set("next-spawn-at", nextSpawnAt);
        flushTrader();
    }

    public long getNextSpawnAt(long fallback) {
        return traderData.getLong("next-spawn-at", fallback);
    }

    public void saveCampBackup(Map<String, String> backup) {
        traderData.set("camp-backup", null);
        for (Map.Entry<String, String> entry : backup.entrySet()) traderData.set("camp-backup." + entry.getKey(), entry.getValue());
        flushTrader();
    }

    public Map<String, String> loadCampBackup() {
        Map<String, String> backup = new LinkedHashMap<>();
        ConfigurationSection section = traderData.getConfigurationSection("camp-backup");
        if (section == null) return backup;
        for (String key : section.getKeys(false)) backup.put(key, section.getString(key, "minecraft:air"));
        return backup;
    }

    public void clearCampBackup() {
        traderData.set("camp-backup", null);
        flushTrader();
    }

    public String getOfferPeriod() { return traderData.getString("offers.period", ""); }

    public List<String> getOfferIds() { return traderData.getStringList("offers.items"); }

    public void saveOffers(String period, List<String> itemIds) {
        traderData.set("offers.period", period);
        traderData.set("offers.items", itemIds);
        traderData.set("offers.updated-at", System.currentTimeMillis());
        flushTrader();
    }

    public FileConfiguration purchases() {
        return purchasesData;
    }

    public void flushAll() {
        flushTrader();
        flushPurchases();
    }

    public void flushPurchases() {
        try { purchasesData.save(purchasesFile); } catch (IOException exception) { plugin.getLogger().warning("Gagal menyimpan data/trader-purchases.yml"); }
    }

    private void flushTrader() {
        try { traderData.save(traderFile); } catch (IOException exception) { plugin.getLogger().warning("Gagal menyimpan data/trader.yml"); }
    }

    private void createIfMissing(File file) {
        if (file.exists()) return;
        try { file.createNewFile(); } catch (IOException exception) { plugin.getLogger().warning("Gagal membuat " + file.getName()); }
    }
}
