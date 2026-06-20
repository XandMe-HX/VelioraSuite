package id.velioragardens.veliorasuite.module.kits;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.config.ConfigFile;
import id.velioragardens.veliorasuite.util.ColorUtil;
import id.velioragardens.veliorasuite.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class KitManager {
    private final VelioraSuite plugin;
    private final ConfigFile configFile;
    private File dataFile;
    private FileConfiguration data;
    private final NamespacedKey kitKey;

    public KitManager(VelioraSuite plugin, ConfigFile configFile) { this.plugin = plugin; this.configFile = configFile; this.kitKey = new NamespacedKey(plugin, "kit_id"); }

    public void load() {
        File folder = new File(plugin.getDataFolder(), "data"); if (!folder.exists()) folder.mkdirs();
        dataFile = new File(folder, "kits-data.yml"); if (!dataFile.exists()) { try { dataFile.createNewFile(); } catch (IOException e) { plugin.getLogger().warning(e.getMessage()); } }
        data = YamlConfiguration.loadConfiguration(dataFile); if (!data.isConfigurationSection("players")) data.createSection("players"); save();
    }
    public void save() { try { if (data != null && dataFile != null) data.save(dataFile); } catch (IOException e) { plugin.getLogger().warning("Gagal menyimpan kits-data.yml: " + e.getMessage()); } }
    public void reload() { configFile.reload(); load(); }

    public void openGui(Player player) {
        int size = configFile.get().getInt("gui.size", 27);
        String title = ColorUtil.color(configFile.get().getString("gui.title", "&8Veliora Kits"));
        Inventory inv = Bukkit.createInventory(null, size, title);
        ConfigurationSection kits = configFile.get().getConfigurationSection("kits");
        if (kits != null) {
            for (String id : kits.getKeys(false)) {
                if (!configFile.get().getBoolean("kits." + id + ".enabled", true)) continue;
                int slot = configFile.get().getInt("kits." + id + ".slot", 0);
                if (slot < 0 || slot >= size) continue;
                inv.setItem(slot, displayItem(player, id));
            }
        }
        player.openInventory(inv);
    }

    public String getKitFromItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(kitKey, PersistentDataType.STRING);
    }

    private ItemStack displayItem(Player player, String id) {
        boolean access = hasAccess(player, id);
        String path = "kits." + id;
        Material material = Material.matchMaterial(configFile.get().getString(path + (access ? ".display.material" : ".display.locked-material"), access ? "CHEST" : "BARRIER"));
        if (material == null) material = access ? Material.CHEST : Material.BARRIER;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ColorUtil.color(configFile.get().getString(path + ".display.name", "&a" + id)));
        List<String> lore = new ArrayList<>();
        for (String line : configFile.get().getStringList(path + ".display.lore")) lore.add(ColorUtil.color(line.replace("%status%", status(player, id))));
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(kitKey, PersistentDataType.STRING, id);
        item.setItemMeta(meta);
        return item;
    }

    public boolean claim(Player player, String id, boolean adminGive) {
        if (!configFile.get().isConfigurationSection("kits." + id)) { player.sendMessage(colorMsg("kit-not-found").replace("%kit%", id)); return false; }
        if (!adminGive && !hasAccess(player, id)) { player.sendMessage(colorMsg("no-permission-kit")); return false; }
        if (!adminGive && !canClaim(player, id)) { player.sendMessage(colorMsg("cooldown").replace("%time%", remaining(player, id))); return false; }
        for (ItemStack item : buildItems(id)) {
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
            for (ItemStack leftover : leftovers.values()) player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
        if (!adminGive) setClaim(player, id);
        player.sendMessage(colorMsg("claim-success").replace("%kit%", name(id)));
        return true;
    }

    public void reset(Player player, String id) { data.set("players." + player.getUniqueId() + "." + id, null); save(); }
    public List<String> kitIds() { ConfigurationSection s = configFile.get().getConfigurationSection("kits"); return s == null ? new ArrayList<>() : new ArrayList<>(s.getKeys(false)); }
    public ConfigFile getConfigFile() { return configFile; }
    public String title() { return ColorUtil.color(configFile.get().getString("gui.title", "&8Veliora Kits")); }

    private List<ItemStack> buildItems(String id) {
        List<ItemStack> items = new ArrayList<>();
        for (Map<?, ?> map : configFile.get().getMapList("kits." + id + ".items")) {
            String materialName = String.valueOf(map.containsKey("material") ? map.get("material") : "STONE");
            Material material = Material.matchMaterial(materialName);
            if (material == null) continue;
            int amount = Integer.parseInt(String.valueOf(map.containsKey("amount") ? map.get("amount") : "1"));
            ItemStack item = new ItemStack(material, amount);
            ItemMeta meta = item.getItemMeta();
            if (map.containsKey("name")) meta.setDisplayName(ColorUtil.color(String.valueOf(map.get("name"))));
            if (map.containsKey("lore") && map.get("lore") instanceof List<?> list) {
                List<String> lore = new ArrayList<>(); for (Object line : list) lore.add(ColorUtil.color(String.valueOf(line))); meta.setLore(lore);
            }
            item.setItemMeta(meta);
            if (map.containsKey("enchants") && map.get("enchants") instanceof List<?> list) {
                for (Object raw : list) applyEnchant(item, String.valueOf(raw));
            }
            items.add(item);
        }
        return items;
    }

    private void applyEnchant(ItemStack item, String raw) {
        String[] split = raw.split(":"); if (split.length < 2) return;
        String name = split[0].toUpperCase(Locale.ROOT);
        int level; try { level = Integer.parseInt(split[1]); } catch (NumberFormatException e) { return; }
        Enchantment enchant = Enchantment.getByName(name);
        if (enchant == null && name.equals("PROTECTION")) enchant = Enchantment.getByName("PROTECTION_ENVIRONMENTAL");
        if (enchant == null && name.equals("EFFICIENCY")) enchant = Enchantment.getByName("DIG_SPEED");
        if (enchant == null) return;
        item.addUnsafeEnchantment(enchant, level);
    }

    private boolean hasAccess(Player player, String id) { String perm = configFile.get().getString("kits." + id + ".permission", ""); return perm == null || perm.isBlank() || player.hasPermission(perm); }
    private boolean canClaim(Player player, String id) { long claim = data.getLong("players." + player.getUniqueId() + "." + id + ".last-claim", 0); String cd = configFile.get().getString("kits." + id + ".cooldown", "0"); long millis = TimeUtil.parseDurationToMillis(cd); if (millis == -1L) return claim <= 0; return claim <= 0 || System.currentTimeMillis() - claim >= millis; }
    private void setClaim(Player player, String id) { data.set("players." + player.getUniqueId() + "." + id + ".last-claim", System.currentTimeMillis()); save(); }
    private String remaining(Player player, String id) { long claim = data.getLong("players." + player.getUniqueId() + "." + id + ".last-claim", 0); long millis = TimeUtil.parseDurationToMillis(configFile.get().getString("kits." + id + ".cooldown", "0")); if (millis == -1L) return "sudah pernah claim"; long left = Math.max(0, (claim + millis - System.currentTimeMillis()) / 1000); return TimeUtil.formatSeconds(left); }
    private String status(Player player, String id) { if (!hasAccess(player, id)) return "&cTerkunci"; if (!canClaim(player, id)) return "&eCooldown: " + remaining(player, id); return "&aBisa claim"; }
    private String name(String id) { return ColorUtil.color(configFile.get().getString("kits." + id + ".display.name", id)); }
    private String colorMsg(String key) { return ColorUtil.color(configFile.get().getString("messages." + key, "&cMessage not found: " + key).replace("%prefix%", configFile.get().getString("messages.prefix", "&8【&aVelioraKits&8】"))); }
}
