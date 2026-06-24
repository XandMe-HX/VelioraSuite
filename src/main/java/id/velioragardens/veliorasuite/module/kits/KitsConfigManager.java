package id.velioragardens.veliorasuite.module.kits;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.module.kits.model.Kit;
import id.velioragardens.veliorasuite.module.kits.model.KitGuiItem;
import id.velioragardens.veliorasuite.module.kits.model.KitReward;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class KitsConfigManager {

    private final VelioraSuite plugin;
    private final Map<String, Kit> kits = new LinkedHashMap<>();
    private FileConfiguration config;

    public KitsConfigManager(VelioraSuite plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveResourceIfNotExists("modules/kits.yml");
        File file = new File(plugin.getDataFolder(), "modules/kits.yml");
        this.config = YamlConfiguration.loadConfiguration(file);
        loadKits();
    }

    public boolean isEnabled() {
        return getBoolean("settings.enabled", true);
    }

    public boolean isOpenGuiOnMainCommand() {
        return getBoolean("settings.open-gui-on-main-command", true);
    }

    public boolean isUsePerKitPermission() {
        return getBoolean("settings.use-per-kit-permission", true);
    }

    public boolean isBlockClaimWhenInventoryFull() {
        return getBoolean("settings.block-claim-when-inventory-full", true);
    }

    public boolean isDropExtraItems() {
        return getBoolean("settings.drop-extra-items", false);
    }

    public boolean isFirstJoinKitEnabled() {
        return getBoolean("settings.first-join-kit.enabled", true);
    }

    public String getFirstJoinKit() {
        return getString("settings.first-join-kit.kit", "starter").toLowerCase(Locale.ROOT);
    }

    public String getPrefix() {
        return getString("settings.prefix", "&8[&bVelioraKits&8] ");
    }

    public String getUsePermission() {
        return getString("permissions.use", "veliorasuite.kits.use");
    }

    public String getAdminPermission() {
        return getString("permissions.admin", "veliorasuite.kits.admin");
    }

    public String getReloadPermission() {
        return getString("permissions.reload", "veliorasuite.kits.reload");
    }

    public String getBypassCooldownPermission() {
        return getString("permissions.bypass-cooldown", "veliorasuite.kits.bypasscooldown");
    }

    public String getBypassPricePermission() {
        return getString("permissions.bypass-price", "veliorasuite.kits.bypassprice");
    }

    public String getKitPermissionPrefix() {
        return getString("permissions.kit-prefix", "veliorasuite.kits.kit.");
    }

    public String getPremiumPermissionPrefix() {
        return getString("permissions.premium-prefix", getString("settings.premium-permission-prefix", "veliorakits.premium."));
    }

    public String getGuiTitle() {
        return color(getString("gui.title", "&8Veliora Kits"));
    }

    public String getPreviewTitle(String kitDisplayName) {
        return color(getString("gui.preview-title", getString("messages.preview-title", "&8Preview: %kit%"))
                .replace("%kit%", kitDisplayName));
    }

    public int getGuiSize() {
        int size = getInt("gui.size", 54);
        if (size < 9 || size > 54 || size % 9 != 0) {
            plugin.getLogger().warning("VelioraKits: gui.size tidak valid. Fallback ke 54.");
            return 54;
        }
        return size;
    }

    public boolean isFillerEnabled() {
        return getBoolean("gui.filler.enabled", true);
    }

    public Material getFillerMaterial() {
        return parseMaterial(getString("gui.filler.material", "BLACK_STAINED_GLASS_PANE"), Material.BLACK_STAINED_GLASS_PANE, "gui.filler.material");
    }

    public String getFillerName() {
        return color(getString("gui.filler.name", " "));
    }

    public List<String> getFillerLore() {
        return colorList(config.getStringList("gui.filler.lore"));
    }

    public List<String> getStatusLore(String key) {
        List<String> list = config.getStringList("gui.status-lore." + key);
        return list.isEmpty() ? List.of("&7Status: &f" + key) : list;
    }

    public Kit getKit(String id) {
        if (id == null) {
            return null;
        }
        return kits.get(id.toLowerCase(Locale.ROOT));
    }

    public List<Kit> getEnabledKits() {
        return kits.values().stream().filter(Kit::isEnabled).toList();
    }

    public List<String> getKitIds() {
        return new ArrayList<>(kits.keySet());
    }

    public String getMessage(String path, String fallback) {
        return getString("messages." + path, fallback).replace("%prefix%", getPrefix());
    }

    public List<String> getMessageList(String path, List<String> fallback) {
        List<String> list = config.getStringList("messages." + path);
        return list.isEmpty() ? fallback : list;
    }

    public String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }

    public List<String> colorList(List<String> lines) {
        return lines == null ? List.of() : lines.stream().map(this::color).toList();
    }

    private void loadKits() {
        kits.clear();
        ConfigurationSection section = config.getConfigurationSection("kits");

        if (section == null) {
            plugin.getLogger().warning("VelioraKits: section kits tidak ditemukan di kits.yml.");
            return;
        }

        for (String rawId : section.getKeys(false)) {
            String id = rawId.toLowerCase(Locale.ROOT);
            String path = "kits." + rawId;
            Kit kit = parseKit(id, path);
            kits.put(id, kit);
        }
    }

    private Kit parseKit(String id, String path) {
        boolean enabled = getBoolean(path + ".enabled", true);
        String displayName = getString(path + ".display-name", id);
        List<String> description = config.getStringList(path + ".description");
        String permission = getString(path + ".permission", "");
        int premiumLevel = Math.max(0, getInt(path + ".premium-level", 0));
        long cooldownMillis = parseCooldown(getString(path + ".cooldown", "24h"), path + ".cooldown");

        boolean buyEnabled = getBoolean(path + ".buy.enabled", false);
        double price = Math.max(0.0D, getDouble(path + ".buy.price", 0.0D));
        boolean oneTimePurchase = getBoolean(path + ".buy.one-time-purchase", true);

        int slot = getInt(path + ".gui.slot", 0);
        Material guiMaterial = parseMaterial(getString(path + ".gui.material", "CHEST"), Material.CHEST, path + ".gui.material");
        String guiName = getString(path + ".gui.name", displayName);
        List<String> guiLore = config.getStringList(path + ".gui.lore");
        KitGuiItem guiItem = new KitGuiItem(slot, guiMaterial, guiName, guiLore);

        List<ItemStack> items = parseItems(path + ".items");
        KitReward reward = new KitReward(
                Math.max(0.0D, getDouble(path + ".rewards.money", 0.0D)),
                Math.max(0, getInt(path + ".rewards.exp", 0)),
                List.of(),
                config.getStringList(path + ".rewards.commands")
        );

        return new Kit(id, enabled, displayName, description, permission, premiumLevel, cooldownMillis, buyEnabled, price, oneTimePurchase, guiItem, items, reward);
    }

    private List<ItemStack> parseItems(String path) {
        List<ItemStack> items = new ArrayList<>();
        List<Map<?, ?>> maps = config.getMapList(path);

        for (Map<?, ?> map : maps) {
            String materialName = getMapString(map, "material", "STONE");
            Material material = parseMaterial(materialName, null, path + ".material");

            if (material == null) {
                continue;
            }

            int amount = getMapInt(map, "amount", 1);
            if (amount < 1) {
                plugin.getLogger().warning("VelioraKits: amount kurang dari 1 pada " + path + ". Fallback ke 1.");
                amount = 1;
            }
            if (amount > 64) {
                plugin.getLogger().warning("VelioraKits: amount lebih dari 64 pada " + path + ". Dibatasi ke 64 agar aman.");
                amount = 64;
            }

            ItemStack item = new ItemStack(material, amount);
            ItemMeta meta = item.getItemMeta();

            if (meta != null) {
                String name = getMapString(map, "name", "");
                if (!name.isBlank()) {
                    meta.setDisplayName(color(name));
                }

                List<String> lore = getMapStringList(map, "lore");
                if (!lore.isEmpty()) {
                    meta.setLore(colorList(lore));
                }

                item.setItemMeta(meta);
            }

            applyEnchants(item, getMapStringList(map, "enchants"), path);
            items.add(item);
        }

        return items;
    }

    private void applyEnchants(ItemStack item, List<String> enchants, String path) {
        for (String rawEnchant : enchants) {
            String[] split = rawEnchant.split(":");
            String enchantName = split[0].trim().toLowerCase(Locale.ROOT);
            int level = 1;

            if (split.length > 1) {
                try {
                    level = Math.max(1, Integer.parseInt(split[1].trim()));
                } catch (NumberFormatException ignored) {
                    level = 1;
                }
            }

            Enchantment enchantment;
            try {
                enchantment = Enchantment.getByKey(NamespacedKey.minecraft(enchantName));
            } catch (IllegalArgumentException exception) {
                enchantment = null;
            }

            if (enchantment == null) {
                enchantment = Enchantment.getByName(enchantName.toUpperCase(Locale.ROOT));
            }

            if (enchantment == null) {
                plugin.getLogger().warning("VelioraKits: enchant tidak valid '" + rawEnchant + "' pada " + path + ". Enchant dilewati.");
                continue;
            }

            item.addUnsafeEnchantment(enchantment, level);
        }
    }

    private long parseCooldown(String input, String path) {
        if (input == null || input.isBlank()) {
            return 0L;
        }

        String value = input.trim().toLowerCase(Locale.ROOT);
        long multiplier = 1000L;

        try {
            if (value.endsWith("s")) {
                value = value.substring(0, value.length() - 1);
            } else if (value.endsWith("m")) {
                value = value.substring(0, value.length() - 1);
                multiplier = 60_000L;
            } else if (value.endsWith("h")) {
                value = value.substring(0, value.length() - 1);
                multiplier = 3_600_000L;
            } else if (value.endsWith("d")) {
                value = value.substring(0, value.length() - 1);
                multiplier = 86_400_000L;
            }

            return Math.max(0L, Long.parseLong(value) * multiplier);
        } catch (NumberFormatException exception) {
            plugin.getLogger().warning("VelioraKits: cooldown tidak valid pada " + path + ". Fallback ke 24h.");
            return 86_400_000L;
        }
    }

    private Material parseMaterial(String name, Material fallback, String path) {
        if (name == null || name.isBlank()) {
            return fallback;
        }

        Material material = Material.matchMaterial(name.toUpperCase(Locale.ROOT));
        if (material == null) {
            plugin.getLogger().warning("VelioraKits: material tidak valid '" + name + "' pada " + path + ". Item dilewati/fallback.");
            return fallback;
        }
        return material;
    }

    private String getString(String path, String fallback) {
        if (config == null || !config.contains(path)) {
            return fallback;
        }
        return config.getString(path, fallback);
    }

    private boolean getBoolean(String path, boolean fallback) {
        if (config == null || !config.contains(path)) {
            return fallback;
        }
        return config.getBoolean(path, fallback);
    }

    private int getInt(String path, int fallback) {
        if (config == null || !config.contains(path)) {
            return fallback;
        }
        return config.getInt(path, fallback);
    }

    private double getDouble(String path, double fallback) {
        if (config == null || !config.contains(path)) {
            return fallback;
        }
        return config.getDouble(path, fallback);
    }

    private String getMapString(Map<?, ?> map, String key, String fallback) {
        Object value = map.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    private int getMapInt(Map<?, ?> map, String key, int fallback) {
        Object value = map.get(key);
        if (value == null) return fallback;
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private List<String> getMapStringList(Map<?, ?> map, String key) {
        Object value = map.get(key);
        if (!(value instanceof List<?> list)) {
            return List.of();
        }

        List<String> result = new ArrayList<>();
        for (Object object : list) {
            result.add(String.valueOf(object));
        }
        return result;
    }
}
