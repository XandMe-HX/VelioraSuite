package id.velioragardens.veliorasuite.module.fishing;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.module.fishing.model.FishDefinition;
import id.velioragardens.veliorasuite.module.fishing.model.FishRarity;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public final class FishingConfigManager {

    private final VelioraSuite plugin;
    private FileConfiguration config;
    private final Map<String, FishDefinition> fishDefinitions = new LinkedHashMap<>();
    private final Map<FishRarity, Double> rarityChances = new EnumMap<>(FishRarity.class);

    public FishingConfigManager(VelioraSuite plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveResourceIfNotExists("modules/fishing.yml");
        File file = new File(plugin.getDataFolder(), "modules/fishing.yml");
        this.config = YamlConfiguration.loadConfiguration(file);
        loadRarityChances();
        loadFishDefinitions();
    }

    public boolean isEnabled() { return bool("settings.enabled", true); }
    public String getPrefix() { return str("settings.prefix", "&8[&bVelioraFishing&8] "); }
    public boolean isMinigameEnabled() { return bool("settings.minigame.enabled", true); }
    public double getMinigameTriggerChance() { return number("settings.minigame.trigger-chance", 35.0D); }
    public int getClickCooldownMs() { return Math.max(0, integer("settings.minigame.click-cooldown-ms", 40)); }
    public boolean isMinigameShowTitle() { return bool("settings.minigame.show-title", true); }
    public boolean isRemoveVanillaCaughtEntity() { return bool("settings.minigame.remove-vanilla-caught-entity", true); }
    public int getSpamNeeded(FishRarity rarity) { return Math.max(1, integer("settings.minigame.difficulty." + rarity.key() + ".spam-needed", fallbackSpam(rarity))); }
    public double getMinigameSeconds(FishRarity rarity) { return Math.max(1.0D, number("settings.minigame.difficulty." + rarity.key() + ".seconds", fallbackSeconds(rarity))); }
    public boolean isSellGuiEnabled() { return bool("settings.sell-gui.enabled", true); }
    public String getSellGuiTitle() { return str("settings.sell-gui.title", "&8VelioraFishing Sell"); }
    public int getSellGuiSize() { int size = integer("settings.sell-gui.size", 54); return size <= 0 ? 54 : Math.min(54, ((size + 8) / 9) * 9); }
    public boolean isVanillaFishSellAllowed() { return bool("settings.sell-gui.allow-vanilla-fish", true); }
    public boolean isTopGuiEnabled() { return bool("settings.top.gui-enabled", false); }
    public int getTopLimit() { return Math.max(1, integer("settings.top.limit", 5)); }
    public boolean isQuestFishingProgressEnabled() { return bool("settings.quest-integration.enabled", true); }

    public String getUsePermission() { return str("permissions.use", "veliorasuite.fishing.use"); }
    public String getSellPermission() { return str("permissions.sell", "veliorasuite.fishing.sell"); }
    public String getBagPermission() { return str("permissions.bag", "veliorasuite.fishing.bag"); }
    public String getTopPermission() { return str("permissions.top", "veliorasuite.fishing.top"); }
    public String getAdminPermission() { return str("permissions.admin", "veliorasuite.fishing.admin"); }
    public String getReloadPermission() { return str("permissions.reload", "veliorasuite.fishing.reload"); }

    public String message(String path, String fallback) { return str("messages." + path, fallback).replace("%prefix%", getPrefix()); }
    public List<String> messageList(String path, List<String> fallback) { List<String> list = config == null ? List.of() : config.getStringList("messages." + path); return list.isEmpty() ? fallback : list; }
    public String color(String text) { return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text); }

    public Map<String, FishDefinition> getFishDefinitions() { return fishDefinitions; }
    public Map<FishRarity, Double> getRarityChances() { return rarityChances; }

    public int minPrice(FishRarity rarity) { return Math.max(0, integer("settings.price." + rarity.key() + ".min", fallbackMinPrice(rarity))); }
    public int maxPrice(FishRarity rarity) { return Math.max(minPrice(rarity), integer("settings.price." + rarity.key() + ".max", fallbackMaxPrice(rarity))); }
    public int maxFinalPrice(FishRarity rarity) { return Math.max(maxPrice(rarity), integer("settings.price." + rarity.key() + ".max-final", fallbackFinalMaxPrice(rarity))); }
    public int randomPrice(FishRarity rarity) { int min = minPrice(rarity); int max = maxPrice(rarity); return max <= min ? min : ThreadLocalRandom.current().nextInt(min, max + 1); }

    public boolean isVanillaFish(Material material) {
        return Set.of(Material.COD, Material.SALMON, Material.TROPICAL_FISH, Material.PUFFERFISH).contains(material);
    }

    private void loadRarityChances() {
        rarityChances.clear();
        rarityChances.put(FishRarity.TRASH, number("settings.rarity-chance.trash", 20.0D));
        rarityChances.put(FishRarity.VANILLA, number("settings.rarity-chance.vanilla", 15.0D));
        rarityChances.put(FishRarity.COMMON, number("settings.rarity-chance.common", 55.0D));
        rarityChances.put(FishRarity.ORNAMENTAL, number("settings.rarity-chance.ornamental", 7.0D));
        rarityChances.put(FishRarity.RARE, number("settings.rarity-chance.rare", 2.5D));
        rarityChances.put(FishRarity.LEGENDARY, number("settings.rarity-chance.legendary", 0.45D));
        rarityChances.put(FishRarity.MITOLOGI, number("settings.rarity-chance.mitologi", 0.05D));
    }

    private void loadFishDefinitions() {
        fishDefinitions.clear();
        ConfigurationSection section = config == null ? null : config.getConfigurationSection("fish");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                FishDefinition definition = readFishDefinition(id, section.getConfigurationSection(id));
                if (definition != null) fishDefinitions.put(definition.id(), definition);
            }
        }
        if (fishDefinitions.isEmpty()) addFallbackFish();
    }

    private FishDefinition readFishDefinition(String id, ConfigurationSection section) {
        if (section == null) return null;
        FishRarity rarity = FishRarity.fromKey(section.getString("rarity", "COMMON"));
        Material material = material(section.getString("material", fallbackMaterial(rarity).name()), fallbackMaterial(rarity));
        Material fallback = material(section.getString("head.fallback-material", material.name()), material);
        return new FishDefinition(
                id.toLowerCase(Locale.ROOT),
                section.getString("name", id),
                rarity,
                material,
                Math.max(0.1D, section.getDouble("weight.min", fallbackMinWeight(rarity))),
                Math.max(0.1D, section.getDouble("weight.max", fallbackMaxWeight(rarity))),
                Math.max(0, section.getInt("price.min", minPrice(rarity))),
                Math.max(0, section.getInt("price.max", maxPrice(rarity))),
                section.getString("origin", "VelioraFishing"),
                section.getString("region", "Veliora"),
                section.getBoolean("head.enabled", rarity == FishRarity.LEGENDARY || rarity == FishRarity.MITOLOGI),
                section.getString("head.texture-base64", ""),
                fallback
        );
    }

    private void addFallbackFish() {
        add("old_boot", "Old Boot", FishRarity.TRASH, Material.LEATHER_BOOTS, 0.1D, 1.0D, "VelioraFishing", "Sampah");
        add("cod", "Cod", FishRarity.VANILLA, Material.COD, 0.5D, 4.0D, "Vanilla", "Ocean");
        add("tilapia", "Tilapia", FishRarity.COMMON, Material.COD, 1.0D, 8.0D, "VelioraFishing", "Sungai");
        add("koi", "Koi Veliora", FishRarity.ORNAMENTAL, Material.TROPICAL_FISH, 0.3D, 2.5D, "VelioraFishing", "Kolam");
        add("bluefin", "Bluefin Tuna", FishRarity.RARE, Material.SALMON, 20.0D, 180.0D, "VelioraFishing", "Laut");
        add("arwana_super_red", "Arwana Super Red", FishRarity.LEGENDARY, Material.PLAYER_HEAD, 10.0D, 60.0D, "VelioraFishing", "Legenda");
        add("bahamut", "Bahamut", FishRarity.MITOLOGI, Material.PLAYER_HEAD, 500.0D, 2500.0D, "VelioraFishing", "Mitologi");
    }

    private void add(String id, String name, FishRarity rarity, Material material, double minWeight, double maxWeight, String origin, String region) {
        fishDefinitions.put(id, new FishDefinition(id, name, rarity, material, minWeight, maxWeight, minPrice(rarity), maxPrice(rarity), origin, region, rarity == FishRarity.LEGENDARY || rarity == FishRarity.MITOLOGI, "", Material.TROPICAL_FISH));
    }

    private int fallbackSpam(FishRarity rarity) {
        return switch (rarity) {
            case TRASH -> 3;
            case VANILLA -> 4;
            case COMMON -> 5;
            case ORNAMENTAL -> 10;
            case RARE -> 18;
            case LEGENDARY -> 35;
            case MITOLOGI -> 55;
        };
    }

    private double fallbackSeconds(FishRarity rarity) {
        return switch (rarity) {
            case TRASH, VANILLA, COMMON -> 4.0D;
            case ORNAMENTAL -> 5.0D;
            case RARE -> 6.0D;
            case LEGENDARY -> 7.0D;
            case MITOLOGI -> 8.0D;
        };
    }

    private int fallbackMinPrice(FishRarity rarity) { return switch (rarity) { case TRASH -> 1; case VANILLA -> 50; case COMMON -> 100; case ORNAMENTAL -> 500; case RARE -> 2000; case LEGENDARY -> 10000; case MITOLOGI -> 50000; }; }
    private int fallbackMaxPrice(FishRarity rarity) { return switch (rarity) { case TRASH -> 5; case VANILLA -> 100; case COMMON -> 500; case ORNAMENTAL -> 2000; case RARE -> 10000; case LEGENDARY -> 75000; case MITOLOGI -> 150000; }; }
    private int fallbackFinalMaxPrice(FishRarity rarity) { return switch (rarity) { case LEGENDARY -> 75000; case MITOLOGI -> 500000; default -> fallbackMaxPrice(rarity); }; }
    private double fallbackMinWeight(FishRarity rarity) { return switch (rarity) { case TRASH -> 0.1D; case VANILLA -> 0.5D; case COMMON -> 1.0D; case ORNAMENTAL -> 0.2D; case RARE -> 10.0D; case LEGENDARY -> 10.0D; case MITOLOGI -> 100.0D; }; }
    private double fallbackMaxWeight(FishRarity rarity) { return switch (rarity) { case TRASH -> 1.0D; case VANILLA -> 5.0D; case COMMON -> 10.0D; case ORNAMENTAL -> 3.0D; case RARE -> 200.0D; case LEGENDARY -> 1000.0D; case MITOLOGI -> 2500.0D; }; }
    private Material fallbackMaterial(FishRarity rarity) { return switch (rarity) { case TRASH -> Material.LEATHER_BOOTS; case VANILLA, COMMON -> Material.COD; case ORNAMENTAL -> Material.TROPICAL_FISH; case RARE -> Material.SALMON; case LEGENDARY, MITOLOGI -> Material.PLAYER_HEAD; }; }
    private Material material(String name, Material fallback) { Material material = Material.matchMaterial(name == null ? "" : name.trim().toUpperCase(Locale.ROOT)); return material == null ? fallback : material; }
    private String str(String path, String fallback) { return config == null || !config.contains(path) ? fallback : config.getString(path, fallback); }
    private boolean bool(String path, boolean fallback) { return config == null || !config.contains(path) ? fallback : config.getBoolean(path, fallback); }
    private int integer(String path, int fallback) { return config == null || !config.contains(path) ? fallback : config.getInt(path, fallback); }
    private double number(String path, double fallback) { return config == null || !config.contains(path) ? fallback : config.getDouble(path, fallback); }
}
