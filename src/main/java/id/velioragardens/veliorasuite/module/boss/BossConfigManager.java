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
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
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
        migrateBalanceV3(file);
        migrateStabilityV4(file);
        migrateRuntimeV5(file);
        migrateTeamRewardsV6(file);
        migrateBossBarV3(file);
        migrateArenaV4(file);
        migrateNotificationDefaults(file);
        migrateHourlyScheduleV2(file);
        loadRarityChance();
        loadBosses();
    }

    /**
     * Restores the intended WIB clock schedule on existing servers:
     * 19:29 -> 20:00 -> 21:00 -> 22:00, instead of one fixed daily time.
     */
    private void migrateHourlyScheduleV2(File file) {
        if (config.getInt("settings.spawn.schedule-version", 0) >= 2) return;
        config.set("settings.spawn.daily-schedule.enabled", false);
        config.set("settings.spawn.realtime-hourly.enabled", true);
        config.set("settings.spawn.interval-minutes", 60);
        config.set("settings.spawn.schedule-version", 2);
        try {
            config.save(file);
            plugin.getLogger().info("VelioraBoss: jadwal WIB per pergantian jam diterapkan.");
        } catch (IOException exception) {
            plugin.getLogger().warning("VelioraBoss: gagal menyimpan migrasi jadwal: " + exception.getMessage());
        }
    }

    /** Applies the approved boss rebalance once while preserving schedule, arena, and custom messages. */
    private void migrateBalanceV3(File file) {
        if (config.getInt("settings.balance-version", 0) >= 3) return;
        try (InputStream input = plugin.getResource("modules/boss.yml")) {
            if (input == null) return;
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(input, StandardCharsets.UTF_8));
            for (String path : List.of("settings.combat", "bosses", "skills", "rewards")) {
                copySection(defaults, path, !path.equals("bosses"));
            }
            config.set("settings.balance-version", 3);
            config.save(file);
            plugin.getLogger().info("VelioraBoss: balance config v3 diterapkan (HP maksimal 30K, Mace, collision, dan knockback skill).");
        } catch (IOException exception) {
            plugin.getLogger().warning("VelioraBoss: gagal menerapkan balance config v3: " + exception.getMessage());
        }
    }

    /** Applies the Progress 1 health/despawn values without replacing custom bosses or rewards. */
    private void migrateStabilityV4(File file) {
        if (config.getInt("settings.balance-version", 0) >= 4) return;
        config.set("settings.spawn.despawn-minutes", 30);
        config.set("settings.combat.global-health-multiplier", 0.80D);
        config.set("settings.balance-version", 4);
        try {
            config.save(file);
            plugin.getLogger().info("VelioraBoss: stabilitas v4 diterapkan (despawn 30 menit dan HP -20%).");
        } catch (IOException exception) {
            plugin.getLogger().warning("VelioraBoss: gagal menerapkan stabilitas v4: " + exception.getMessage());
        }
    }

    /** Keeps existing custom bosses/rewards while applying the runtime safety fix. */
    private void migrateRuntimeV5(File file) {
        if (config.getInt("settings.balance-version", 0) >= 5) return;
        config.set("settings.boss-size.min", 3.0D);
        config.set("settings.boss-size.max", 6.0D);
        config.set("settings.combat.outgoing-damage-multiplier", 0.85D);
        config.set("settings.combat.anti-reach-enabled", false);
        config.set("settings.balance-version", 5);
        try {
            config.save(file);
            plugin.getLogger().info("VelioraBoss: runtime v5 diterapkan (cleanup pasti, ukuran 3-6, damage -15%, hit normal).");
        } catch (IOException exception) {
            plugin.getLogger().warning("VelioraBoss: gagal menyimpan runtime v5: " + exception.getMessage());
        }
    }

    /** Makes team boss rewards fair and useful on existing installations. */
    private void migrateTeamRewardsV6(File file) {
        if (config.getInt("settings.reward-version", 0) >= 6) return;
        config.set("rewards.min-damage-to-reward", 1.0D);
        config.set("rewards.min-damage-contribution-percent", 1.0D);
        config.set("rewards.team-bonus.enabled", true);
        config.set("rewards.team-bonus.minimum-eligible-members", 2);
        config.set("rewards.team-bonus.per-member.min", 18_000);
        config.set("rewards.team-bonus.per-member.max", 22_000);
        config.set("rewards.money-total-cap.enabled", true);
        config.set("rewards.money-total-cap.max-per-player", 22_000);
        config.set("settings.reward-version", 6);
        try {
            config.save(file);
            plugin.getLogger().info("VelioraBoss: reward team v6 diterapkan (bagian setara sekitar 20K per kontributor).");
        } catch (IOException exception) {
            plugin.getLogger().warning("VelioraBoss: gagal menyimpan migrasi reward team v6.");
        }
    }

    private void copySection(FileConfiguration source, String path, boolean clearExisting) {
        ConfigurationSection section = source.getConfigurationSection(path);
        if (section == null) return;
        if (clearExisting) config.set(path, null);
        for (Map.Entry<String, Object> entry : section.getValues(true).entrySet()) {
            if (!(entry.getValue() instanceof ConfigurationSection)) {
                config.set(path + "." + entry.getKey(), entry.getValue());
            }
        }
    }

    /** Upgrades the old BossBar display once, so existing servers do not retain stale bossbar settings. */
    private void migrateBossBarV3(File file) {
        if (config.getInt("settings.bossbar-version", 0) >= 3) return;
        try (InputStream input = plugin.getResource("modules/boss.yml")) {
            if (input == null) return;
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(input, StandardCharsets.UTF_8));
            copySection(defaults, "bossbar", true);
            config.set("bossbar-radius", defaults.getDouble("bossbar-radius", 120.0D));
            config.set("settings.bossbar-version", 3);
            config.save(file);
            plugin.getLogger().info("VelioraBoss: BossBar config v3 diterapkan.");
        } catch (IOException exception) {
            plugin.getLogger().warning("VelioraBoss: gagal menerapkan BossBar config v3: " + exception.getMessage());
        }
    }

    /** Adds new optimization options to existing server configs without replacing user settings. */
    private void migrateNotificationDefaults(File file) {
        boolean changed = false;
        changed |= addDefault("settings.spawn.skip-when-no-players", true);
        changed |= addDefault("settings.notifications.players", true);
        changed |= addDefault("settings.notifications.console", false);
        changed |= addDefault("effects.skill-visuals.enabled", true);
        if (!changed) return;
        try {
            config.save(file);
        } catch (IOException exception) {
            plugin.getLogger().warning("VelioraBoss: gagal menambahkan pengaturan notifikasi baru ke boss.yml.");
        }
    }

    /** World Boss uses a 120x120 arena: radius 60 from its spawn centre. */
    private void migrateArenaV4(File file) {
        if (config.getInt("settings.arena-version", 0) >= 4) return;
        config.set("arena.radius", 60.0D);
        config.set("arena.target-radius", 60.0D);
        config.set("arena.teleport-back-distance", 70.0D);
        config.set("targeting.target-radius-horizontal", 70.0D);
        config.set("bossbar-radius", 75.0D);
        config.set("settings.arena-version", 4);
        try {
            config.save(file);
            plugin.getLogger().info("VelioraBoss: arena 120x120 diterapkan.");
        } catch (IOException exception) {
            plugin.getLogger().warning("VelioraBoss: gagal menerapkan arena 120x120.");
        }
    }

    private boolean addDefault(String path, Object value) {
        if (config.contains(path)) return false;
        config.set(path, value);
        return true;
    }

    public boolean isEnabled() { return bool("settings.enabled", true); }
    public String prefix() { return str("messages.prefix", "&8[&cVelioraBoss&8] "); }
    public boolean isSpawnEnabled() { return bool("settings.spawn.enabled", true); }
    public boolean dailyScheduleEnabled() { return bool("settings.spawn.daily-schedule.enabled", false); }
    public List<String> dailySpawnTimes() {
        List<String> times = config == null ? List.of() : config.getStringList("settings.spawn.daily-schedule.times");
        return times.isEmpty() ? List.of("20:00") : times;
    }
    public boolean realtimeHourlySpawnEnabled() { return !dailyScheduleEnabled() && bool("settings.spawn.realtime-hourly.enabled", true); }
    public int intervalMinutes() { return realtimeHourlySpawnEnabled() ? 60 : Math.max(1, integer("settings.spawn.interval-minutes", 60)); }
    public int spawnRetryMinutes() { return Math.max(1, integer("settings.spawn.retry-minutes", 5)); }
    public int despawnMinutes() { return Math.max(1, integer("settings.spawn.despawn-minutes", 25)); }
    public boolean announceSpawn() { return bool("settings.spawn.announce-spawn", true); }
    public boolean announceDeath() { return bool("settings.spawn.announce-death", true); }
    public boolean announceDespawn() { return bool("settings.spawn.announce-despawn", true); }
    public boolean skipSpawnWhenNoPlayers() { return bool("settings.spawn.skip-when-no-players", true); }
    public boolean playerNotificationsEnabled() { return bool("settings.notifications.players", true); }
    public boolean consoleNotificationsEnabled() { return bool("settings.notifications.console", false); }
    public boolean allowMultiple() { return bool("settings.spawn.allow-multiple-active", false); }
    public boolean requireSpawnPoint() { return bool("settings.spawn.require-spawn-point", true); }
    public List<Integer> warningTimesMinutes() { List<Integer> list = config == null ? List.of() : config.getIntegerList("settings.spawn.warning-times-minutes"); return list.isEmpty() ? List.of(5, 1) : list; }
    public boolean randomScaleEnabled() { return bool("settings.boss-size.random-enabled", true); }
    public double randomScaleMin() { return Math.max(0.0625D, number("settings.boss-size.min", 4.0D)); }
    public double randomScaleMax() { return Math.max(randomScaleMin(), number("settings.boss-size.max", 7.0D)); }
    public double bossArmor() { return Math.max(0.0D, number("settings.combat.armor", 18.0D)); }
    public double bossArmorToughness() { return Math.max(0.0D, number("settings.combat.armor-toughness", 12.0D)); }
    public double bossKnockbackResistance() { return clamp(number("settings.combat.knockback-resistance", 0.85D), 0.0D, 1.0D); }
    public boolean healthScalingEnabled() { return bool("settings.combat.health-scale-by-nearby-players", true); }
    public double globalHealthMultiplier() { return clamp(number("settings.combat.global-health-multiplier", 0.80D), 0.05D, 10.0D); }
    public double healthPerPlayerMultiplier() { return Math.max(0.0D, number("settings.combat.health-per-player-multiplier", 0.20D)); }
    public double maxHealthMultiplier() { return Math.max(1.0D, number("settings.combat.max-health-multiplier", 2.75D)); }
    public boolean bossCollisionEnabled() { return bool("settings.combat.collision-enabled", false); }
    public double maximumBossHealth() { return Math.max(1.0D, number("settings.combat.maximum-health", 30_000.0D)); }
    public double maceDamageMultiplier() { return clamp(number("settings.combat.mace-damage-multiplier", 1.0D), 0.05D, 1.0D); }
    public double maceMaxDamagePerHit() { return Math.max(1.0D, number("settings.combat.mace-max-damage-per-hit", 450.0D)); }
    public int maceSmashCharges() { return Math.max(1, integer("settings.combat.mace-smash-charges", 3)); }
    public int maceSmashCooldownSeconds() { return Math.max(60, integer("settings.combat.mace-smash-cooldown-seconds", 300)); }
    public double bossMeleeReach() { return Math.max(3.0D, number("settings.combat.melee-reach-limit", 6.5D)); }
    public boolean bossAntiReachEnabled() { return bool("settings.combat.anti-reach-enabled", false); }
    public double outgoingDamageMultiplier() { return clamp(number("settings.combat.outgoing-damage-multiplier", 0.85D), 0.05D, 1.0D); }
    public int invalidReachLimit() { return Math.max(3, integer("settings.combat.invalid-reach-limit", 8)); }
    public int invalidReachWindowSeconds() { return Math.max(5, integer("settings.combat.invalid-reach-window-seconds", 30)); }
    public int invalidReachDamageLockSeconds() { return Math.max(10, integer("settings.combat.invalid-reach-damage-lock-seconds", 120)); }
    public double virtualDamageMultiplier() { return clamp(number("settings.combat.virtual-damage-multiplier", 1.0D), 0.01D, 1.0D); }
    public boolean spawnTitleEnabled() { return bool("effects.spawn.title-enabled", true); }
    public String spawnTitle() { return str("effects.spawn.title", "&c%boss%"); }
    public String spawnSubtitle() { return str("effects.spawn.subtitle", "&7Boss %rarity% muncul di &f%world%"); }
    public int spawnParticleCount() { return Math.max(0, integer("effects.spawn.particle-count", 120)); }
    public boolean bossBarEnabled() { return bool("bossbar.enabled", true); }
    public String bossBarTitle() { return str("bossbar.title", "&c%boss% &7- &f%health%&7/&f%max_health% HP &8| &e%time%"); }
    public boolean colorBossBarByRarity() { return bool("bossbar.color-by-rarity", true); }
    public BarStyle bossBarStyle() { return barStyle(str("bossbar.style", "SEGMENTED_20")); }
    public double bossBarRadius() { return Math.max(20.0D, number("bossbar-radius", 75.0D)); }
    public boolean skillsEnabled() { return bool("skills.enabled", true); }
    public int skillCooldownSeconds() { return Math.max(3, integer("skills.cooldown-seconds", 12)); }
    public int skillTelegraphTicks() { return Math.max(10, integer("skills.telegraph-ticks", 26)); }
    public boolean skillVisualsEnabled() { return bool("effects.skill-visuals.enabled", true); }
    public double groundSlamDamage() { return Math.max(0.0D, number("skills.damage.ground-slam", 6.0D)); }
    public double fireBombDamage() { return Math.max(0.0D, number("skills.damage.fire-bomb", 7.0D)); }
    public double lightningChainDamage() { return Math.max(0.0D, number("skills.damage.lightning-chain", 5.0D)); }
    public double lightningChainRadius() { return Math.max(2.0D, number("skills.lightning-radius", 8.0D)); }
    public double shadowPulseDamage() { return Math.max(0.0D, number("skills.damage.shadow-pulse", 5.0D)); }
    public double soulCageDamage() { return Math.max(0.0D, number("skills.damage.soul-cage", 4.0D)); }
    public double frostNovaDamage() { return Math.max(0.0D, number("skills.damage.frost-nova", 4.0D)); }
    public double arcaneBarrageDamage() { return Math.max(0.0D, number("skills.damage.arcane-barrage", 4.5D)); }
    public double vineSnareDamage() { return Math.max(0.0D, number("skills.damage.vine-snare", 3.0D)); }
    public double meteorDamage() { return Math.max(0.0D, number("skills.damage.meteor-shower", 5.0D)); }
    public double sonicBurstDamage() { return Math.max(0.0D, number("skills.damage.sonic-burst", 4.0D)); }
    public double bloodMarkDamage() { return Math.max(0.0D, number("skills.damage.blood-mark", 4.0D)); }
    public double frostNovaRadius() { return Math.max(2.0D, number("skills.frost-nova-radius", 6.0D)); }
    public double vineSnareRadius() { return Math.max(2.0D, number("skills.vine-snare-radius", 7.0D)); }
    public double sonicBurstRadius() { return Math.max(2.0D, number("skills.sonic-burst-radius", 8.0D)); }
    public int meteorCount() { return Math.max(1, Math.min(4, integer("skills.meteor-count", 3))); }
    public double groundSlamKnockback() { return clamp(number("skills.movement.ground-slam-knockback", 0.45D), 0.0D, 1.5D); }
    public double groundSlamUpward() { return clamp(number("skills.movement.ground-slam-upward", 0.18D), 0.0D, 0.75D); }
    public double pullAuraStrength() { return clamp(number("skills.movement.pull-aura-strength", 0.25D), 0.0D, 1.0D); }
    public double pullAuraUpward() { return clamp(number("skills.movement.pull-aura-upward", 0.08D), 0.0D, 0.5D); }
    public double shadowPulsePullStrength() { return clamp(number("skills.movement.shadow-pulse-pull-strength", 0.18D), 0.0D, 1.0D); }
    public double rageThreshold() { return clamp(number("skills.rage.health-threshold", 0.30D), 0.05D, 0.90D); }
    public double rageDamageMultiplier() { return Math.max(1.0D, number("skills.rage.damage-multiplier", 1.18D)); }
    public double rageHealPercentPerCast() { return clamp(number("skills.rage.heal-percent-per-cast", 0.005D), 0.0D, 0.05D); }
    public double healPulsePercent() { return clamp(number("skills.heal-pulse.max-health-percent", 0.0125D), 0.0D, 0.10D); }
    public int maxMinions() { return Math.max(0, integer("skills.summon.max-minions", 8)); }
    public int minionsPerCast() { return Math.max(1, integer("skills.summon.minions-per-cast", 3)); }
    public List<String> minionTypes() { List<String> list = config == null ? List.of() : config.getStringList("skills.summon.types"); return list.isEmpty() ? List.of("ZOMBIE", "HUSK", "DROWNED", "ZOMBIFIED_PIGLIN") : list; }
    public List<BossSkillType> defaultSkills() { return parseSkills(config == null ? List.of() : config.getStringList("skills.default")); }
    public boolean arenaEnabled() { return bool("arena.enabled", true); }
    public double arenaRadius() { return Math.max(20.0D, number("arena.radius", 60.0D)); }
    public double targetRadius() { return Math.max(20.0D, number("arena.target-radius", 60.0D)); }
    public boolean leashToSpawn() { return bool("arena.leash-to-spawn", true); }
    public boolean teleportBackIfFar() { return bool("arena.teleport-back-if-far", true); }
    public double teleportBackDistance() { return Math.max(arenaRadius() + 5.0D, number("arena.teleport-back-distance", 70.0D)); }
    public boolean teleportBackIfBelowSpawnY() { return bool("arena.teleport-back-if-below-spawn-y", true); }
    public double belowYOffset() { return Math.max(28.0D, number("arena.below-y-offset", 28.0D)); }
    public boolean removeMinionOutsideRadius() { return bool("arena.remove-minion-outside-radius", true); }
    public double minionScanRadius() { return arenaRadius() + 10.0D; }
    public boolean targetingEnabled() { return bool("targeting.enabled", true); }
    public boolean targetingIncludeSurvival() { return bool("targeting.include-survival", true); }
    public boolean targetingIncludeAdventure() { return bool("targeting.include-adventure", true); }
    public boolean targetingIncludeCreative() { return bool("targeting.include-creative", false); }
    public boolean targetingIncludeSpectator() { return bool("targeting.include-spectator", false); }
    public double targetingRadiusHorizontal() { return Math.max(20.0D, number("targeting.target-radius-horizontal", 70.0D)); }
    public double targetingRadiusVertical() { return Math.max(72.0D, number("targeting.target-radius-vertical", 72.0D)); }
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
    public boolean teamBonusEnabled() { return bool("rewards.team-bonus.enabled", true); }
    public int teamBonusMinimumMembers() { return Math.max(2, integer("rewards.team-bonus.minimum-eligible-members", 2)); }
    public long teamBonusPoolMin() { return clampMoney(integer("rewards.team-bonus.pool.min", 1_500)); }
    public long teamBonusPoolMax() { return Math.max(teamBonusPoolMin(), clampMoney(integer("rewards.team-bonus.pool.max", 3_000))); }
    public long teamRewardPerMemberMin() { return clampMoney(integer("rewards.team-bonus.per-member.min", 18_000)); }
    public long teamRewardPerMemberMax() { return Math.max(teamRewardPerMemberMin(), clampMoney(integer("rewards.team-bonus.per-member.max", 22_000))); }
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
            List<BossSkillType> skills = enhanceSkills(id, rarity, parseSkills(boss.getStringList("skills")));
            double damage = Math.max(minimumDamage(rarity), boss.getDouble("damage", minimumDamage(rarity)));
            double health = Math.min(maximumBossHealth(), Math.max(1.0D, boss.getDouble("health", 5000.0D)));
            bosses.put(id.toLowerCase(Locale.ROOT), new BossDefinition(id.toLowerCase(Locale.ROOT), type, boss.getString("display-name", id), rarity, health, damage, Math.max(0.0625D, boss.getDouble("scale", 4.0D)), skills.isEmpty() ? defaultSkills() : skills));
        }
    }
    /** Every existing boss receives a small deterministic rotation of the new skills. */
    private List<BossSkillType> enhanceSkills(String id, BossRarity rarity, List<BossSkillType> source) {
        List<BossSkillType> skills = new ArrayList<>(source);
        if (!bool("skills.auto-enhance-bosses", true)) return skills;
        List<BossSkillType> additions = List.of(BossSkillType.FROST_NOVA, BossSkillType.ARCANE_BARRAGE,
                BossSkillType.VINE_SNARE, BossSkillType.METEOR_SHOWER, BossSkillType.SONIC_BURST, BossSkillType.BLOOD_MARK);
        int start = Math.floorMod(id.toLowerCase(Locale.ROOT).hashCode(), additions.size());
        BossSkillType first = additions.get(start);
        if (!skills.contains(first)) skills.add(first);
        if (rarity == BossRarity.EPIC || rarity == BossRarity.LEGENDARY || rarity == BossRarity.MYTHIC) {
            BossSkillType second = additions.get((start + 1) % additions.size());
            if (!skills.contains(second)) skills.add(second);
        }
        return skills;
    }
    private List<BossSkillType> parseSkills(List<String> raw) { List<BossSkillType> skills = new ArrayList<>(); for (String value : raw) { BossSkillType skill = BossSkillType.from(value); if (skill != null && !skills.contains(skill)) skills.add(skill); } if (skills.isEmpty()) skills.addAll(List.of(BossSkillType.GROUND_SLAM, BossSkillType.SUMMON_MINIONS, BossSkillType.FIRE_BOMB, BossSkillType.PULL_AURA, BossSkillType.POISON_CLOUD, BossSkillType.RAGE_MODE)); return skills; }
    private boolean isAllowedBossType(EntityType type) { return switch (type) { case WARDEN, RAVAGER, ZOMBIE, HUSK, DROWNED, PIGLIN_BRUTE, WITHER_SKELETON, VINDICATOR, EVOKER -> true; default -> false; }; }
    private double defaultChance(BossRarity rarity) { return switch (rarity) { case COMMON -> 45.0D; case RARE -> 27.0D; case EPIC -> 16.0D; case LEGENDARY -> 9.0D; case MYTHIC -> 3.0D; }; }
    private double minimumDamage(BossRarity rarity) { return switch (rarity) { case COMMON -> 7.0D; case RARE -> 8.0D; case EPIC -> 9.0D; case LEGENDARY -> 10.0D; case MYTHIC -> 11.0D; }; }
    private String rankKey(int rankIndex) { return rankIndex == 0 ? "first" : rankIndex == 1 ? "second" : "third"; }
    private int defaultTopMin(int rankIndex) { return rankIndex == 0 ? 0 : 0; }
    private int defaultTopMax(int rankIndex) { return rankIndex == 0 ? 0 : 0; }
    private long clampMoney(long value) { return Math.max(0L, value); }
    private BarStyle barStyle(String raw) { try { return BarStyle.valueOf(raw.toUpperCase(Locale.ROOT)); } catch (Exception ignored) { return BarStyle.SEGMENTED_20; } }
    private String str(String path, String fallback) { return config == null || !config.contains(path) ? fallback : config.getString(path, fallback); }
    private boolean bool(String path, boolean fallback) { return config == null || !config.contains(path) ? fallback : config.getBoolean(path, fallback); }
    private int integer(String path, int fallback) { return config == null || !config.contains(path) ? fallback : config.getInt(path, fallback); }
    private double number(String path, double fallback) { return config == null || !config.contains(path) ? fallback : config.getDouble(path, fallback); }
    private double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }
}
