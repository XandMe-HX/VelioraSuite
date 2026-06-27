package id.velioragardens.veliorasuite.module.pets;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.module.pets.model.PetDefinition;
import id.velioragardens.veliorasuite.module.pets.model.PetRarity;
import id.velioragardens.veliorasuite.module.pets.model.PetSkillType;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;

import java.io.File;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class PetConfigManager {
    private final VelioraSuite plugin;
    private FileConfiguration config;
    private final Map<String, PetDefinition> pets = new LinkedHashMap<>();
    private final Map<PetRarity, Double> chances = new EnumMap<>(PetRarity.class);
    private final DecimalFormat moneyFormat;

    public PetConfigManager(VelioraSuite plugin) {
        this.plugin = plugin;
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        symbols.setGroupingSeparator('.');
        this.moneyFormat = new DecimalFormat("#,###", symbols);
    }

    public void load() {
        plugin.saveResourceIfNotExists("modules/pets.yml");
        config = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "modules/pets.yml"));
        loadChances();
        loadPets();
    }

    public String prefix() { return str("messages.prefix", "&8[&dVelioraPets&8] "); }
    public boolean economyEnabled() { return bool("settings.economy.enabled", true); }
    public long gachaPrice() { return Math.max(0L, config.getLong("settings.economy.gacha-price", 100000L)); }
    public int maxLevel() { return Math.max(1, integer("settings.max-level", 50)); }
    public int duplicateExp() { return Math.max(0, integer("settings.duplicate-exp", 50)); }
    public boolean autoSummonNewPet() { return bool("settings.auto-summon-new-pet", false); }
    public boolean autoSummonLastPet() { return bool("settings.auto-summon-last-pet", false); }
    public int deathCooldownMinutes() { return Math.max(0, integer("settings.death-cooldown-minutes", 15)); }
    public boolean usePathfinderFollow() { return bool("settings.follow.use-pathfinder", true); }
    public boolean allowFlyingPets() { return bool("settings.allow-flying-pets", false); }
    public boolean flyingSafeMode() { return bool("settings.flying-safe-mode.enabled", true); }
    public boolean allowStorageWithoutActive() { return bool("storage.allow-storage-without-active", false); }
    public int storageSize(PetRarity rarity) { return Math.max(9, integer("storage.size." + rarity.name().toLowerCase(Locale.ROOT), defaultStorage(rarity))); }

    public boolean ridingEnabled() { return bool("riding.enabled", true); }
    public boolean ridingRequireAdult() { return bool("riding.require-adult", true); }
    public int defaultAdultLevel() { return Math.max(1, integer("riding.default-adult-level", 10)); }
    public boolean allowRideActiveOnly() { return bool("riding.allow-ride-active-only", true); }
    public boolean dismountOnDismiss() { return bool("riding.dismount-on-dismiss", true); }
    public double rideSpeed() { return Math.max(0.05D, number("riding.speed", 0.32D)); }
    public double rideJumpY() { return Math.max(0.0D, number("riding.jump-y", 0.42D)); }
    public boolean rideableRarity(PetRarity rarity) { return bool("riding.rideable-rarities." + rarity.name().toLowerCase(Locale.ROOT), true); }

    public boolean silentPets() { return bool("sounds.silent-pets", true); }
    public boolean playCustomSummonSound() { return bool("sounds.play-custom-summon-sound", true); }
    public Sound summonSound() { return sound("sounds.summon-sound", "ENTITY_EXPERIENCE_ORB_PICKUP"); }
    public Sound feedSound() { return sound("sounds.feed-sound", "ENTITY_GENERIC_EAT"); }
    public float soundVolume() { return (float) Math.max(0.0D, number("sounds.volume", 0.25D)); }
    public float soundPitch() { return (float) Math.max(0.1D, number("sounds.pitch", 1.2D)); }

    public double scalePerLevel() { return Math.max(0.0D, number("leveling.scale-per-level", 0.003D)); }
    public double maxScaleBonus() { return Math.max(0.0D, number("leveling.max-scale-bonus", 0.15D)); }

    public boolean combatEnabled() { return bool("combat.enabled", true); }
    public double attackRange() { return Math.max(1.0D, number("combat.attack-range", 3.0D)); }
    public int attackCooldownSeconds() { return Math.max(1, integer("combat.attack-cooldown-seconds", 2)); }
    public boolean allowAttackPassive() { return bool("combat.allow-attack-passive", false); }
    public boolean allowAttackPlayers() { return bool("combat.allow-attack-players", false); }
    public double petDamageMultiplier() { return Math.max(0.1D, number("combat.pet-damage-multiplier", 1.0D)); }

    public boolean auraEnabled() { return bool("cosmetic.aura-enabled", true); }
    public int auraIntervalSeconds() { return Math.max(3, integer("cosmetic.aura-interval-seconds", 5)); }
    public boolean glowEnabled() { return bool("cosmetic.glow-enabled", false); }
    public boolean lowLagParticles() { return bool("cosmetic.particles-low-lag", true); }
    public Particle auraParticle() { return particle("cosmetic.aura-particle", "HAPPY_VILLAGER"); }

    public Map<String, PetDefinition> pets() { return pets; }
    public Map<PetRarity, Double> chances() { return chances; }
    public String message(String key, String fallback) { return str("messages." + key, fallback).replace("%prefix%", prefix()); }
    public String color(String text) { return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text); }
    public String plain(String text) { return ChatColor.stripColor(color(text == null ? "" : text)); }
    public String formatMoney(long amount) { return moneyFormat.format(Math.max(0L, amount)); }
    public Sound sound(String path, String fallback) { try { return Sound.valueOf(str(path, fallback).toUpperCase(Locale.ROOT)); } catch (Exception ignored) { return Sound.UI_BUTTON_CLICK; } }

    private void loadChances() {
        chances.clear();
        for (PetRarity rarity : PetRarity.values()) chances.put(rarity, Math.max(0.0D, number("gacha.chance." + rarity.name().toLowerCase(Locale.ROOT), defaultChance(rarity))));
    }

    private void loadPets() {
        pets.clear();
        ConfigurationSection section = config.getConfigurationSection("pets");
        if (section != null) {
            for (String id : section.getKeys(false)) loadConfiguredPet(id);
        }
        addBuiltinPhase12EPets();
    }

    private void loadConfiguredPet(String id) {
        String normalizedId = id.toLowerCase(Locale.ROOT);
        String path = "pets." + id;
        String rawEntity = config.getString(path + ".entity", "");
        if (isBlacklistedPet(normalizedId, rawEntity)) {
            plugin.getLogger().warning("VelioraPets: skip blacklisted pet: " + id + " / " + rawEntity);
            return;
        }
        EntityType type = entityType(rawEntity);
        if (type == null) {
            plugin.getLogger().warning("VelioraPets: skip invalid or unavailable entity for pet " + id + ": " + rawEntity);
            return;
        }
        boolean flyingPet = isFlyingPet(type);
        if (flyingPet && !allowFlyingPets()) {
            plugin.getLogger().warning("VelioraPets: skip flying pet karena settings.allow-flying-pets false: " + type + " for " + id);
            return;
        }
        if (flyingPet && !flyingSafeMode()) {
            plugin.getLogger().warning("VelioraPets: skip flying pet entity karena flying-safe-mode false: " + type + " for " + id);
            return;
        }
        PetRarity rarity = PetRarity.from(config.getString(path + ".rarity", "COMMON"));
        PetSkillType skillType = PetSkillType.from(config.getString(path + ".skill.type", "NONE"));
        Material food = material(config.getString(path + ".food.material", defaultFood(rarity).name()), defaultFood(rarity));
        boolean aquatic = config.getBoolean(path + ".aquatic", defaultAquatic(type));
        double defaultDamage = aquatic ? 0.0D : defaultDamage(rarity);
        pets.put(normalizedId, new PetDefinition(
                normalizedId,
                config.getString(path + ".display-name", id),
                type,
                material(config.getString(path + ".icon", "BONE"), Material.BONE),
                rarity,
                skillType,
                Math.min(maxSkillBonus(skillType), Math.max(0.0D, config.getDouble(path + ".skill.bonus", 0.0D))),
                Math.max(0.0D, config.getDouble(path + ".damage", defaultDamage)),
                Math.max(0.1D, config.getDouble(path + ".scale", 0.5D)),
                Math.max(0L, config.getLong(path + ".price", defaultPrice(rarity))),
                storageSize(rarity),
                food,
                Math.max(1, config.getInt(path + ".food.exp", defaultFeedExp(rarity))),
                flyingPet,
                config.getBoolean(path + ".rideable", defaultRideable(type)),
                Math.max(1, config.getInt(path + ".adult-level", defaultAdultLevel())),
                aquatic
        ));
    }

    private void addBuiltinPhase12EPets() {
        addBuiltin("ocelot", "&eOcelot", "OCELOT", "COD", PetRarity.COMMON, 1.0D, 0.55D, 130000L, false, 10, "COD", 20, PetSkillType.NONE, 0.0D, false);
        addBuiltin("polar_bear", "&bPolar Bear", "POLAR_BEAR", "SNOW_BLOCK", PetRarity.RARE, 1.5D, 0.65D, 280000L, true, 10, "COD", 25, PetSkillType.PET_DAMAGE, 0.01D, false);
        addBuiltin("dolphin", "&bDolphin", "DOLPHIN", "COD", PetRarity.RARE, 0.0D, 0.55D, 220000L, false, 10, "COD", 25, PetSkillType.NONE, 0.0D, true);
        addBuiltin("cod", "&fCod", "COD", "COD", PetRarity.COMMON, 0.0D, 0.45D, 100000L, false, 10, "KELP", 20, PetSkillType.NONE, 0.0D, true);
        addBuiltin("salmon", "&fSalmon", "SALMON", "SALMON", PetRarity.COMMON, 0.0D, 0.45D, 100000L, false, 10, "KELP", 20, PetSkillType.NONE, 0.0D, true);
        addBuiltin("tropical_fish", "&fTropical Fish", "TROPICAL_FISH", "TROPICAL_FISH", PetRarity.COMMON, 0.0D, 0.45D, 120000L, false, 10, "KELP", 20, PetSkillType.NONE, 0.0D, true);
        addBuiltin("pufferfish", "&ePufferfish", "PUFFERFISH", "PUFFERFISH", PetRarity.RARE, 0.0D, 0.45D, 180000L, false, 10, "KELP", 25, PetSkillType.NONE, 0.0D, true);
        addBuiltin("tadpole", "&fTadpole", "TADPOLE", "SLIME_BALL", PetRarity.COMMON, 0.0D, 0.35D, 100000L, false, 10, "KELP", 20, PetSkillType.NONE, 0.0D, true);
        addBuiltin("elder_guardian", "&6Elder Guardian", "ELDER_GUARDIAN", "PRISMARINE_CRYSTALS", PetRarity.LEGENDARY, 0.0D, 0.40D, 750000L, false, 15, "PRISMARINE_SHARD", 45, PetSkillType.FISHING_LUCK, 0.02D, true);
        addBuiltin("silverfish", "&fSilverfish", "SILVERFISH", "STONE", PetRarity.COMMON, 1.0D, 0.45D, 120000L, false, 10, "STONE", 20, PetSkillType.NONE, 0.0D, false);
        addBuiltin("endermite", "&bEndermite", "ENDERMITE", "ENDER_PEARL", PetRarity.RARE, 1.5D, 0.45D, 220000L, false, 10, "CHORUS_FRUIT", 25, PetSkillType.PET_DAMAGE, 0.01D, false);
        addBuiltin("shulker", "&dShulker", "SHULKER", "SHULKER_SHELL", PetRarity.EPIC, 2.0D, 0.45D, 450000L, false, 10, "SHULKER_SHELL", 35, PetSkillType.PET_DAMAGE, 0.02D, false);
        addBuiltin("witch", "&dWitch", "WITCH", "POTION", PetRarity.EPIC, 2.0D, 0.55D, 450000L, false, 10, "GLASS_BOTTLE", 35, PetSkillType.PET_DAMAGE, 0.02D, false);
        addBuiltin("wither_skeleton", "&dWither Skeleton", "WITHER_SKELETON", "WITHER_SKELETON_SKULL", PetRarity.EPIC, 2.0D, 0.55D, 480000L, false, 10, "BONE", 35, PetSkillType.PET_DAMAGE, 0.02D, false);
        addBuiltin("zombified_piglin", "&bZombified Piglin", "ZOMBIFIED_PIGLIN", "GOLD_NUGGET", PetRarity.RARE, 1.5D, 0.55D, 250000L, false, 10, "GOLD_NUGGET", 25, PetSkillType.PET_DAMAGE, 0.01D, false);
        addBuiltin("villager", "&fVillager", "VILLAGER", "EMERALD", PetRarity.COMMON, 1.0D, 0.55D, 150000L, false, 10, "BREAD", 20, PetSkillType.NONE, 0.0D, false);
        addBuiltin("wandering_trader", "&bWandering Trader", "WANDERING_TRADER", "EMERALD", PetRarity.RARE, 1.5D, 0.55D, 280000L, false, 10, "BREAD", 25, PetSkillType.QUEST_MONEY, 0.01D, false);
        addBuiltin("strider", "&bStrider", "STRIDER", "WARPED_FUNGUS_ON_A_STICK", PetRarity.RARE, 1.5D, 0.55D, 280000L, true, 10, "WARPED_FUNGUS", 25, PetSkillType.PET_DAMAGE, 0.01D, false);
        addBuiltin("skeleton_horse", "&dSkeleton Horse", "SKELETON_HORSE", "BONE", PetRarity.EPIC, 2.0D, 0.60D, 480000L, true, 10, "BONE", 35, PetSkillType.PET_DAMAGE, 0.02D, false);
        addBuiltin("zombie_horse", "&dZombie Horse", "ZOMBIE_HORSE", "ROTTEN_FLESH", PetRarity.EPIC, 2.0D, 0.60D, 480000L, true, 10, "ROTTEN_FLESH", 35, PetSkillType.PET_DAMAGE, 0.02D, false);
        addBuiltin("creaking", "&6Creaking", "CREAKING", "RESIN_CLUMP", PetRarity.LEGENDARY, 2.5D, 0.50D, 750000L, false, 15, "RESIN_CLUMP", 45, PetSkillType.PET_DAMAGE, 0.03D, false);
        addBuiltin("illusioner", "&6Illusioner", "ILLUSIONER", "BOW", PetRarity.LEGENDARY, 2.5D, 0.55D, 750000L, false, 15, "BREAD", 45, PetSkillType.PET_DAMAGE, 0.03D, false);
        addBuiltin("giant", "&5Giant", "GIANT", "ZOMBIE_HEAD", PetRarity.MYTHIC, 3.0D, 0.18D, 1000000L, true, 20, "ROTTEN_FLESH", 60, PetSkillType.PET_DAMAGE, 0.05D, false);
    }

    private void addBuiltin(String id, String display, String entity, String icon, PetRarity rarity, double damage, double scale, long price, boolean rideable, int adultLevel, String food, int feedExp, PetSkillType skill, double skillBonus, boolean aquatic) {
        String normalizedId = id.toLowerCase(Locale.ROOT);
        if (pets.containsKey(normalizedId)) return;
        if (isBlacklistedPet(normalizedId, entity)) return;
        EntityType type = entityType(entity);
        if (type == null) {
            plugin.getLogger().warning("VelioraPets: skip builtin pet, EntityType tidak tersedia: " + entity + " for " + id);
            return;
        }
        boolean flying = isFlyingPet(type);
        if (flying && !allowFlyingPets()) return;
        pets.put(normalizedId, new PetDefinition(normalizedId, display, type, material(icon, Material.BONE), rarity, skill, Math.min(maxSkillBonus(skill), Math.max(0.0D, skillBonus)), Math.max(0.0D, damage), scale, price, storageSize(rarity), material(food, defaultFood(rarity)), Math.max(1, feedExp), flying, rideable, Math.max(1, adultLevel), aquatic || defaultAquatic(type)));
    }

    private boolean isBlacklistedPet(String id, String rawEntity) {
        String raw = (rawEntity == null ? "" : rawEntity).toLowerCase(Locale.ROOT).replace("-", "_").replace(" ", "_");
        String key = (id == null ? "" : id).toLowerCase(Locale.ROOT).replace("-", "_").replace(" ", "_");
        return raw.equals("ghast") || raw.equals("happy_ghast") || raw.equals("happghast") || key.equals("happy_ghast") || key.equals("happghast") || key.equals("happyghast") || key.contains("happy_ghast") || key.contains("happghast");
    }

    private boolean isFlyingPet(EntityType type) {
        // Jangan pakai EntityType.HAPPY_GHAST di sini agar tetap aman di API yang belum punya enum itu.
        return type != null && switch (type) {
            case PHANTOM, GHAST, BLAZE, VEX, ENDER_DRAGON, WITHER, BEE, ALLAY, BAT, PARROT -> true;
            default -> false;
        };
    }

    private boolean defaultAquatic(EntityType type) {
        if (type == null) return false;
        return switch (type) {
            case DOLPHIN, COD, SALMON, TROPICAL_FISH, PUFFERFISH, TADPOLE, ELDER_GUARDIAN, GUARDIAN, AXOLOTL -> true;
            default -> false;
        };
    }

    private boolean defaultRideable(EntityType type) {
        return switch (type) {
            case COW, SHEEP, PIG, GOAT, CAMEL, HORSE, DONKEY, MULE, LLAMA, TRADER_LLAMA, PANDA, IRON_GOLEM, RAVAGER, WARDEN, HOGLIN, ZOGLIN, POLAR_BEAR, STRIDER, SKELETON_HORSE, ZOMBIE_HORSE, GIANT -> true;
            default -> false;
        };
    }

    private double maxSkillBonus(PetSkillType type) {
        return switch (type) { case QUEST_MONEY -> 0.03D; case FISHING_LUCK -> 0.02D; case PET_DAMAGE -> 0.05D; default -> 0.0D; };
    }

    private double defaultChance(PetRarity rarity) { return switch (rarity) { case COMMON -> 55.0D; case RARE -> 25.0D; case EPIC -> 12.0D; case LEGENDARY -> 6.0D; case MYTHIC -> 2.0D; }; }
    private long defaultPrice(PetRarity rarity) { return switch (rarity) { case COMMON -> 100000L; case RARE -> 200000L; case EPIC -> 400000L; case LEGENDARY -> 700000L; case MYTHIC -> 1000000L; }; }
    private double defaultDamage(PetRarity rarity) { return switch (rarity) { case COMMON -> 1.0D; case RARE -> 1.5D; case EPIC -> 2.0D; case LEGENDARY -> 2.5D; case MYTHIC -> 3.0D; }; }
    private int defaultStorage(PetRarity rarity) { return switch (rarity) { case COMMON, RARE -> 9; case EPIC, LEGENDARY -> 18; case MYTHIC -> 27; }; }
    private int defaultFeedExp(PetRarity rarity) { return switch (rarity) { case COMMON -> 20; case RARE -> 25; case EPIC -> 35; case LEGENDARY -> 45; case MYTHIC -> 60; }; }
    private Material defaultFood(PetRarity rarity) { return switch (rarity) { case COMMON -> Material.APPLE; case RARE -> Material.COOKED_CHICKEN; case EPIC -> Material.GOLDEN_CARROT; case LEGENDARY -> Material.GOLDEN_APPLE; case MYTHIC -> Material.ENCHANTED_GOLDEN_APPLE; }; }
    private Material material(String raw, Material fallback) { Material material = Material.matchMaterial(raw == null ? "" : raw.toUpperCase(Locale.ROOT)); return material == null ? fallback : material; }
    private EntityType entityType(String raw) { try { return EntityType.valueOf(raw == null ? "" : raw.toUpperCase(Locale.ROOT)); } catch (Exception ignored) { return null; } }
    private Particle particle(String path, String fallback) { try { return Particle.valueOf(str(path, fallback).toUpperCase(Locale.ROOT)); } catch (Exception ignored) { return Particle.HAPPY_VILLAGER; } }
    private String str(String path, String fallback) { return config == null || !config.contains(path) ? fallback : config.getString(path, fallback); }
    private boolean bool(String path, boolean fallback) { return config == null || !config.contains(path) ? fallback : config.getBoolean(path, fallback); }
    private int integer(String path, int fallback) { return config == null || !config.contains(path) ? fallback : config.getInt(path, fallback); }
    private double number(String path, double fallback) { return config == null || !config.contains(path) ? fallback : config.getDouble(path, fallback); }
}
