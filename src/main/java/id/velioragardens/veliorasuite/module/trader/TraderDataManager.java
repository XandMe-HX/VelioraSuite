package id.velioragardens.veliorasuite.module.trader;

import id.velioragardens.veliorasuite.VelioraSuite;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

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
