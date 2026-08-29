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
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class PetConfigManager {
    private static final Set<String> SAFE_ANIMAL_IDS = Set.of(
            "wolf", "cat", "fox", "rabbit", "panda", "axolotl",
            "chicken", "cow", "sheep", "pig", "mooshroom", "mushroom_cow", "sniffer",
            "horse", "donkey", "mule", "llama", "trader_llama", "goat", "camel", "armadillo",
            "frog", "turtle", "ocelot", "polar_bear"
    );

    private static final Set<String> SAFE_ANIMAL_ENTITY_NAMES = Set.of(
            "WOLF", "CAT", "FOX", "RABBIT", "PANDA", "AXOLOTL",
            "CHICKEN", "COW", "SHEEP", "PIG", "MOOSHROOM", "MUSHROOM_COW", "SNIFFER",
            "HORSE", "DONKEY", "MULE", "LLAMA", "TRADER_LLAMA", "GOAT", "CAMEL", "ARMADILLO",
            "FROG", "TURTLE", "OCELOT", "POLAR_BEAR"
    );

    private static final Set<String> WALKING_DISABLED_IDS = Set.of(
            "bat_sprite", "parrot", "parrot_scout", "bee_safe", "allay_wisp", "phantom_shadow_safe", "vex_lantern_safe", "blaze_core_safe", "astral_allay", "abyss_phantom_safe",
            "phantom", "ghast", "happy_ghast", "happghast", "happyghast", "bee", "allay", "bat", "blaze", "vex", "ender_dragon", "wither",
            "dolphin", "cod", "salmon", "tropical_fish", "pufferfish", "tadpole", "elder_guardian", "guardian", "squid", "glow_squid"
    );

    private static final Set<String> FLYING_ENTITY_NAMES = Set.of(
            "PHANTOM", "GHAST", "BLAZE", "VEX", "ENDER_DRAGON", "WITHER", "BEE", "ALLAY", "BAT", "PARROT"
    );

    private static final Set<String> AQUATIC_ENTITY_NAMES = Set.of(
            "DOLPHIN", "COD", "SALMON", "TROPICAL_FISH", "PUFFERFISH", "TADPOLE", "ELDER_GUARDIAN", "GUARDIAN", "AXOLOTL", "SQUID", "GLOW_SQUID"
    );

    private static final Set<String> RIDEABLE_ENTITY_NAMES = Set.of(
            "COW", "SHEEP", "PIG", "PANDA", "MOOSHROOM", "MUSHROOM_COW", "SNIFFER"
    );

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
        File file = new File(plugin.getDataFolder(), "modules/pets.yml");
        config = YamlConfiguration.loadConfiguration(file);
        mergeBundledDefaults(file);
        pruneUnsupportedPets(file);
        loadChances();
        loadPets();
    }

    public String prefix() { return str("messages.prefix", "&8[&dVelioraPets&8] "); }
    public boolean stableSafeMode() { return bool("settings.stable-safe-mode", true); }
    public boolean walkingPetsOnly() { return bool("settings.walking-pets-only", true); }
    public boolean allowAquaticPets() { return bool("settings.allow-aquatic-pets", false); }
    public boolean allowAxolotlGroundPet() { return bool("settings.allow-axolotl-ground-pet", true); }
    public boolean economyEnabled() { return bool("settings.economy.enabled", true); }
    public long gachaPrice() { return config == null ? 25_000L : Math.max(0L, config.getLong("settings.economy.gacha-price", 25_000L)); }
    public int maxLevel() { return Math.max(1, integer("settings.max-level", 50)); }
    public int duplicateExp() { return Math.max(0, integer("settings.duplicate-exp", 50)); }
    public int maxFeedAmount() { return Math.max(1, integer("feeding.max-feed-amount", 64)); }
    public boolean autoSummonNewPet() { return bool("settings.auto-summon-new-pet", false); }
    public boolean autoSummonLastPet() { return bool("settings.auto-summon-last-pet", false); }
    public int deathCooldownMinutes() { return Math.max(0, integer("settings.death-cooldown-minutes", 15)); }
    public boolean usePathfinderFollow() { return bool("settings.follow.use-pathfinder", true); }
    public boolean allowFlyingPets() { return bool("settings.allow-flying-pets", true); }
    public boolean flyingSafeMode() { return bool("settings.flying-safe-mode.enabled", true); }
    public int flyingMinimumLevel() { return Math.max(1, integer("settings.flying-pets.minimum-level", 30)); }
    public boolean isAllowedFlyingEntity(EntityType type) {
        return allowFlyingPets() && type != null
                && config.getStringList("settings.flying-pets.allowed-entities").stream()
                .anyMatch(entry -> type.name().equalsIgnoreCase(entry));
    }

    private void mergeBundledDefaults(File file) {
        try (InputStream input = plugin.getResource("modules/pets.yml")) {
            if (input == null) return;
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(input, StandardCharsets.UTF_8));
            config.setDefaults(defaults);
            config.options().copyDefaults(true);
            config.save(file);
        } catch (IOException exception) {
            plugin.getLogger().warning("VelioraPets: gagal memperbarui default pets.yml: " + exception.getMessage());
        }
    }
    public boolean allowStorageWithoutActive() { return bool("storage.allow-storage-without-active", true); }
    public int storageSize(PetRarity rarity) { return PetDataManager.SHARED_STORAGE_SIZE; }

    public boolean ridingEnabled() { return bool("riding.enabled", true); }
    public boolean ridingRequireAdult() { return bool("riding.require-adult", true); }
    public int defaultAdultLevel() { return Math.max(1, integer("riding.default-adult-level", 10)); }
    public boolean allowRideActiveOnly() { return bool("riding.allow-ride-active-only", true); }
    public boolean dismountOnDismiss() { return bool("riding.dismount-on-dismiss", true); }
    public double rideSpeed() { return Math.max(0.05D, number("riding.speed", 0.32D)); }
    public double rideJumpY() { return Math.max(0.0D, number("riding.jump-y", 0.42D)); }
    public double rideFlySpeed() { return Math.max(0.05D, number("riding.fly-speed", 0.30D)); }
    public double rideFlyVerticalSpeed() { return Math.max(0.05D, number("riding.fly-vertical-speed", 0.24D)); }
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
    public int attackCooldownSeconds() { return Math.max(1, integer("combat.attack-cooldown-seconds", 1)); }
    public boolean allowAttackPassive() { return bool("combat.allow-attack-passive", false); }
    public boolean allowAttackPlayers() { return false; }
    public double petDamageMultiplier() { return Math.max(0.0D, number("combat.pet-damage-multiplier", 1.0D)); }

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

    public boolean isSafeModeDisabledPet(String id) {
        String key = id == null ? "" : id.toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        if (SAFE_ANIMAL_IDS.contains(key)) return false;
        if (config != null) {
            EntityType configuredType = entityType(config.getString("pets." + key + ".entity", ""));
            if (isAllowedFlyingEntity(configuredType)) return false;
            if (isSafeAnimalType(configuredType)) return false;
        }
        if (walkingPetsOnly() && WALKING_DISABLED_IDS.contains(key)) return true;
        if (!stableSafeMode()) return false;
        String rawEntity = config == null ? "" : config.getString("pets." + key + ".entity", "");
        EntityType type = entityType(rawEntity);
        return type != null && isDisabledByWalkingMode(type);
    }

    private void loadChances() {
        chances.clear();
        for (PetRarity rarity : PetRarity.values()) chances.put(rarity, Math.max(0.0D, number("gacha.chance." + rarity.name().toLowerCase(Locale.ROOT), defaultChance(rarity))));
    }

    private void pruneUnsupportedPets(File file) {
        ConfigurationSection section = config.getConfigurationSection("pets");
        if (section == null) return;
        boolean changed = false;
        for (String id : Set.copyOf(section.getKeys(false))) {
            String path = "pets." + id;
            String rawEntity = config.getString(path + ".entity", "");
            EntityType type = entityType(rawEntity);
            boolean aquaticDisabled = type != null && defaultAquatic(type) && !allowAquaticPets() && !isAxolotlGround(type);
            if (isBlacklistedPet(id, rawEntity) || (type != null && !isPermittedPetType(type)) || aquaticDisabled) {
                config.set(path, null);
                changed = true;
            }
        }
        if (!changed) return;
        try {
            config.save(file);
        } catch (IOException exception) {
            plugin.getLogger().warning("VelioraPets: gagal membersihkan pet lama dari pets.yml: " + exception.getMessage());
        }
    }

    private void loadPets() {
        pets.clear();
        ConfigurationSection section = config.getConfigurationSection("pets");
        if (section != null) for (String id : section.getKeys(false)) loadConfiguredPet(id);
        addBuiltinAnimalPets();
    }

    private void loadConfiguredPet(String id) {
        String normalizedId = id.toLowerCase(Locale.ROOT);
        String path = "pets." + id;
        String rawEntity = config.getString(path + ".entity", "");
        if (isBlacklistedPet(normalizedId, rawEntity)) return;
        EntityType type = entityType(rawEntity);
        if (type == null) { plugin.getLogger().warning("VelioraPets: skip invalid or unavailable entity for pet " + id + ": " + rawEntity); return; }
        if (!isPermittedPetType(type)) return;

        boolean flyingPet = isFlyingPet(type);
        boolean aquatic = config.getBoolean(path + ".aquatic", defaultAquatic(type));
        if (isAxolotlGround(type)) aquatic = false;
        if (aquatic && !allowAquaticPets() && !isAxolotlGround(type)) return;

        PetRarity rarity = PetRarity.from(config.getString(path + ".rarity", "COMMON"));
        Material food = material(config.getString(path + ".food.material", defaultFood(rarity).name()), defaultFood(rarity));
        long price = balancedPrice(rarity, config.getLong(path + ".price", defaultPrice(rarity)));
        pets.put(normalizedId, new PetDefinition(normalizedId,
                config.getString(path + ".display-name", id),
                type,
                material(config.getString(path + ".icon", "BONE"), Material.BONE),
                rarity,
                PetSkillType.NONE,
                Math.max(0.0D, config.getDouble(path + ".damage", defaultDamage(rarity))),
                0.0D,
                Math.max(0.1D, config.getDouble(path + ".scale", 0.5D)),
                price,
                storageSize(rarity),
                food,
                Math.max(1, config.getInt(path + ".food.exp", defaultFeedExp(rarity))),
                flyingPet,
                config.getBoolean(path + ".rideable", defaultRideable(type)),
                Math.max(1, config.getInt(path + ".adult-level", defaultAdultLevel())),
                aquatic));
    }

    private void addBuiltinAnimalPets() {
        addBuiltin("wolf", "&fWolf", "WOLF", "BONE", PetRarity.COMMON, 0.55D, 25_000L, false, 10, "BONE", 20);
        addBuiltin("cat", "&eCat", "CAT", "COD", PetRarity.COMMON, 0.50D, 25_000L, false, 10, "COD", 20);
        addBuiltin("fox", "&6Fox", "FOX", "SWEET_BERRIES", PetRarity.COMMON, 0.50D, 25_000L, false, 10, "SWEET_BERRIES", 20);
        addBuiltin("rabbit", "&fRabbit", "RABBIT", "CARROT", PetRarity.COMMON, 0.42D, 25_000L, false, 10, "CARROT", 20);
        addBuiltin("panda", "&aPanda", "PANDA", "BAMBOO", PetRarity.RARE, 0.65D, 50_000L, true, 10, "BAMBOO", 25);
        addBuiltin("axolotl", "&dAxolotl", "AXOLOTL", "TROPICAL_FISH_BUCKET", PetRarity.RARE, 0.55D, 50_000L, false, 10, "TROPICAL_FISH", 25);
        addBuiltin("chicken", "&fChicken", "CHICKEN", "FEATHER", PetRarity.COMMON, 0.45D, 25_000L, false, 10, "WHEAT_SEEDS", 20);
        addBuiltin("cow", "&fCow", "COW", "WHEAT", PetRarity.COMMON, 0.60D, 25_000L, true, 10, "WHEAT", 20);
        addBuiltin("sheep", "&fSheep", "SHEEP", "WHITE_WOOL", PetRarity.COMMON, 0.55D, 25_000L, true, 10, "WHEAT", 20);
        addBuiltin("pig", "&dPig", "PIG", "CARROT", PetRarity.COMMON, 0.55D, 25_000L, true, 10, "CARROT", 20);
        addBuiltin("mooshroom", "&cMooshroom Cow", "MUSHROOM_COW", "RED_MUSHROOM", PetRarity.RARE, 0.60D, 50_000L, true, 10, "WHEAT", 25);
        addBuiltin("sniffer", "&6Sniffer", "SNIFFER", "SNIFFER_EGG", PetRarity.EPIC, 0.75D, 100_000L, true, 15, "TORCHFLOWER_SEEDS", 35);
        addBuiltin("horse", "&6Horse", "HORSE", "SADDLE", PetRarity.RARE, 0.75D, 50_000L, true, 10, "WHEAT", 25);
        addBuiltin("donkey", "&7Donkey", "DONKEY", "CHEST", PetRarity.RARE, 0.70D, 50_000L, true, 10, "WHEAT", 25);
        addBuiltin("llama", "&eLlama", "LLAMA", "LEAD", PetRarity.RARE, 0.70D, 50_000L, true, 10, "WHEAT", 25);
        addBuiltin("goat", "&fGoat", "GOAT", "GOAT_HORN", PetRarity.RARE, 0.65D, 50_000L, true, 10, "WHEAT", 25);
        addBuiltin("camel", "&6Camel", "CAMEL", "SADDLE", PetRarity.EPIC, 0.75D, 100_000L, true, 15, "CACTUS", 35);
        addBuiltin("armadillo", "&7Armadillo", "ARMADILLO", "ARMADILLO_SCUTE", PetRarity.RARE, 0.55D, 50_000L, false, 10, "SPIDER_EYE", 25);
        addBuiltin("frog", "&aFrog", "FROG", "SLIME_BALL", PetRarity.RARE, 0.55D, 50_000L, false, 10, "SLIME_BALL", 25);
        addBuiltin("turtle", "&aTurtle", "TURTLE", "TURTLE_SCUTE", PetRarity.RARE, 0.60D, 50_000L, true, 10, "SEAGRASS", 25);
        addBuiltin("parrot", "&bParrot", "PARROT", "FEATHER", PetRarity.EPIC, 0.50D, 100_000L, true, 30, "WHEAT_SEEDS", 35);
        addBuiltin("bee", "&eBee", "BEE", "HONEYCOMB", PetRarity.EPIC, 0.55D, 100_000L, true, 30, "FLOWER", 35);
        addBuiltin("allay", "&bAllay", "ALLAY", "AMETHYST_SHARD", PetRarity.LEGENDARY, 0.50D, 175_000L, true, 30, "AMETHYST_SHARD", 45);
    }

    private void addBuiltin(String id, String display, String entity, String icon, PetRarity rarity, double scale, long price, boolean rideable, int adultLevel, String food, int feedExp) {
        String normalizedId = id.toLowerCase(Locale.ROOT);
        if (pets.containsKey(normalizedId)) return;
        if (isBlacklistedPet(normalizedId, entity)) return;
        EntityType type = entityType(entity);
        if (type == null) { plugin.getLogger().warning("VelioraPets: skip builtin animal pet, EntityType tidak tersedia: " + entity + " for " + id); return; }
        if (!isPermittedPetType(type)) return;
        boolean flying = isFlyingPet(type);
        boolean isAquatic = defaultAquatic(type);
        if (isAxolotlGround(type)) isAquatic = false;
        if (isAquatic && !allowAquaticPets() && !isAxolotlGround(type)) return;
        pets.put(normalizedId, new PetDefinition(normalizedId, display, type, material(icon, Material.BONE), rarity, PetSkillType.NONE, 0.0D, 0.0D, scale, balancedPrice(rarity, price), storageSize(rarity), material(food, defaultFood(rarity)), Math.max(1, feedExp), flying, rideable, Math.max(1, adultLevel), isAquatic));
    }

    private boolean isBlacklistedPet(String id, String rawEntity) {
        String raw = (rawEntity == null ? "" : rawEntity).toLowerCase(Locale.ROOT).replace("-", "_").replace(" ", "_");
        String key = (id == null ? "" : id).toLowerCase(Locale.ROOT).replace("-", "_").replace(" ", "_");
        return raw.equals("ghast") || raw.equals("happy_ghast") || raw.equals("happghast") || key.equals("happy_ghast") || key.equals("happghast") || key.equals("happyghast") || key.contains("happy_ghast") || key.contains("happghast");
    }

    private boolean isSafeAnimalType(EntityType type) { return type != null && SAFE_ANIMAL_ENTITY_NAMES.contains(type.name()); }
    private boolean isPermittedPetType(EntityType type) { return isSafeAnimalType(type) || isAllowedFlyingEntity(type); }
    private boolean isFlyingPet(EntityType type) { return type != null && FLYING_ENTITY_NAMES.contains(type.name()); }
    private boolean defaultAquatic(EntityType type) { if (type == null || isAxolotlGround(type)) return false; return AQUATIC_ENTITY_NAMES.contains(type.name()); }
    private boolean isAxolotlGround(EntityType type) { return walkingPetsOnly() && allowAxolotlGroundPet() && type == EntityType.AXOLOTL; }
    private boolean isDisabledByWalkingMode(EntityType type) { if (type == null) return false; if (isSafeAnimalType(type) || isAxolotlGround(type)) return false; return isFlyingPet(type) || defaultAquatic(type) || type.name().equals("SQUID") || type.name().equals("GLOW_SQUID"); }
    private boolean defaultRideable(EntityType type) { return type != null && RIDEABLE_ENTITY_NAMES.contains(type.name()); }
    private double maxSkillBonus(PetSkillType type) { return switch (type) { case QUEST_MONEY -> 0.03D; case FISHING_LUCK -> 0.02D; case PET_DAMAGE -> 0.05D; default -> 0.0D; }; }
    private double defaultChance(PetRarity rarity) { return switch (rarity) { case COMMON -> 65.0D; case RARE -> 25.0D; case EPIC -> 8.0D; case LEGENDARY -> 2.0D; case MYTHIC -> 0.0D; }; }
    private long defaultPrice(PetRarity rarity) { return switch (rarity) { case COMMON -> 25_000L; case RARE -> 50_000L; case EPIC -> 100_000L; case LEGENDARY -> 175_000L; case MYTHIC -> 250_000L; }; }
    private long balancedPrice(PetRarity rarity, long configured) { return Math.max(0L, configured); }
    private double defaultDamage(PetRarity rarity) { return switch (rarity) { case COMMON -> 1.5D; case RARE -> 2.0D; case EPIC -> 3.0D; case LEGENDARY -> 4.0D; case MYTHIC -> 5.0D; }; }
    private int defaultFeedExp(PetRarity rarity) { return switch (rarity) { case COMMON -> 20; case RARE -> 25; case EPIC -> 35; case LEGENDARY -> 45; case MYTHIC -> 60; }; }
    private Material defaultFood(PetRarity rarity) { return switch (rarity) { case COMMON -> Material.APPLE; case RARE -> Material.COOKED_CHICKEN; case EPIC -> Material.GOLDEN_CARROT; case LEGENDARY -> Material.GOLDEN_APPLE; case MYTHIC -> Material.ENCHANTED_GOLDEN_APPLE; }; }
    private Material material(String raw, Material fallback) { Material material = Material.matchMaterial(raw == null ? "" : raw.toUpperCase(Locale.ROOT)); return material == null ? fallback : material; }

    private EntityType entityType(String raw) {
        String key = raw == null ? "" : raw.toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        try { return EntityType.valueOf(key); } catch (Exception ignored) { }
        if (key.equals("MOOSHROOM")) {
            try { return EntityType.valueOf("MUSHROOM_COW"); } catch (Exception ignored) { }
        }
        if (key.equals("MUSHROOM_COW")) {
            try { return EntityType.valueOf("MOOSHROOM"); } catch (Exception ignored) { }
        }
        return null;
    }

    private Particle particle(String path, String fallback) { try { return Particle.valueOf(str(path, fallback).toUpperCase(Locale.ROOT)); } catch (Exception ignored) { return Particle.HAPPY_VILLAGER; } }
    private String str(String path, String fallback) { return config == null || !config.contains(path) ? fallback : config.getString(path, fallback); }
    private boolean bool(String path, boolean fallback) { return config == null || !config.contains(path) ? fallback : config.getBoolean(path, fallback); }
    private int integer(String path, int fallback) { return config == null || !config.contains(path) ? fallback : config.getInt(path, fallback); }
    private double number(String path, double fallback) { return config == null || !config.contains(path) ? fallback : config.getDouble(path, fallback); }
}
