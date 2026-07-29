package id.velioragardens.veliorasuite.module.boss;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.module.boss.model.BossDefinition;
import id.velioragardens.veliorasuite.module.boss.model.BossRarity;
import id.velioragardens.veliorasuite.module.boss.model.BossSkillType;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.boss.BarStyle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;

import java.io.File;
import java.util.ArrayList;
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

    public BossConfigManager(VelioraSuite plugin) { this.plugin = plugin; }

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
    public boolean realtimeHourlySpawnEnabled() { return bool("settings.spawn.realtime-hourly.enabled", true); }
    public int intervalMinutes() { return realtimeHourlySpawnEnabled() ? 60 : Math.max(1, integer("settings.spawn.interval-minutes", 60)); }
    public int spawnRetryMinutes() { return Math.max(1, integer("settings.spawn.retry-minutes", 5)); }
    public int despawnMinutes() { return Math.max(1, integer("settings.spawn.despawn-minutes", 25)); }
    public boolean announceSpawn() { return bool("settings.spawn.announce-spawn", true); }
    public boolean announceDeath() { return bool("settings.spawn.announce-death", true); }
    public boolean announceDespawn() { return bool("settings.spawn.announce-despawn", true); }
    public boolean allowMultiple() { return bool("settings.spawn.allow-multiple-active", false); }
    public boolean requireSpawnPoint() { return bool("settings.spawn.require-spawn-point", true); }
    public List<Integer> warningTimesMinutes() { List<Integer> list = config == null ? List.of() : config.getIntegerList("settings.spawn.warning-times-minutes"); return list.isEmpty() ? List.of(5, 1) : list; }
    public boolean randomScaleEnabled() { return bool("settings.boss-size.random-enabled", true); }
    public double randomScaleMin() { return Math.max(1.0D, number("settings.boss-size.min", 4.0D)); }
    public double randomScaleMax() { return Math.max(randomScaleMin(), number("settings.boss-size.max", 7.0D)); }
    public double bossArmor() { return Math.max(0.0D, number("settings.combat.armor", 18.0D)); }
    public double bossArmorToughness() { return Math.max(0.0D, number("settings.combat.armor-toughness", 12.0D)); }
    public double bossKnockbackResistance() { return clamp(number("settings.combat.knockback-resistance", 0.85D), 0.0D, 1.0D); }
    public boolean healthScalingEnabled() { return bool("settings.combat.health-scale-by-nearby-players", true); }
    public double healthPerPlayerMultiplier() { return Math.max(0.0D, number("settings.combat.health-per-player-multiplier", 0.20D)); }
    public double maxHealthMultiplier() { return Math.max(1.0D, number("settings.combat.max-health-multiplier", 2.75D)); }
    public boolean spawnTitleEnabled() { return bool("effects.spawn.title-enabled", true); }
    public String spawnTitle() { return str("effects.spawn.title", "&c%boss%"); }
    public String spawnSubtitle() { return str("effects.spawn.subtitle", "&7Boss %rarity% muncul di &f%world%"); }
    public int spawnParticleCount() { return Math.max(1, integer("effects.spawn.particle-count", 120)); }
    public boolean bossBarEnabled() { return bool("bossbar.enabled", true); }
    public String bossBarTitle() { return str("bossbar.title", "&c%boss% &7- &f%health%&7/&f%max_health% HP &8| &e%time%"); }
    public boolean colorBossBarByRarity() { return bool("bossbar.color-by-rarity", true); }
    public BarStyle bossBarStyle() { return barStyle(str("bossbar.style", "SEGMENTED_20")); }
    public double bossBarRadius() { return number("bossbar-radius", 80.0D); }
    public boolean skillsEnabled() { return bool("skills.enabled", true); }
    public int skillCooldownSeconds() { return Math.max(3, integer("skills.cooldown-seconds", 14)); }
    public double groundSlamDamage() { return number("skills.damage.ground-slam", 6.0D); }
    public double fireBombDamage() { return number("skills.damage.fire-bomb", 8.0D); }
    public int maxMinions() { return Math.max(0, integer("skills.summon.max-minions", 8)); }
    public int minionsPerCast() { return Math.max(1, integer("skills.summon.minions-per-cast", 3)); }
    public List<String> minionTypes() { List<String> list = config == null ? List.of() : config.getStringList("skills.summon.types"); return list.isEmpty() ? List.of("ZOMBIE", "HUSK", "DROWNED", "ZOMBIFIED_PIGLIN") : list; }
    public List<BossSkillType> defaultSkills() { return parseSkills(config == null ? List.of() : config.getStringList("skills.default")); }
    public boolean arenaEnabled() { return bool("arena.enabled", true); }
    public double arenaRadius() { return Math.max(5.0D, number("arena.radius", 45.0D)); }
    public double targetRadius() { return Math.max(5.0D, number("arena.target-radius", 45.0D)); }
    public boolean leashToSpawn() { return bool("arena.leash-to-spawn", true); }
    public boolean teleportBackIfFar() { return bool("arena.teleport-back-if-far", true); }
    public double teleportBackDistance() { return Math.max(arenaRadius(), number("arena.teleport-back-distance", 55.0D)); }
    public boolean teleportBackIfBelowSpawnY() { return bool("arena.teleport-back-if-below-spawn-y", true); }
    public double belowYOffset() { return Math.max(1.0D, number("arena.below-y-offset", 12.0D)); }
    public boolean removeMinionOutsideRadius() { return bool("arena.remove-minion-outside-radius", true); }
    public boolean targetingEnabled() { return bool("targeting.enabled", true); }
    public boolean targetingIncludeSurvival() { return bool("targeting.include-survival", true); }
    public boolean targetingIncludeAdventure() { return bool("targeting.include-adventure", true); }
    public boolean targetingIncludeCreative() { return bool("targeting.include-creative", false); }
    public boolean targetingIncludeSpectator() { return bool("targeting.include-spectator", false); }
    public double targetingRadiusHorizontal() { return Math.max(1.0D, number("targeting.target-radius-horizontal", 64.0D)); }
    public double targetingRadiusVertical() { return Math.max(1.0D, number("targeting.target-radius-vertical", 32.0D)); }
    public int retargetIntervalSeconds() { return Math.max(1, integer("targeting.retarget-interval-seconds", 3)); }
    public boolean forceTargetNearest() { return bool("targeting.force-target-nearest", true); }
    public boolean ignoreLineOfSight() { return bool("targeting.ignore-line-of-sight", true); }
    public boolean targetPlayersAbove() { return bool("targeting.target-players-above", true); }
    public boolean targetPlayersBelow() { return bool("targeting.target-players-below", true); }
    public boolean noTargetTeleportBack() { return bool("targeting.no-target-teleport-back", false); }
    public boolean debugTargeting() { return bool("debug.targeting", false); }
    public double minDamageToReward() { return number("rewards.min-damage-to-reward", 20.0D); }
    public double minDamageContributionPercent() { return clamp(number("rewards.min-damage-contribution-percent", 5.0D), 0.0D, 100.0D); }
    public long rewardCooldownMillis() { return Math.max(0L, (long) integer("rewards.reward-cooldown-seconds", 60)) * 1000L; }
    public int rewardMaterial(BossRarity rarity, String key) { return Math.max(0, config == null ? 0 : config.getInt("rewards.materials." + rarity.name().toLowerCase(Locale.ROOT) + "." + key, 0)); }
    public boolean defaultMoneyEnabled() { return bool("rewards.default-money.enabled", true); }
    public long defaultMoneyMin() { return clampMoney(integer("rewards.default-money.min", 10_000)); }
    public long defaultMoneyMax() { return clampMoney(integer("rewards.default-money.max", 150_000)); }
    public boolean bossMoneyEnabled(BossDefinition definition) { return bool("bosses." + definition.id() + ".rewards.money.enabled", defaultMoneyEnabled()); }
    public long bossMoneyMin(BossDefinition definition) { return clampMoney(integer("bosses." + definition.id() + ".rewards.money.min", (int) defaultMoneyMin())); }
    public long bossMoneyMax(BossDefinition definition) { return clampMoney(integer("bosses." + definition.id() + ".rewards.money.max", (int) defaultMoneyMax())); }
    public boolean topDamageBonusEnabled() { return bool("rewards.top-damage-bonus.enabled", true); }
    public long topBonusMin(int rankIndex) { return clampMoney(integer("rewards.top-damage-bonus." + rankKey(rankIndex) + ".min", defaultTopMin(rankIndex))); }
    public long topBonusMax(int rankIndex) { return clampMoney(integer("rewards.top-damage-bonus." + rankKey(rankIndex) + ".max", defaultTopMax(rankIndex))); }
    public boolean moneyTotalCapEnabled() { return bool("rewards.money-total-cap.enabled", true); }
    public long moneyTotalCapMax() { return clampMoney(integer("rewards.money-total-cap.max-per-player", 150_000)); }
    public boolean preventBlockDamage() { return bool("protection.prevent-block-damage", true); }
    public boolean allowBossDamageInProtectedRegion() { return bool("protection.allow-boss-damage-in-protected-region", true); }
    public Map<String, BossDefinition> bosses() { return bosses; }
    public Map<BossRarity, Double> rarityChance() { return rarityChance; }
    public String message(String key, String fallback) { return str("messages." + key, fallback).replace("%prefix%", prefix()); }
    public String color(String input) { return ChatColor.translateAlternateColorCodes('&', input == null ? "" : input); }
    public String plain(String input) { return ChatColor.stripColor(color(input == null ? "" : input)); }
    public Sound sound(String path, String fallback) { try { return Sound.valueOf(str(path, fallback).toUpperCase(Locale.ROOT)); } catch (Exception ignored) { return Sound.ENTITY_EXPERIENCE_ORB_PICKUP; } }
    public Particle particle(String path, String fallback) { try { return Particle.valueOf(str(path, fallback).toUpperCase(Locale.ROOT)); } catch (Exception ignored) { return Particle.CLOUD; } }
    public Material material(String name, Material fallback) { Material material = Material.matchMaterial(name == null ? "" : name.toUpperCase(Locale.ROOT)); return material == null ? fallback : material; }
    public EntityType entityType(String name, EntityType fallback) { try { return EntityType.valueOf(name.toUpperCase(Locale.ROOT)); } catch (Exception ignored) { return fallback; } }

    private void loadRarityChance() { rarityChance.clear(); for (BossRarity rarity : BossRarity.values()) rarityChance.put(rarity, Math.max(0.0D, number("rarity-chance." + rarity.name().toLowerCase(Locale.ROOT), defaultChance(rarity)))); }
    private void loadBosses() {
        bosses.clear();
        ConfigurationSection section = config == null ? null : config.getConfigurationSection("bosses");
        if (section == null) return;
        for (String id : section.getKeys(false)) {
            ConfigurationSection boss = section.getConfigurationSection(id);
            if (boss == null) continue;
            EntityType type = entityType(boss.getString("entity", "ZOMBIE"), EntityType.ZOMBIE);
            if (!isAllowedBossType(type)) { plugin.getLogger().warning("VelioraBoss: entity boss tidak aman/terbang atau tidak dipakai lagi, skip: " + type); continue; }
            BossRarity rarity = BossRarity.from(boss.getString("rarity", "COMMON"));
            List<BossSkillType> skills = parseSkills(boss.getStringList("skills"));
            bosses.put(id.toLowerCase(Locale.ROOT), new BossDefinition(id.toLowerCase(Locale.ROOT), type, boss.getString("display-name", id), rarity, clamp(boss.getDouble("health", 5000.0D), 800.0D, 50_000.0D), clamp(boss.getDouble("damage", 4.0D), 3.0D, 20.0D), Math.max(1.0D, boss.getDouble("scale", 4.0D)), skills.isEmpty() ? defaultSkills() : skills));
        }
    }
    private List<BossSkillType> parseSkills(List<String> raw) { List<BossSkillType> skills = new ArrayList<>(); for (String value : raw) { BossSkillType skill = BossSkillType.from(value); if (skill != null && !skills.contains(skill)) skills.add(skill); } if (skills.isEmpty()) skills.addAll(List.of(BossSkillType.GROUND_SLAM, BossSkillType.SUMMON_MINIONS, BossSkillType.FIRE_BOMB, BossSkillType.PULL_AURA, BossSkillType.POISON_CLOUD, BossSkillType.RAGE_MODE)); return skills; }
    private boolean isAllowedBossType(EntityType type) { return switch (type) { case WARDEN, RAVAGER, ZOMBIE, HUSK, DROWNED, PIGLIN_BRUTE, WITHER_SKELETON, VINDICATOR, EVOKER -> true; default -> false; }; }
    private double defaultChance(BossRarity rarity) { return switch (rarity) { case COMMON -> 45.0D; case RARE -> 27.0D; case EPIC -> 16.0D; case LEGENDARY -> 9.0D; case MYTHIC -> 3.0D; }; }
    private String rankKey(int rankIndex) { return rankIndex == 0 ? "first" : rankIndex == 1 ? "second" : "third"; }
    private int defaultTopMin(int rankIndex) { return rankIndex == 0 ? 0 : 0; }
    private int defaultTopMax(int rankIndex) { return rankIndex == 0 ? 0 : 0; }
    private long clampMoney(long value) { return Math.max(0L, Math.min(150_000L, value)); }
    private BarStyle barStyle(String raw) { try { return BarStyle.valueOf(raw.toUpperCase(Locale.ROOT)); } catch (Exception ignored) { return BarStyle.SEGMENTED_20; } }
    private String str(String path, String fallback) { return config == null || !config.contains(path) ? fallback : config.getString(path, fallback); }
    private boolean bool(String path, boolean fallback) { return config == null || !config.contains(path) ? fallback : config.getBoolean(path, fallback); }
    private int integer(String path, int fallback) { return config == null || !config.contains(path) ? fallback : config.getInt(path, fallback); }
    private double number(String path, double fallback) { return config == null || !config.contains(path) ? fallback : config.getDouble(path, fallback); }
    private double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }
}
