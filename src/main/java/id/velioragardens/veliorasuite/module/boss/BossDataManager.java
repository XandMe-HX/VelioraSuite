package id.velioragardens.veliorasuite.module.boss;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.module.boss.model.BossSpawnPoint;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
        stats.set("total-kills", stats.getInt("total-kills", 0) + 1);
        stats.set("kills." + bossId, stats.getInt("kills." + bossId, 0) + 1);
        saveStats();
    }

    public int totalKills() { return stats.getInt("total-kills", 0); }

    public Map<String, Integer> bossKills() {
        Map<String, Integer> map = new LinkedHashMap<>();
        ConfigurationSection section = stats.getConfigurationSection("kills");
        if (section == null) return map;
        for (String key : section.getKeys(false)) map.put(key, section.getInt(key, 0));
        return map;
    }

    public void addDamage(Player player, double damage) {
        if (player == null || damage <= 0.0D) return;
        String path = "players." + player.getUniqueId();
        stats.set(path + ".name", player.getName());
        stats.set(path + ".total-damage", stats.getDouble(path + ".total-damage", 0.0D) + damage);
        saveStats();
    }

    public void addParticipation(Player player) {
        if (player == null) return;
        String path = "players." + player.getUniqueId();
        stats.set(path + ".name", player.getName());
        stats.set(path + ".participation-kills", stats.getInt(path + ".participation-kills", 0) + 1);
        saveStats();
    }

    public List<PlayerDamageStat> topDamage(int limit) {
        List<PlayerDamageStat> list = new ArrayList<>();
        ConfigurationSection section = stats.getConfigurationSection("players");
        if (section == null) return list;
        for (String key : section.getKeys(false)) {
            String path = "players." + key;
            list.add(new PlayerDamageStat(key, stats.getString(path + ".name", key.substring(0, Math.min(8, key.length()))), stats.getDouble(path + ".total-damage", 0.0D), stats.getInt(path + ".participation-kills", 0)));
        }
        list.sort(Comparator.comparingDouble(PlayerDamageStat::damage).reversed());
        return list.subList(0, Math.min(limit, list.size()));
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

    public record PlayerDamageStat(String uuid, String name, double damage, int participationKills) {}
}
