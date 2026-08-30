package id.velioragardens.veliorasuite.module.race;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.core.storage.BufferedYamlWriter;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.Locale;
import java.util.UUID;

/** UUID-only storage; it deliberately does not modify player inventory, pets, or economy. */
public final class RaceManager {
    private final VelioraSuite plugin;
    private final File dataFile;
    private YamlConfiguration config;
    private YamlConfiguration data;
    private BufferedYamlWriter writer;

    public RaceManager(VelioraSuite plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "data/races.yml");
    }
    public void load() {
        reloadConfig();
        data = YamlConfiguration.loadConfiguration(dataFile);
        writer = new BufferedYamlWriter(plugin, dataFile, data, "data/races.yml");
        writer.start();
    }
    public void reloadConfig() { config = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "modules/race.yml")); }
    public boolean enforcementEnabled() { return config.getBoolean("settings.enforce-selection", false); }
    public boolean selected(UUID uuid) { return data.contains("players." + uuid + ".race"); }
    public String race(UUID uuid) { return data.getString("players." + uuid + ".race", "BELUM_MEMILIH").toUpperCase(Locale.ROOT); }
    public void reset(UUID uuid) { data.set("players." + uuid, null); writer.markDirty(); writer.flushAsync(); }
    public void shutdown() { if (writer != null) writer.shutdown(); }
}
