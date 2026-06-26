package id.velioragardens.veliorasuite.module.boss;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.module.boss.model.BossDefinition;
import id.velioragardens.veliorasuite.module.boss.model.BossRarity;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;

import java.io.File;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class BossConfigManager {

    private final VelioraSuite plugin;
    private FileConfiguration config;
    private final Map<String, BossDefinition> bosses = new LinkedHashMap<>();
    private final Map<BossRarity, Double> rarityChance = new EnumMap<>(BossRarity.class);

    public BossConfigManager(VelioraSuite plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveResourceIfNotExists("modules/boss.yml");
        File file = new File(plugin.getDataFolder(), "modules/boss.yml");
        config = YamlConfiguration.loadConfiguration(file);
        loadRarityChance();
        loadBosses();
    }

    public boolean isEnabled() { return bool("settings.enabled", true); }
    public String prefix() { return str("messages.prefix", "&8[&cVelioraBoss&8] "); }
    public boolean isSpawnEnabled() { return bool("settings.spawn.enabled", true); }
    public int intervalMinutes() { return Math.max(1, integer("settings.spawn.interval-minutes", 30)); }
    public int despawnMinutes() { return Math.max(1, integer("settings.spawn.despawn-minutes", 20)); }
    public boolean announceSpawn() { return bool("settings.spawn.announce-spawn", true); }
    public boolean announceDeath() { return bool("settings.spawn.announce-death", true); }
    public boolean announceDespawn() { return bool("settings.spawn.announce-despawn", true); }
    public boolean allowMultiple() { return bool("settings.spawn.allow-multiple-active", false); }
    public boolean requireSpawnPoint() { return bool("settings.spawn.require-spawn-point", true); }

    public boolean bossBarEnabled() { return bool("bossbar.enabled", true); }
    public String bossBarTitle() { return str("bossbar.title", "&c%boss% &7- &f%health%&7/&f%max_health% HP &8| &e%time%"); }
    public boolean colorBossBarByRarity() { return bool("bossbar.color-by-rarity", true); }
    public BarStyle bossBarStyle() { return barStyle(str("bossbar.style", "SEGMENTED_20")); }
    public double bossBarRadius() { return number("bossbar-radius", 80.0D); }

    public boolean skillsEnabled() { return bool("skills.enabled", true); }
    public int skillCooldownSeconds() { return Math.max(3, integer("skills.cooldown-seconds", 12)); }
    public double groundSlamDamage() { return number("skills.damage.ground-slam", 6.0D); }
    public double fireBombDamage() { return number("skills.damage.fire-bomb", 8.0D); }
    public int maxMinions() { return Math.max(0, integer("skills.summon.max-minions", 8)); }
    public int minionsPerCast() { return Math.max(1, integer("skills.summon.minions-per-cast", 3)); }
    public List<String> minionTypes() { List<String> list = config.getStringList("skills.summon.types"); return list.isEmpty() ? List.of("ZOMBIE", "HUSK", "VINDICATOR", "DROWNED") : list; }

    public double minDamageToReward() { return number("rewards.min-damage-to-reward", 20.0D); }
    public long rewardMoney(BossRarity rarity, String key) { return Math.max(0L, config.getLong("rewards.money." + rarity.name().toLowerCase(Locale.ROOT) + "." + key, 0L)); }
    public int rewardMaterial(BossRarity rarity, String key) { return Math.max(0, config.getInt("rewards.materials." + rarity.name().toLowerCase(Locale.ROOT) + "." + key, 0)); }

    public boolean preventBlockDamage() { return bool("protection.prevent-block-damage", true); }
    public boolean allowBossDamageInProtectedRegion() { return bool("protection.allow-boss-damage-in-protected-region", true); }

    public Map<String, BossDefinition> bosses() { return bosses; }
    public Map<BossRarity, Double> rarityChance() { return rarityChance; }

    public String message(String key, String fallback) { return str("messages." + key, fallback).replace("%prefix%", prefix()); }
    public String color(String input) { return ChatColor.translateAlternateColorCodes('&', input == null ? "" : input); }
    public Sound sound(String path, String fallback) { try { return Sound.valueOf(str(path, fallback).toUpperCase(Locale.ROOT)); } catch (Exception ignored) { return Sound.ENTITY_EXPERIENCE_ORB_PICKUP; } }
    public Particle particle(String path, String fallback) { try { return Particle.valueOf(str(path, fallback).toUpperCase(Locale.ROOT)); } catch (Exception ignored) { return Particle.CLOUD; } }

    public Material material(String name, Material fallback) { Material material = Material.matchMaterial(name == null ? "" : name.toUpperCase(Locale.ROOT)); return material == null ? fallback : material; }
    public EntityType entityType(String name, EntityType fallback) { try { return EntityType.valueOf(name.toUpperCase(Locale.ROOT)); } catch (Exception ignored) { return fallback; } }

    private void loadRarityChance() {
        rarityChance.clear();
        for (BossRarity rarity : BossRarity.values()) rarityChance.put(rarity, Math.max(0.0D, number("rarity-chance." + rarity.name().toLowerCase(Locale.ROOT), defaultChance(rarity))));
    }

    private void loadBosses() {
        bosses.clear();
        ConfigurationSection section = config.getConfigurationSection("bosses");
        if (section == null) return;
        for (String id : section.getKeys(false)) {
            ConfigurationSection boss = section.getConfigurationSection(id);
            if (boss == null) continue;
            EntityType type = entityType(boss.getString("entity", "ZOMBIE"), EntityType.ZOMBIE);
            if (!isAllowedBossType(type)) {
                plugin.getLogger().warning("VelioraBoss: entity boss tidak aman/terbang, skip: " + type);
                continue;
            }
            BossRarity rarity = BossRarity.from(boss.getString("rarity", "COMMON"));
            bosses.put(id.toLowerCase(Locale.ROOT), new BossDefinition(
                    id.toLowerCase(Locale.ROOT),
                    type,
                    boss.getString("display-name", id),
                    rarity,
                    clamp(boss.getDouble("health", 100.0D), 100.0D, 500.0D),
                    clamp(boss.getDouble("damage", 4.0D), 4.0D, 10.0D),
                    Math.max(1.0D, boss.getDouble("scale", 3.0D))
            ));
        }
    }

    private boolean isAllowedBossType(EntityType type) {
        return switch (type) {
            case WARDEN, RAVAGER, IRON_GOLEM, ZOMBIE, HUSK, DROWNED, PIGLIN_BRUTE, WITHER_SKELETON, VINDICATOR, EVOKER -> true;
            default -> false;
        };
    }

    private double defaultChance(BossRarity rarity) {
        return switch (rarity) { case COMMON -> 50.0D; case RARE -> 25.0D; case EPIC -> 15.0D; case LEGENDARY -> 8.0D; case MYTHIC -> 2.0D; };
    }

    private BarStyle barStyle(String raw) { try { return BarStyle.valueOf(raw.toUpperCase(Locale.ROOT)); } catch (Exception ignored) { return BarStyle.SEGMENTED_20; } }
    private BarColor barColor(String raw) { try { return BarColor.valueOf(raw.toUpperCase(Locale.ROOT)); } catch (Exception ignored) { return BarColor.RED; } }
    private String str(String path, String fallback) { return config == null || !config.contains(path) ? fallback : config.getString(path, fallback); }
    private boolean bool(String path, boolean fallback) { return config == null || !config.contains(path) ? fallback : config.getBoolean(path, fallback); }
    private int integer(String path, int fallback) { return config == null || !config.contains(path) ? fallback : config.getInt(path, fallback); }
    private double number(String path, double fallback) { return config == null || !config.contains(path) ? fallback : config.getDouble(path, fallback); }
    private double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }
}
