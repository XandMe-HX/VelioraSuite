package id.velioragardens.veliorasuite.module.fishing;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.module.fishing.model.FishDefinition;
import id.velioragardens.veliorasuite.module.fishing.model.FishRarity;
import id.velioragardens.veliorasuite.module.fishing.model.FishingRodDefinition;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
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
        migrateHardMinigame(file);
        migrateProgressTwo(file);
        loadRarityChances();
        loadFishDefinitions();
    }

    private void migrateHardMinigame(File file) {
        if (config.getInt("settings.minigame.difficulty-version", 0) >= 2) return;
        String root = "settings.minigame.difficulty.";
        config.set(root + "vanilla.spam-needed", 8);
        config.set(root + "vanilla.seconds", 4.0D);
        config.set(root + "common.spam-needed", 14);
        config.set(root + "common.seconds", 5.0D);
        config.set(root + "ornamental.spam-needed", 24);
        config.set(root + "ornamental.seconds", 6.0D);
        config.set(root + "epic.spam-needed", 36);
        config.set(root + "epic.seconds", 7.0D);
        config.set(root + "legendary.spam-needed", 55);
        config.set(root + "legendary.seconds", 8.0D);
        config.set(root + "mitologi.spam-needed", 80);
        config.set(root + "mitologi.seconds", 10.0D);
        config.set("settings.minigame.difficulty-version", 2);
        try {
            config.save(file);
            plugin.getLogger().info("VelioraFishing: mode minigame hard v2 diterapkan.");
        } catch (IOException exception) {
            plugin.getLogger().warning("VelioraFishing: gagal menyimpan migrasi minigame: " + exception.getMessage());
        }
    }

    public VelioraSuite getPlugin() { return plugin; }

    public boolean isEnabled() { return bool("settings.enabled", true); }
    public String getPrefix() { return str("settings.prefix", "&8[&bVelioraFishing&8] "); }
    public boolean isMinigameEnabled() { return bool("settings.minigame.enabled", true); }
    public double getMinigameTriggerChance() { return number("settings.minigame.trigger-chance", 100.0D); }
    public int getClickCooldownMs() { return Math.max(0, integer("settings.minigame.click-cooldown-ms", 40)); }
    public boolean isMinigameShowTitle() { return bool("settings.minigame.show-title", true); }
    public boolean isRemoveVanillaCaughtEntity() { return bool("settings.minigame.remove-vanilla-caught-entity", true); }
    public boolean isMinigameEnabledForRarity(FishRarity rarity) { return bool(rarityPath("settings.minigame.difficulty", rarity, ".enabled"), rarity != FishRarity.TRASH); }
    public int getSpamNeeded(FishRarity rarity) { return Math.max(0, integer(rarityPath("settings.minigame.difficulty", rarity, ".spam-needed"), fallbackSpam(rarity))); }
    public double getMinigameSeconds(FishRarity rarity) { return Math.max(0.0D, number(rarityPath("settings.minigame.difficulty", rarity, ".seconds"), fallbackSeconds(rarity))); }
    public boolean isRodsEnabled() { return bool("settings.rods.enabled", true); }
    public FishRarity getCatchMessageMinRarity() {
        String value = str("settings.catch-message-min-rarity", "NONE").trim();
        return value.equalsIgnoreCase("NONE") || value.isEmpty() ? null : FishRarity.fromKey(value);
    }
    public List<FishingRodDefinition> getRodDefinitions() {
        List<FishingRodDefinition> fallback = List.of(
                new FishingRodDefinition(1, "Bamboo Drift", "#76C043", "#F3D36B", 0, 0, 0, 0, "Rod awal yang sederhana."),
                new FishingRodDefinition(2, "Coral Whisper", "#FF8A65", "#FF70A6", 12000, 75, 1, 0, "Buih coral lembut di kail."),
                new FishingRodDefinition(3, "Tidecaller", "#55E6FF", "#3E7BFA", 35000, 250, 1, 1, "Aura aqua di kail dan tangan."),
                new FishingRodDefinition(4, "Abyssal Current", "#2454C6", "#7837D6", 75000, 750, 2, 2, "Gelombang laut gelap saat menarik."),
                new FishingRodDefinition(5, "Celestial Leviathan", "#93E9FF", "#8D52E7", 150000, 1500, 3, 3, "Cahaya samudra surgawi yang lembut.")
        );
        ConfigurationSection section = config == null ? null : config.getConfigurationSection("settings.rods.items");
        if (section == null) return fallback;
        List<FishingRodDefinition> result = new java.util.ArrayList<>();
        for (String key : section.getKeys(false)) {
            ConfigurationSection rod = section.getConfigurationSection(key);
            if (rod == null) continue;
            int tier = Math.max(1, rod.getInt("tier", result.size() + 1));
            result.add(new FishingRodDefinition(tier, rod.getString("name", key), rod.getString("gradient-from", "#55D6FF"),
                    rod.getString("gradient-to", "#3E7BFA"), Math.max(0, rod.getInt("price", 0)),
                    Math.max(0, rod.getInt("required-catches", 0)), Math.max(0, rod.getInt("seconds-bonus", 0)),
                    Math.max(0, rod.getInt("click-reduction", 0)), rod.getString("aura", "Aura fishing."),
                    Math.max(0, rod.getInt("luck", 0)), Math.max(1.0D, rod.getDouble("max-weight", 10.0D)),
                    Math.max(0, rod.getInt("speed", 0)), rod.getBoolean("quest", false)));
        }
        result.sort(java.util.Comparator.comparingInt(FishingRodDefinition::tier));
        return result.isEmpty() ? fallback : result;
    }

    public boolean isSellGuiEnabled() { return bool("settings.sell-gui.enabled", true); }
    public String getSellGuiTitle() { return str("settings.sell-gui.title", "&8VelioraFishing Sell"); }
    public int getSellGuiSize() { return inventorySize(integer("settings.sell-gui.size", 54)); }
    public boolean isVanillaFishSellAllowed() { return bool("settings.sell-gui.allow-vanilla-fish", true); }
    public boolean isTopGuiEnabled() { return bool("settings.top.gui-enabled", false); }
    public int getTopLimit() { return Math.max(1, integer("settings.top.limit", 5)); }
    public boolean isQuestFishingProgressEnabled() { return bool("settings.quest-integration.enabled", true); }
    public int getPotionPrice(String type) { return Math.max(0, integer("settings.potions." + type + ".price", type.equals("mutation") ? 1000 : 750)); }
    public int getPotionDurationSeconds(String type) { return Math.max(60, integer("settings.potions." + type + ".duration-seconds", 600)); }
    public boolean isTradeEnabled() { return bool("settings.trade.enabled", true); }
    public int getTradeRequestTimeoutSeconds() { return Math.max(10, integer("settings.trade.request-timeout-seconds", 60)); }
    public int getTradeCountdownSeconds() { return Math.max(1, integer("settings.trade.countdown-seconds", 3)); }
    public double getTradeMaxDistance() { return Math.max(2.0D, number("settings.trade.max-distance", 10.0D)); }

    public boolean isBagEnabled() { return bool("settings.bag.enabled", true); }
    public String getBagTitle() { return str("settings.bag.title", "&8Fish Bag"); }
    public int getBagSize() { return inventorySize(integer("settings.bag.size", 54)); }
    public boolean isBagAutoStoreEnabled() { return bool("settings.bag.auto-store.enabled", false); }
    public Set<FishRarity> getBagAutoStoreRarities() {
        List<String> raw = config == null ? List.of() : config.getStringList("settings.bag.auto-store.rarities");
        if (raw.isEmpty()) raw = List.of("ORNAMENTAL", "EPIC", "LEGENDARY", "MITOLOGI");
        Set<FishRarity> result = java.util.EnumSet.noneOf(FishRarity.class);
        for (String value : raw) result.add(FishRarity.fromKey(value));
        return result;
    }

    public String getCollectionTitle() { return str("settings.collection.title", "&8Fish Collection"); }
    public int getCollectionSize() { return inventorySize(integer("settings.collection.size", 54)); }

    public boolean isEffectEnabled(FishRarity rarity) { return bool(rarityPath("effects", rarity, ".enabled"), true); }
    public Sound getEffectSound(FishRarity rarity) { return sound(str(rarityPath("effects", rarity, ".sound"), fallbackSound(rarity).name()), fallbackSound(rarity)); }
    public Particle getEffectParticle(FishRarity rarity) { return particle(str(rarityPath("effects", rarity, ".particle"), fallbackParticle(rarity).name()), fallbackParticle(rarity)); }
    public int getEffectAmount(FishRarity rarity) { return Math.max(0, integer(rarityPath("effects", rarity, ".amount"), fallbackParticleAmount(rarity))); }
    public boolean isEffectBroadcast(FishRarity rarity) { return bool(rarityPath("effects", rarity, ".broadcast"), rarity == FishRarity.LEGENDARY || rarity == FishRarity.MITOLOGI); }
    public boolean isVisualLightning(FishRarity rarity) { return bool(rarityPath("effects", rarity, ".visual-lightning"), rarity == FishRarity.MITOLOGI); }

    public String getUsePermission() { return str("permissions.use", "veliorasuite.fishing.use"); }
    public String getSellPermission() { return str("permissions.sell", "veliorasuite.fishing.sell"); }
    public String getBagPermission() { return str("permissions.bag", "veliorasuite.fishing.bag"); }
    public String getTopPermission() { return str("permissions.top", "veliorasuite.fishing.top"); }
    public String getAdminPermission() { return str("permissions.admin", "veliorasuite.fishing.admin"); }
    public String getReloadPermission() { return str("permissions.reload", "veliorasuite.fishing.reload"); }
    public String getRodBypassPermission() { return str("settings.rods.bypass-permission", "veliorasuite.fishing.rods.bypass"); }

    public String message(String path, String fallback) { return str("messages." + path, fallback).replace("%prefix%", getPrefix()); }
    public List<String> messageList(String path, List<String> fallback) { List<String> list = config == null ? List.of() : config.getStringList("messages." + path); return list.isEmpty() ? fallback : list; }
    public String color(String text) { return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text); }

    public Map<String, FishDefinition> getFishDefinitions() { return fishDefinitions; }
    public FishDefinition getFishDefinition(String id) { return id == null ? null : fishDefinitions.get(id.toLowerCase(Locale.ROOT)); }
    public Map<FishRarity, Double> getRarityChances() { return rarityChances; }

    public int minPrice(FishRarity rarity) {
        int configured = integer(rarityPath("settings.price", rarity, ".min"), fallbackMinPrice(rarity));
        return clamp(configured, 0, fallbackMinPrice(rarity));
    }

    public int maxPrice(FishRarity rarity) {
        int configured = integer(rarityPath("settings.price", rarity, ".max"), fallbackMaxPrice(rarity));
        return Math.max(minPrice(rarity), clamp(configured, 0, fallbackMaxPrice(rarity)));
    }

    public int maxFinalPrice(FishRarity rarity) {
        int configured = integer(rarityPath("settings.price", rarity, ".max-final"), fallbackFinalMaxPrice(rarity));
        return Math.max(maxPrice(rarity), clamp(configured, 0, fallbackFinalMaxPrice(rarity)));
    }

    public int randomPrice(FishRarity rarity) { int min = minPrice(rarity); int max = maxPrice(rarity); return max <= min ? min : ThreadLocalRandom.current().nextInt(min, max + 1); }

    public boolean isVanillaFish(Material material) {
        return Set.of(Material.COD, Material.SALMON, Material.TROPICAL_FISH, Material.PUFFERFISH).contains(material);
    }

    private void loadRarityChances() {
        rarityChances.clear();
        rarityChances.put(FishRarity.TRASH, number("settings.rarity-chance.trash", 28.0D));
        rarityChances.put(FishRarity.VANILLA, number("settings.rarity-chance.vanilla", 22.0D));
        rarityChances.put(FishRarity.COMMON, number("settings.rarity-chance.common", 46.0D));
        rarityChances.put(FishRarity.ORNAMENTAL, number("settings.rarity-chance.ornamental", 2.5D));
        rarityChances.put(FishRarity.EPIC, number(rarityPath("settings.rarity-chance", FishRarity.EPIC, ""), 1.2D));
        rarityChances.put(FishRarity.LEGENDARY, number("settings.rarity-chance.legendary", 0.08D));
        rarityChances.put(FishRarity.MITOLOGI, number("settings.rarity-chance.mitologi", 0.02D));
        rarityChances.put(FishRarity.SECRET, number("settings.rarity-chance.secret", 0.002D));
    }

    public FishingRodDefinition getRodDefinition(int tier) {
        return getRodDefinitions().stream().filter(rod -> rod.tier() == tier).findFirst().orElse(null);
    }

    private void migrateProgressTwo(File file) {
        if (config.getInt("settings.progress-two-version", 0) >= 1) return;
        try (InputStream stream = plugin.getResource("modules/fishing.yml")) {
            if (stream == null) return;
            File backup = new File(file.getParentFile(), "fishing-before-progress-2.yml");
            if (file.isFile() && !backup.exists()) Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
            FileConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
            for (String path : List.of("settings.rarity-chance", "settings.price", "settings.mutations",
                    "settings.rods", "settings.minigame.difficulty.secret", "effects.secret")) {
                config.set(path, defaults.get(path));
            }
            ConfigurationSection defaultFish = defaults.getConfigurationSection("fish");
            if (defaultFish != null) {
                for (String id : defaultFish.getKeys(false)) {
                    if (!config.contains("fish." + id)) config.set("fish." + id, defaultFish.get(id));
                }
            }
            config.set("settings.progress-two-version", 1);
            config.save(file);
            plugin.getLogger().info("VelioraFishing: ekonomi Koin dan konten Progres 2 diterapkan tanpa menghapus data pemain.");
        } catch (IOException exception) {
            plugin.getLogger().warning("VelioraFishing: gagal menyimpan migrasi Progres 2: " + exception.getMessage());
        }
    }

    public MutationRoll rollMutation(org.bukkit.entity.Player player) {
        Long boostUntil = player == null ? null : player.getPersistentDataContainer().get(
                new org.bukkit.NamespacedKey(plugin, "fishing_potion_mutation"), org.bukkit.persistence.PersistentDataType.LONG);
        double boost = boostUntil != null && boostUntil > System.currentTimeMillis() ? 2.0D : 1.0D;
        double roll = ThreadLocalRandom.current().nextDouble(100.0D);
        double current = 0.0D;
        ConfigurationSection section = config == null ? null : config.getConfigurationSection("settings.mutations");
        Map<String, Double> fallback = new LinkedHashMap<>();
        fallback.put("Gold", 1.4D); fallback.put("Ghost", 1.2D); fallback.put("Radioactive", 1.0D);
        fallback.put("Lightning", 0.9D); fallback.put("Midnight", 0.8D); fallback.put("Fairy Dust", 0.7D);
        fallback.put("Gemstone", 0.6D); fallback.put("Corrupt", 0.5D);
        for (Map.Entry<String, Double> entry : fallback.entrySet()) {
            String key = entry.getKey().toLowerCase(Locale.ROOT).replace(' ', '-');
            double chance = section == null ? entry.getValue() : section.getDouble(key + ".chance", entry.getValue());
            double multiplier = section == null ? mutationMultiplier(key) : section.getDouble(key + ".price-multiplier", mutationMultiplier(key));
            current += Math.max(0.0D, chance) * boost;
            if (roll <= current) return new MutationRoll(entry.getKey(), Math.max(1.0D, multiplier));
        }
        return new MutationRoll("Normal", 1.0D);
    }

    public String formatCoins(long value) {
        long safe = Math.max(0L, value);
        if (safe >= 1_000_000_000_000L) return compact(safe, 1_000_000_000_000D, "T");
        if (safe >= 1_000_000L) return compact(safe, 1_000_000D, "M");
        if (safe >= 1_000L) return compact(safe, 1_000D, "K");
        return String.valueOf(safe);
    }

    private String compact(long value, double divisor, String suffix) {
        double amount = value / divisor;
        return (amount >= 100.0D ? String.format(Locale.US, "%.0f", amount)
                : amount >= 10.0D ? String.format(Locale.US, "%.1f", amount)
                : String.format(Locale.US, "%.2f", amount)).replaceAll("\\.0+$", "") + suffix;
    }

    private double mutationMultiplier(String key) {
        return switch (key) {
            case "gold" -> 2.0D; case "ghost" -> 2.4D; case "radioactive" -> 3.0D;
            case "lightning" -> 3.5D; case "midnight" -> 4.0D; case "fairy-dust" -> 4.5D;
            case "gemstone" -> 5.0D; case "corrupt" -> 6.0D; default -> 1.0D;
        };
    }

    public record MutationRoll(String name, double multiplier) { }

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
        int safeMin = clamp(section.getInt("price.min", minPrice(rarity)), 0, minPrice(rarity));
        int safeMax = Math.max(safeMin, clamp(section.getInt("price.max", maxPrice(rarity)), 0, maxPrice(rarity)));
        return new FishDefinition(
                id.toLowerCase(Locale.ROOT),
                section.getString("name", id),
                rarity,
                material,
                Math.max(0.1D, section.getDouble("weight.min", fallbackMinWeight(rarity))),
                Math.max(0.1D, section.getDouble("weight.max", fallbackMaxWeight(rarity))),
                safeMin,
                safeMax,
                section.getString("origin", "VelioraFishing"),
                section.getString("region", "Veliora"),
                section.getBoolean("head.enabled", rarity.power() >= FishRarity.LEGENDARY.power()),
                headTexture(section),
                fallback
        );
    }

    /**
     * minecraft-heads.com exposes a Base64 textures value. A direct Mojang texture
     * URL is accepted as a friendlier config alternative and is converted once on
     * reload; this never performs an HTTP request from the game server.
     */
    private String headTexture(ConfigurationSection section) {
        String value = section.getString("head.texture-base64", "").trim();
        if (!value.isBlank()) return value;
        String url = section.getString("head.texture-url", "").trim();
        if (!url.startsWith("https://textures.minecraft.net/texture/")) return "";
        String payload = "{\"textures\":{\"SKIN\":{\"url\":\"" + url + "\"}}}";
        return Base64.getEncoder().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    private void addFallbackFish() {
        add("old_boot", "Old Boot", FishRarity.TRASH, Material.LEATHER_BOOTS, 0.1D, 1.0D, "VelioraFishing", "Sampah");
        add("cod", "Cod", FishRarity.VANILLA, Material.COD, 0.5D, 4.0D, "Vanilla", "Ocean");
        add("tilapia", "Tilapia", FishRarity.COMMON, Material.COD, 1.0D, 8.0D, "VelioraFishing", "Sungai");
        add("koi", "Koi Veliora", FishRarity.ORNAMENTAL, Material.TROPICAL_FISH, 0.3D, 2.5D, "VelioraFishing", "Kolam");
        add("bluefin", "Bluefin Tuna", FishRarity.EPIC, Material.SALMON, 20.0D, 180.0D, "VelioraFishing", "Laut");
        add("arwana_super_red", "Arwana Super Red", FishRarity.LEGENDARY, Material.PLAYER_HEAD, 10.0D, 60.0D, "VelioraFishing", "Legenda");
        add("bahamut", "Bahamut", FishRarity.MITOLOGI, Material.PLAYER_HEAD, 500.0D, 2500.0D, "VelioraFishing", "Mitologi");
    }

    private void add(String id, String name, FishRarity rarity, Material material, double minWeight, double maxWeight, String origin, String region) {
        fishDefinitions.put(id, new FishDefinition(id, name, rarity, material, minWeight, maxWeight, minPrice(rarity), maxPrice(rarity), origin, region, rarity.power() >= FishRarity.LEGENDARY.power(), "", Material.TROPICAL_FISH));
    }

    private int fallbackSpam(FishRarity rarity) { return switch (rarity) { case TRASH -> 0; case VANILLA -> 8; case COMMON -> 14; case ORNAMENTAL -> 26; case EPIC -> 42; case LEGENDARY -> 68; case MITOLOGI -> 92; case SECRET -> 120; }; }
    private double fallbackSeconds(FishRarity rarity) { return switch (rarity) { case TRASH -> 0.0D; case VANILLA -> 4.0D; case COMMON -> 5.0D; case ORNAMENTAL -> 6.0D; case EPIC -> 7.0D; case LEGENDARY -> 8.0D; case MITOLOGI -> 10.0D; case SECRET -> 12.0D; }; }
    private int fallbackMinPrice(FishRarity rarity) { return switch (rarity) { case TRASH -> 1; case VANILLA -> 3; case COMMON -> 8; case ORNAMENTAL -> 20; case EPIC -> 75; case LEGENDARY -> 350; case MITOLOGI -> 1500; case SECRET -> 5000; }; }
    private int fallbackMaxPrice(FishRarity rarity) { return switch (rarity) { case TRASH -> 1; case VANILLA -> 8; case COMMON -> 18; case ORNAMENTAL -> 55; case EPIC -> 180; case LEGENDARY -> 900; case MITOLOGI -> 3500; case SECRET -> 12000; }; }
    private int fallbackFinalMaxPrice(FishRarity rarity) { return switch (rarity) { case TRASH -> 1; case VANILLA -> 10; case COMMON -> 24; case ORNAMENTAL -> 75; case EPIC -> 240; case LEGENDARY -> 1200; case MITOLOGI -> 5000; case SECRET -> 18000; }; }
    private double fallbackMinWeight(FishRarity rarity) { return switch (rarity) { case TRASH -> 0.1D; case VANILLA -> 0.5D; case COMMON -> 1.0D; case ORNAMENTAL -> 0.2D; case EPIC -> 10.0D; case LEGENDARY -> 10.0D; case MITOLOGI -> 100.0D; case SECRET -> 500.0D; }; }
    private double fallbackMaxWeight(FishRarity rarity) { return switch (rarity) { case TRASH -> 1.0D; case VANILLA -> 5.0D; case COMMON -> 10.0D; case ORNAMENTAL -> 3.0D; case EPIC -> 200.0D; case LEGENDARY -> 1000.0D; case MITOLOGI -> 5000.0D; case SECRET -> 20000.0D; }; }
    private Material fallbackMaterial(FishRarity rarity) { return switch (rarity) { case TRASH -> Material.LEATHER_BOOTS; case VANILLA, COMMON -> Material.COD; case ORNAMENTAL -> Material.TROPICAL_FISH; case EPIC -> Material.SALMON; case LEGENDARY, MITOLOGI, SECRET -> Material.PLAYER_HEAD; }; }
    private Sound fallbackSound(FishRarity rarity) { return switch (rarity) { case TRASH -> Sound.BLOCK_NOTE_BLOCK_BASS; case VANILLA -> Sound.ENTITY_FISHING_BOBBER_SPLASH; case COMMON, ORNAMENTAL -> Sound.ENTITY_EXPERIENCE_ORB_PICKUP; case EPIC -> Sound.ENTITY_PLAYER_LEVELUP; case LEGENDARY -> Sound.UI_TOAST_CHALLENGE_COMPLETE; case MITOLOGI, SECRET -> Sound.ENTITY_ENDER_DRAGON_GROWL; }; }
    private Particle fallbackParticle(FishRarity rarity) { return switch (rarity) { case TRASH -> Particle.SMOKE; case VANILLA -> Particle.SPLASH; case COMMON -> Particle.BUBBLE; case ORNAMENTAL -> Particle.HAPPY_VILLAGER; case EPIC -> Particle.ENCHANT; case LEGENDARY -> Particle.TOTEM_OF_UNDYING; case MITOLOGI, SECRET -> Particle.DRAGON_BREATH; }; }
    private int fallbackParticleAmount(FishRarity rarity) { return switch (rarity) { case TRASH -> 3; case VANILLA -> 5; case COMMON -> 7; case ORNAMENTAL -> 10; case EPIC -> 20; case LEGENDARY -> 35; case MITOLOGI -> 60; case SECRET -> 90; }; }
    private Material material(String name, Material fallback) { Material material = Material.matchMaterial(name == null ? "" : name.trim().toUpperCase(Locale.ROOT)); return material == null ? fallback : material; }
    private Sound sound(String name, Sound fallback) { try { return Sound.valueOf(name.trim().toUpperCase(Locale.ROOT)); } catch (Exception ignored) { return fallback; } }
    private Particle particle(String name, Particle fallback) { try { return Particle.valueOf(name.trim().toUpperCase(Locale.ROOT)); } catch (Exception ignored) { return fallback; } }
    private int inventorySize(int size) { return size <= 0 ? 54 : Math.min(54, ((size + 8) / 9) * 9); }
    private String rarityPath(String base, FishRarity rarity, String suffix) { String primary = base + "." + rarity.key() + suffix; if (rarity == FishRarity.EPIC && config != null) { String legacy = base + ".rare" + suffix; if (!config.contains(primary) && config.contains(legacy)) return legacy; } return primary; }
    private int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
    private String str(String path, String fallback) { return config == null || !config.contains(path) ? fallback : config.getString(path, fallback); }
    private boolean bool(String path, boolean fallback) { return config == null || !config.contains(path) ? fallback : config.getBoolean(path, fallback); }
    private int integer(String path, int fallback) { return config == null || !config.contains(path) ? fallback : config.getInt(path, fallback); }
    private double number(String path, double fallback) { return config == null || !config.contains(path) ? fallback : config.getDouble(path, fallback); }
}
