package id.velioragardens.veliorasuite.module.boss;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.module.boss.model.BossSpawnPoint;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public final class BossDataManager {

    private final VelioraSuite plugin;
    private File spawnsFile;
    private File statsFile;
    private FileConfiguration spawns;
    private FileConfiguration stats;
    private final Map<String, BossSpawnPoint> spawnPoints = new LinkedHashMap<>();

    public BossDataManager(VelioraSuite plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.createFolder("data");
        spawnsFile = new File(plugin.getDataFolder(), "data/boss-spawns.yml");
        statsFile = new File(plugin.getDataFolder(), "data/boss-stats.yml");
        create(spawnsFile);
        create(statsFile);
        spawns = YamlConfiguration.loadConfiguration(spawnsFile);
        stats = YamlConfiguration.loadConfiguration(statsFile);
        loadSpawnPoints();
    }

    public void setSpawnPoint(BossSpawnPoint point) {
        spawnPoints.put(point.name().toLowerCase(java.util.Locale.ROOT), point);
        String path = "spawn-points." + point.name().toLowerCase(java.util.Locale.ROOT);
        spawns.set(path + ".world", point.world());
        spawns.set(path + ".x", point.x());
        spawns.set(path + ".y", point.y());
        spawns.set(path + ".z", point.z());
        spawns.set(path + ".yaw", point.yaw());
        spawns.set(path + ".pitch", point.pitch());
        saveSpawns();
    }

    public Map<String, BossSpawnPoint> spawnPoints() { return spawnPoints; }

    public void addKill(String bossId) {
        stats.set("kills." + bossId, stats.getInt("kills." + bossId, 0) + 1);
        saveStats();
    }

    private void loadSpawnPoints() {
        spawnPoints.clear();
        ConfigurationSection section = spawns.getConfigurationSection("spawn-points");
        if (section == null) return;
        for (String name : section.getKeys(false)) {
            String path = "spawn-points." + name;
            spawnPoints.put(name.toLowerCase(java.util.Locale.ROOT), new BossSpawnPoint(
                    name.toLowerCase(java.util.Locale.ROOT),
                    spawns.getString(path + ".world", "world"),
                    spawns.getDouble(path + ".x"),
                    spawns.getDouble(path + ".y"),
                    spawns.getDouble(path + ".z"),
                    (float) spawns.getDouble(path + ".yaw"),
                    (float) spawns.getDouble(path + ".pitch")
            ));
        }
    }

    private void saveSpawns() { try { spawns.save(spawnsFile); } catch (IOException exception) { plugin.getLogger().warning("Gagal menyimpan boss-spawns.yml"); } }
    private void saveStats() { try { stats.save(statsFile); } catch (IOException exception) { plugin.getLogger().warning("Gagal menyimpan boss-stats.yml"); } }
    private void create(File file) { if (!file.exists()) try { file.createNewFile(); } catch (IOException exception) { plugin.getLogger().warning("Gagal membuat " + file.getName()); } }
}
