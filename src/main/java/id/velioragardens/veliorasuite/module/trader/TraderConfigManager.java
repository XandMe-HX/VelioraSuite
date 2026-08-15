package id.velioragardens.veliorasuite.module.trader;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.module.trader.model.TraderCampBlock;
import id.velioragardens.veliorasuite.module.trader.model.TraderFishRequirement;
import id.velioragardens.veliorasuite.module.trader.model.TraderLocation;
import id.velioragardens.veliorasuite.module.trader.model.TraderPaymentType;
import id.velioragardens.veliorasuite.module.trader.model.TraderTradeItem;
import org.bukkit.ChatColor;
import org.bukkit.Material;
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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class TraderConfigManager {

    private final VelioraSuite plugin;
    private FileConfiguration config;
    private final List<TraderLocation> locations = new ArrayList<>();
    private final List<TraderCampBlock> campBlocks = new ArrayList<>();
    private final List<TraderTradeItem> tradePool = new ArrayList<>();

    public TraderConfigManager(VelioraSuite plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveResourceIfNotExists("modules/trader.yml");
        File file = new File(plugin.getDataFolder(), "modules/trader.yml");
        config = YamlConfiguration.loadConfiguration(file);
        mergeBundledDefaults(file);
        migrateConfig(file);
        migrateMythicEnchants(file);
        loadLocations();
        loadCampBlocks();
        loadTradePool();
    }

    private void mergeBundledDefaults(File file) {
        try (InputStream input = plugin.getResource("modules/trader.yml")) {
            if (input == null) return;
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(input, StandardCharsets.UTF_8));
            config.setDefaults(defaults);
            config.options().copyDefaults(true);
            config.save(file);
        } catch (IOException exception) {
            plugin.getLogger().warning("VelioraTrader: gagal memperbarui default trader.yml: " + exception.getMessage());
        }
    }

    private void migrateMythicEnchants(File file) {
        if (config.getInt("settings.enchant-version", 0) >= 2) return;
        config.set("trade-pool.excalibur.enchantments", List.of("DAMAGE_ALL:7", "FIRE_ASPECT:3", "LOOT_BONUS_MOBS:4", "DURABILITY:5", "MENDING:1"));
        config.set("trade-pool.angel_of_death_bow.enchantments", List.of("ARROW_DAMAGE:7", "ARROW_FIRE:1", "ARROW_KNOCKBACK:2", "ARROW_INFINITE:1", "DURABILITY:5"));
        config.set("trade-pool.trisula_poseidon.enchantments", List.of("LOYALTY:3", "IMPALING:7", "CHANNELING:1", "DURABILITY:5", "MENDING:1"));
        config.set("trade-pool.ryujin_no_tsuri.enchantments", List.of("LUCK:5", "LURE:5", "DURABILITY:5", "MENDING:1"));
        config.set("trade-pool.ancient_mace.enchantments", List.of("DENSITY:5", "BREACH:4", "WIND_BURST:2", "DURABILITY:5", "MENDING:1"));
        config.set("settings.enchant-version", 2);
        try {
            config.save(file);
            plugin.getLogger().info("VelioraTrader: enchant mitologi v2 diterapkan.");
        } catch (IOException exception) {
            plugin.getLogger().warning("VelioraTrader: gagal menyimpan migrasi enchant: " + exception.getMessage());
        }
    }

    private void migrateConfig(File file) {
        int version = config.getInt("settings.config-version", 1);
        if (version >= 2) return;
        config.set("settings.trade.random-items-per-spawn", 5);
        config.set("settings.config-version", 2);
        try {
            config.save(file);
        } catch (IOException exception) {
            plugin.getLogger().warning("VelioraTrader: gagal memigrasikan trader.yml: " + exception.getMessage());
        }
    }

    public boolean isEnabled() { return bool("settings.enabled", true); }
    public String getPrefix() { return str("settings.prefix", "&8[&6VelioraTrader&8] "); }
    public boolean isSpawnEnabled() { return bool("settings.spawn.enabled", true); }
    public int getIntervalHours() { return Math.max(1, integer("settings.spawn.interval-hours", 3)); }
    public int getAnchorHour() { return Math.floorMod(integer("settings.spawn.anchor-hour", 1), 24); }
    public String getTimezone() { return str("settings.spawn.timezone", "Asia/Jakarta"); }
    public int getActiveMinutes() { return Math.max(1, integer("settings.spawn.active-minutes", 30)); }
    public int getReminderMinutes() { return Math.max(1, integer("settings.spawn.reminder-minutes", 10)); }
    public boolean isRandomFromConfigLocations() { return bool("settings.spawn.random-from-config-locations", true); }
    public boolean isAnnounceSpawn() { return bool("settings.spawn.announce-spawn", true); }
    public boolean isAnnounceDespawn() { return bool("settings.spawn.announce-despawn", true); }
    public boolean isAnnouncePurchase() { return bool("settings.spawn.announce-purchase", true); }
    public int getMaxRandomAttempts() { return Math.max(1, integer("settings.spawn.max-random-attempts", 25)); }

    public boolean isDebugSpawn() { return bool("debug.spawn", false); }

    public EntityType getNpcType() { return entityType(str("npc.type", "VILLAGER"), EntityType.VILLAGER); }
    public String getNpcName() { return str("npc.name", "&6Veliora Trader"); }
    public boolean isNpcSilent() { return bool("npc.silent", true); }
    public boolean isNpcGravity() { return bool("npc.gravity", true); }
    public boolean isNpcGlowing() { return bool("npc.glowing", true); }
    public double getNpcOffsetX() { return number("npc.offset.x", 0.5D); }
    public double getNpcOffsetY() { return number("npc.offset.y", 1.0D); }
    public double getNpcOffsetZ() { return number("npc.offset.z", 1.5D); }

    public boolean isCompanionEnabled() { return bool("companion.enabled", true); }
    public EntityType getCompanionType() { return entityType(str("companion.type", "LLAMA"), EntityType.LLAMA); }
    public boolean isCompanionFrozen() { return bool("companion.frozen", true); }
    public String getCompanionName() { return str("companion.name", "&eTrader Companion"); }
    public boolean isCompanionNameVisible() { return bool("companion.show-name", false); }
    public double getCompanionOffsetX() { return number("companion.offset.x", 3.0D); }
    public double getCompanionOffsetY() { return number("companion.offset.y", 1.0D); }
    public double getCompanionOffsetZ() { return number("companion.offset.z", 1.5D); }

    public boolean isCampEnabled() { return bool("camp.enabled", true); }
    public boolean isRestoreOnDespawn() { return bool("camp.restore-on-despawn", true); }
    public boolean isCampSkipNpcSpace() { return bool("camp.skip-npc-space", true); }
    public boolean isProtectSolidBlocks() { return bool("camp.protect-solid-blocks", true); }
    public String getCampTemplate() { return str("camp.template", "ADVENTURER_TENT"); }
    public Set<Material> getAllowReplaceMaterials() {
        Set<Material> materials = new HashSet<>();
        List<String> raw = config == null ? List.of() : config.getStringList("camp.allow-replace");
        if (raw.isEmpty()) raw = List.of("AIR", "CAVE_AIR", "VOID_AIR", "GRASS", "TALL_GRASS", "SHORT_GRASS", "FERN", "LARGE_FERN", "SNOW");
        for (String name : raw) {
            Material material = Material.matchMaterial(name == null ? "" : name.trim().toUpperCase(Locale.ROOT));
            if (material != null) materials.add(material);
        }
        materials.add(Material.AIR);
        materials.add(Material.CAVE_AIR);
        materials.add(Material.VOID_AIR);
        return materials;
    }

    public String getGuiTitle() { return str("gui.title", "&8Veliora Trader"); }
    public int getGuiSize() { return inventorySize(integer("gui.size", 27)); }
    public List<Integer> getTradeSlots() { List<Integer> slots = config == null ? List.of() : config.getIntegerList("gui.trade-slots"); return slots.isEmpty() ? List.of(10, 11, 13, 15, 16) : slots; }
    public int getCloseSlot() { return integer("gui.close-slot", 26); }

    public int getRandomItemsPerSpawn() {
        int configured = integer("settings.trade.random-items-per-spawn", 5);
        return Math.max(1, Math.min(getTradeSlots().size(), configured));
    }
    public long getMaxMoneyPrice() { return Math.max(0L, Math.min(500_000L, config == null ? 500_000L : config.getLong("settings.trade.max-money-price", 500_000L))); }
    public String getStockMode() { return str("settings.trade.stock-mode", "GLOBAL"); }
    public int getDefaultStock() { return Math.max(1, integer("settings.trade.default-stock", 1)); }
    public int getPerPlayerLimit() { return Math.max(1, integer("settings.trade.per-player-limit", 1)); }

    public boolean isRepairBlockEnabled() { return bool("repair-block.enabled", true); }
    public List<String> getBlockedRepairCommands() { List<String> list = config == null ? List.of() : config.getStringList("repair-block.blocked-commands"); return list.isEmpty() ? List.of("repair", "fix", "erepair", "efix") : list; }

    public String getUsePermission() { return str("permissions.use", "veliorasuite.trader.use"); }
    public String getAdminPermission() { return str("permissions.admin", "veliorasuite.trader.admin"); }
    public String getReloadPermission() { return str("permissions.reload", "veliorasuite.trader.reload"); }

    public List<TraderLocation> getLocations() { return locations; }
    public List<TraderCampBlock> getCampBlocks() { return campBlocks; }
    public List<TraderTradeItem> getTradePool() { return tradePool; }

    public String message(String path, String fallback) { return str("messages." + path, fallback).replace("%prefix%", getPrefix()); }
    public String color(String text) { return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text); }

    private void loadLocations() {
        locations.clear();
        List<?> raw = config == null ? List.of() : config.getList("settings.spawn.locations", List.of());
        for (Object object : raw) {
            if (object instanceof java.util.Map<?, ?> map) {
                String world = String.valueOf(mapValue(map, "world", "world"));
                double x = doubleValue(map.get("x"), 0.0D);
                double y = doubleValue(map.get("y"), 80.0D);
                double z = doubleValue(map.get("z"), 0.0D);
                locations.add(new TraderLocation(world, x, y, z));
            }
        }
    }

    private void loadCampBlocks() {
        campBlocks.clear();
        List<?> raw = config == null ? List.of() : config.getList("camp.blocks", List.of());
        for (Object object : raw) {
            if (object instanceof java.util.Map<?, ?> map) {
                List<?> offset = map.get("offset") instanceof List<?> list ? list : List.of(0, 0, 0);
                int x = offset.size() > 0 ? intValue(offset.get(0), 0) : 0;
                int y = offset.size() > 1 ? intValue(offset.get(1), 0) : 0;
                int z = offset.size() > 2 ? intValue(offset.get(2), 0) : 0;
                Material material = material(String.valueOf(mapValue(map, "material", "BARREL")), Material.BARREL);
                campBlocks.add(new TraderCampBlock(x, y, z, material));
            }
        }
        if (campBlocks.isEmpty()) addFallbackCampTemplate();
    }

    private void addFallbackCampTemplate() {
        addCamp(-2, 0, -1, Material.OAK_PLANKS); addCamp(-1, 0, -1, Material.OAK_PLANKS); addCamp(0, 0, -1, Material.OAK_PLANKS); addCamp(1, 0, -1, Material.OAK_PLANKS);
        addCamp(-2, 0, 0, Material.OAK_SLAB); addCamp(-1, 0, 0, Material.OAK_SLAB); addCamp(0, 0, 0, Material.OAK_SLAB); addCamp(1, 0, 0, Material.OAK_SLAB);
        addCamp(-2, 1, 0, Material.BARREL); addCamp(-1, 1, 0, Material.CHEST); addCamp(0, 1, 0, Material.CRAFTING_TABLE); addCamp(1, 1, 0, Material.COMPOSTER); addCamp(2, 1, 0, Material.BARREL);
        addCamp(-3, 0, 1, Material.CAMPFIRE); addCamp(2, 1, 1, Material.OAK_FENCE); addCamp(2, 2, 1, Material.LANTERN);
        addCamp(-2, 1, -2, Material.WHITE_WOOL); addCamp(-1, 2, -2, Material.WHITE_WOOL); addCamp(0, 2, -2, Material.WHITE_WOOL); addCamp(1, 1, -2, Material.WHITE_WOOL);
        addCamp(-2, 1, -3, Material.WHITE_WOOL); addCamp(-1, 2, -3, Material.WHITE_WOOL); addCamp(0, 2, -3, Material.WHITE_WOOL); addCamp(1, 1, -3, Material.WHITE_WOOL);
        addCamp(-1, 1, -2, Material.BROWN_CARPET); addCamp(0, 1, -2, Material.BROWN_CARPET); addCamp(-1, 1, -3, Material.BROWN_CARPET); addCamp(0, 1, -3, Material.BROWN_CARPET);
    }

    private void addCamp(int x, int y, int z, Material material) { campBlocks.add(new TraderCampBlock(x, y, z, material)); }

    private void loadTradePool() {
        tradePool.clear();
        ConfigurationSection section = config == null ? null : config.getConfigurationSection("trade-pool");
        if (section == null) return;
        for (String id : section.getKeys(false)) {
            ConfigurationSection item = section.getConfigurationSection(id);
            if (item == null) continue;
            Material material = material(item.getString("material", "STONE"), Material.STONE);
            TraderPaymentType paymentType = TraderPaymentType.from(item.getString("payment.type", "MONEY"));
            List<TraderFishRequirement> fish = new ArrayList<>();
            for (Object object : item.getList("payment.fish.requirements", List.of())) {
                if (object instanceof java.util.Map<?, ?> map) {
                    fish.add(new TraderFishRequirement(
                            String.valueOf(mapValue(map, "fish-id", "")),
                            String.valueOf(mapValue(map, "rarity", "")),
                            intValue(map.get("amount"), 1)
                    ));
                }
            }
            tradePool.add(new TraderTradeItem(
                    id.toLowerCase(Locale.ROOT),
                    material,
                    item.getString("name", id),
                    Math.max(1, item.getInt("amount", 1)),
                    item.getStringList("lore"),
                    item.getStringList("enchantments"),
                    Math.max(1, item.getInt("stock", getDefaultStock())),
                    Math.max(0, item.getInt("custom-damage", 0)),
                    Math.max(0, item.getInt("fishing-luck-bonus", 0)),
                    item.getBoolean("unrepairable", true),
                    paymentType,
                    Math.min(getMaxMoneyPrice(), Math.max(0L, item.getLong("payment.money", 0L))),
                    fish
            ));
        }
    }

    private Object mapValue(java.util.Map<?, ?> map, String key, Object fallback) { Object value = map.get(key); return value == null ? fallback : value; }
    private EntityType entityType(String name, EntityType fallback) { try { return EntityType.valueOf(name.trim().toUpperCase(Locale.ROOT)); } catch (Exception ignored) { return fallback; } }
    private Material material(String name, Material fallback) { Material material = Material.matchMaterial(name == null ? "" : name.trim().toUpperCase(Locale.ROOT)); return material == null ? fallback : material; }
    private int inventorySize(int size) { return size <= 0 ? 27 : Math.min(54, ((size + 8) / 9) * 9); }
    private String str(String path, String fallback) { return config == null || !config.contains(path) ? fallback : config.getString(path, fallback); }
    private boolean bool(String path, boolean fallback) { return config == null || !config.contains(path) ? fallback : config.getBoolean(path, fallback); }
    private int integer(String path, int fallback) { return config == null || !config.contains(path) ? fallback : config.getInt(path, fallback); }
    private double number(String path, double fallback) { return config == null || !config.contains(path) ? fallback : config.getDouble(path, fallback); }
    private double doubleValue(Object value, double fallback) { return value instanceof Number number ? number.doubleValue() : fallback; }
    private int intValue(Object value, int fallback) { return value instanceof Number number ? number.intValue() : fallback; }
}
