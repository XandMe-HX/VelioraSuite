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
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class PetConfigManager {
    private final VelioraSuite plugin;
    private FileConfiguration config;
    private final Map<String, PetDefinition> pets = new LinkedHashMap<>();
    private final Map<PetRarity, Double> chances = new EnumMap<>(PetRarity.class);

    public PetConfigManager(VelioraSuite plugin) { this.plugin = plugin; }

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
    public boolean allowStorageWithoutActive() { return bool("storage.allow-storage-without-active", false); }
    public int storageSize(PetRarity rarity) { return Math.max(9, integer("storage.size." + rarity.name().toLowerCase(Locale.ROOT), defaultStorage(rarity))); }

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
    public Sound sound(String path, String fallback) { try { return Sound.valueOf(str(path, fallback).toUpperCase(Locale.ROOT)); } catch (Exception ignored) { return Sound.UI_BUTTON_CLICK; } }

    private void loadChances() {
        chances.clear();
        for (PetRarity rarity : PetRarity.values()) chances.put(rarity, Math.max(0.0D, number("gacha.chance." + rarity.name().toLowerCase(Locale.ROOT), defaultChance(rarity))));
    }

    private void loadPets() {
        pets.clear();
        ConfigurationSection section = config.getConfigurationSection("pets");
        if (section == null) return;
        for (String id : section.getKeys(false)) {
            String path = "pets." + id;
            EntityType type = entityType(config.getString(path + ".entity", "WOLF"));
            if (isSkippedFlying(type)) {
                plugin.getLogger().warning("VelioraPets: skip flying/unsafe pet entity " + type + " for " + id);
                continue;
            }
            PetRarity rarity = PetRarity.from(config.getString(path + ".rarity", "COMMON"));
            pets.put(id.toLowerCase(Locale.ROOT), new PetDefinition(
                    id.toLowerCase(Locale.ROOT),
                    config.getString(path + ".display-name", id),
                    type,
                    material(config.getString(path + ".icon", "BONE"), Material.BONE),
                    rarity,
                    PetSkillType.from(config.getString(path + ".skill.type", "NONE")),
                    Math.min(maxSkillBonus(PetSkillType.from(config.getString(path + ".skill.type", "NONE"))), Math.max(0.0D, config.getDouble(path + ".skill.bonus", 0.0D))),
                    Math.max(0.5D, config.getDouble(path + ".damage", defaultDamage(rarity))),
                    Math.max(0.1D, config.getDouble(path + ".scale", 0.5D)),
                    Math.max(0L, config.getLong(path + ".price", defaultPrice(rarity))),
                    storageSize(rarity)
            ));
        }
    }

    private boolean isSkippedFlying(EntityType type) {
        return switch (type) {
            case PHANTOM, GHAST, BLAZE, VEX, ENDER_DRAGON, WITHER -> true;
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
    private Material material(String raw, Material fallback) { Material material = Material.matchMaterial(raw == null ? "" : raw.toUpperCase(Locale.ROOT)); return material == null ? fallback : material; }
    private EntityType entityType(String raw) { try { return EntityType.valueOf(raw.toUpperCase(Locale.ROOT)); } catch (Exception ignored) { return EntityType.WOLF; } }
    private Particle particle(String path, String fallback) { try { return Particle.valueOf(str(path, fallback).toUpperCase(Locale.ROOT)); } catch (Exception ignored) { return Particle.HAPPY_VILLAGER; } }
    private String str(String path, String fallback) { return config == null || !config.contains(path) ? fallback : config.getString(path, fallback); }
    private boolean bool(String path, boolean fallback) { return config == null || !config.contains(path) ? fallback : config.getBoolean(path, fallback); }
    private int integer(String path, int fallback) { return config == null || !config.contains(path) ? fallback : config.getInt(path, fallback); }
    private double number(String path, double fallback) { return config == null || !config.contains(path) ? fallback : config.getDouble(path, fallback); }
}
