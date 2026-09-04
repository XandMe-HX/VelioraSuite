package id.velioragardens.veliorasuite.module.race;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.core.storage.BufferedYamlWriter;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** UUID-only storage; it deliberately does not modify player inventory, pets, or economy. */
public final class RaceManager {
    private final VelioraSuite plugin;
    private final File dataFile;
    private final File configFile;
    private YamlConfiguration config;
    private YamlConfiguration data;
    private BufferedYamlWriter writer;
    private final Map<UUID, String> drafts = new ConcurrentHashMap<>();

    public RaceManager(VelioraSuite plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "data/races.yml");
        this.configFile = new File(plugin.getDataFolder(), "modules/race.yml");
    }
    public void load() {
        reloadConfig();
        data = YamlConfiguration.loadConfiguration(dataFile);
        writer = new BufferedYamlWriter(plugin, dataFile, data, "data/races.yml");
        writer.start();
    }
    public void reloadConfig() {
        config = YamlConfiguration.loadConfiguration(configFile);
        if(!config.getBoolean("migrations.change-price-20k",false)) {
            if(configFile.isFile()) try { java.nio.file.Files.copy(configFile.toPath(),new File(configFile.getParentFile(),"race.pre-20k-"+System.currentTimeMillis()+".yml").toPath()); }
            catch(java.io.IOException e) { plugin.getLogger().warning("Backup race gagal; migrasi harga ditunda."); return; }
            config.set("change.cost",20000D);
            config.set("migrations.change-price-20k",true);
            try { config.save(configFile); } catch(java.io.IOException e) { plugin.getLogger().warning("Gagal menyimpan harga race: "+e.getMessage()); }
        }
    }
    public boolean enforcementEnabled() { return config.getBoolean("settings.enforce-selection", false); }
    public double changeCost() { return Math.max(0D, config.getDouble("change.cost", 20000D)); }
    public long changeCooldownMillis() { return Math.max(0L, config.getLong("change.cooldown-days", 7L)) * 86_400_000L; }
    public long nextChangeAt(UUID uuid) { return data.getLong("players." + uuid + ".last-changed-at", data.getLong("players." + uuid + ".selected-at", 0L)) + changeCooldownMillis(); }
    public long changeRemaining(UUID uuid) { return Math.max(0L, nextChangeAt(uuid) - System.currentTimeMillis()); }
    public boolean canChange(UUID uuid) { return selected(uuid) && changeRemaining(uuid) == 0L; }
    public long formCooldownMillis() { return Math.max(0L, config.getLong("form-change.cooldown-days", 2L)) * 86_400_000L; }
    public long formChangeRemaining(UUID uuid) { return Math.max(0L, data.getLong("players." + uuid + ".last-form-changed-at", 0L) + formCooldownMillis() - System.currentTimeMillis()); }
    public boolean canChangeForm(UUID uuid) { return selected(uuid) && formChangeRemaining(uuid) == 0L; }
    /** Quest rewards are intentionally limited to Human and Angel so race bonuses stay readable. */
    public double questRewardMultiplier(UUID uuid) {
        if (!selected(uuid)) return 1.0D;
        return switch (race(uuid)) { case "HUMAN", "ANGEL" -> 1.15D; default -> 1.0D; };
    }
    public void setEnforcementEnabled(boolean enabled) {
        config.set("settings.enforce-selection", enabled);
        try { config.save(configFile); } catch (Exception exception) { plugin.getLogger().warning("Gagal menyimpan pengaturan race: " + exception.getMessage()); }
    }
    public boolean selected(UUID uuid) { return data.contains("players." + uuid + ".race"); }
    /** Unselected players display as Human, but receive no race bonus until confirmation. */
    public String race(UUID uuid) { return data.getString("players." + uuid + ".race", "HUMAN").toUpperCase(Locale.ROOT); }
    public String form(UUID uuid) { return data.getString("players." + uuid + ".form", "ADULT").toUpperCase(Locale.ROOT); }
    public double scaleFor(String form) {
        String key = switch (form.toUpperCase(Locale.ROOT)) {
            case "CHILD" -> "child";
            case "TALL" -> "tall";
            default -> "adult";
        };
        return Math.clamp(config.getDouble("scale." + key, 1.0D), 0.5D, 1.2D);
    }
    public void setDraft(UUID uuid, String race) { drafts.put(uuid, race.toUpperCase(Locale.ROOT)); }
    public String draft(UUID uuid) { return drafts.get(uuid); }
    public void clearDraft(UUID uuid) { drafts.remove(uuid); }
    public void complete(UUID uuid, String race, String form) {
        String normalizedRace = race.toUpperCase(Locale.ROOT);
        String normalizedForm = form.toUpperCase(Locale.ROOT);
        data.set("players." + uuid + ".race", normalizedRace);
        data.set("players." + uuid + ".form", normalizedForm);
        data.set("players." + uuid + ".selected-at", System.currentTimeMillis());
        clearDraft(uuid);
        writer.markDirty();
        writer.flushAsync();
    }
    public void change(UUID uuid, String race, String form) {
        data.set("players." + uuid + ".race", race.toUpperCase(Locale.ROOT));
        data.set("players." + uuid + ".form", form.toUpperCase(Locale.ROOT));
        data.set("players." + uuid + ".last-changed-at", System.currentTimeMillis());
        clearDraft(uuid);
        writer.markDirty();
        writer.flushAsync();
    }
    public void changeForm(UUID uuid, String form) {
        data.set("players." + uuid + ".form", form.toUpperCase(Locale.ROOT));
        data.set("players." + uuid + ".last-form-changed-at", System.currentTimeMillis());
        writer.markDirty();
        writer.flushAsync();
    }
    public void reset(UUID uuid) { data.set("players." + uuid, null); writer.markDirty(); writer.flushAsync(); }
    public void shutdown() { if (writer != null) writer.shutdown(); drafts.clear(); }
}
