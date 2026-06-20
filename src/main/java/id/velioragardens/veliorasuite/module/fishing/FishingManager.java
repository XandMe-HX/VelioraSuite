package id.velioragardens.veliorasuite.module.fishing;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.config.ConfigFile;
import id.velioragardens.veliorasuite.util.ColorUtil;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

public final class FishingManager {
    private final VelioraSuite plugin;
    private final ConfigFile configFile;
    private final Random random = new Random();
    private final NamespacedKey priceKey;
    private final NamespacedKey rarityKey;
    private File dataFile;
    private FileConfiguration data;

    public FishingManager(VelioraSuite plugin, ConfigFile configFile) { this.plugin = plugin; this.configFile = configFile; this.priceKey = new NamespacedKey(plugin, "fish_price"); this.rarityKey = new NamespacedKey(plugin, "fish_rarity"); }

    public void load() { File folder = new File(plugin.getDataFolder(), "data"); if (!folder.exists()) folder.mkdirs(); dataFile = new File(folder, "fishing-stats.yml"); if (!dataFile.exists()) { try { dataFile.createNewFile(); } catch (IOException e) { plugin.getLogger().warning(e.getMessage()); } } data = YamlConfiguration.loadConfiguration(dataFile); if (!data.isConfigurationSection("players")) data.createSection("players"); save(); }
    public void save() { try { if (data != null && dataFile != null) data.save(dataFile); } catch (IOException e) { plugin.getLogger().warning("Gagal simpan fishing-stats.yml: " + e.getMessage()); } }
    public void reload() { configFile.reload(); load(); }

    public ItemStack createCatch(Player player) {
        String rarity = chooseRarity();
        List<Map<?, ?>> catches = configFile.get().getMapList("catches." + rarity.toLowerCase(Locale.ROOT));
        if (catches.isEmpty()) catches = configFile.get().getMapList("catches.vanilla");
        Map<?, ?> map = catches.get(random.nextInt(catches.size()));
        Material material = Material.matchMaterial(String.valueOf(map.containsKey("material") ? map.get("material") : "COD"));
        if (material == null) material = Material.COD;
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        String name = String.valueOf(map.containsKey("name") ? map.get("name") : "Unknown Fish");
        String origin = String.valueOf(map.containsKey("origin") ? map.get("origin") : "Unknown");
        double weight = randomDouble(toDouble(map.containsKey("weight-min") ? map.get("weight-min") : 1), toDouble(map.containsKey("weight-max") ? map.get("weight-max") : 5));
        int price = randomInt(toInt(map.containsKey("price-min") ? map.get("price-min") : 10), toInt(map.containsKey("price-max") ? map.get("price-max") : 100));
        meta.setDisplayName(ColorUtil.color(color(rarity) + name));
        List<String> lore = new ArrayList<>();
        for (String line : configFile.get().getStringList("item-lore")) {
            lore.add(ColorUtil.color(line.replace("%rarity%", rarity).replace("%origin%", origin).replace("%weight%", String.format(Locale.US, "%.1f", weight)).replace("%price%", String.valueOf(price)).replace("%player%", player.getName())));
        }
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(priceKey, PersistentDataType.INTEGER, price);
        meta.getPersistentDataContainer().set(rarityKey, PersistentDataType.STRING, rarity);
        item.setItemMeta(meta);
        addStats(player, rarity, price);
        broadcast(player, rarity, name);
        return item;
    }

    private void addStats(Player player, String rarity, int score) { String path = "players." + player.getUniqueId(); data.set(path + ".name", player.getName()); data.set(path + ".catches", data.getInt(path + ".catches", 0) + 1); data.set(path + ".score", data.getInt(path + ".score", 0) + score); data.set(path + ".rarity." + rarity.toLowerCase(Locale.ROOT), data.getInt(path + ".rarity." + rarity.toLowerCase(Locale.ROOT), 0) + 1); save(); }
    public boolean sellHand(Player player) { ItemStack item = player.getInventory().getItemInMainHand(); if (item.getType().isAir() || !item.hasItemMeta()) { player.sendMessage(msg("not-fish")); return false; } Integer price = item.getItemMeta().getPersistentDataContainer().get(priceKey, PersistentDataType.INTEGER); if (price == null) { player.sendMessage(msg("not-fish")); return false; } Economy economy = plugin.getHookManager().getEconomy(); if (economy == null) { player.sendMessage(msg("no-economy")); return false; } economy.depositPlayer(player, price * item.getAmount()); player.getInventory().setItemInMainHand(null); player.sendMessage(msg("sell-success").replace("%price%", String.valueOf(price))); return true; }
    public void sendStats(Player player) { String path = "players." + player.getUniqueId(); player.sendMessage(ColorUtil.color("&8&m------------------------------")); player.sendMessage(ColorUtil.color("&aVelioraFishing Stats")); player.sendMessage(ColorUtil.color("&7Total Catch: &f" + data.getInt(path + ".catches", 0))); player.sendMessage(ColorUtil.color("&7Score: &f" + data.getInt(path + ".score", 0))); player.sendMessage(ColorUtil.color("&8&m------------------------------")); }
    public void sendTop(Player player) { ConfigurationSection section = data.getConfigurationSection("players"); player.sendMessage(ColorUtil.color("&8&m------------------------------")); player.sendMessage(ColorUtil.color("&aTop Angler")); if (section == null) { player.sendMessage(ColorUtil.color("&7Belum ada data.")); return; } List<String> ids = new ArrayList<>(section.getKeys(false)); ids.sort(Comparator.comparingInt(id -> -data.getInt("players." + id + ".score", 0))); int rank = 1; for (String id : ids) { if (rank > 10) break; player.sendMessage(ColorUtil.color("&e#" + rank + " &f" + data.getString("players." + id + ".name", "Unknown") + " &7- &a" + data.getInt("players." + id + ".score", 0))); rank++; } player.sendMessage(ColorUtil.color("&8&m------------------------------")); }

    private String chooseRarity() { double total = 0; ConfigurationSection section = configFile.get().getConfigurationSection("rarity-chance"); if (section == null) return "vanilla"; for (String key : section.getKeys(false)) total += section.getDouble(key); double roll = random.nextDouble() * total; double current = 0; for (String key : section.getKeys(false)) { current += section.getDouble(key); if (roll <= current) return key.toUpperCase(Locale.ROOT); } return "VANILLA"; }
    private void broadcast(Player player, String rarity, String name) { List<String> broadcast = configFile.get().getStringList("broadcast-rarities"); if (!broadcast.contains(rarity.toUpperCase(Locale.ROOT))) return; String message = msg("broadcast").replace("%player%", player.getName()).replace("%rarity%", rarity).replace("%fish%", name); Bukkit.broadcastMessage(message); }
    private String color(String rarity) { return switch (rarity.toUpperCase(Locale.ROOT)) { case "SECRET" -> "&d&l"; case "MYTHIC" -> "&5&l"; case "LEGEND" -> "&6&l"; case "EPIC" -> "&b"; case "COMMON" -> "&a"; case "TRASH" -> "&7"; default -> "&f"; }; }
    private int randomInt(int min, int max) { return min >= max ? min : min + random.nextInt(max - min + 1); }
    private double randomDouble(double min, double max) { return min >= max ? min : min + (max - min) * random.nextDouble(); }
    private int toInt(Object o) { try { return Integer.parseInt(String.valueOf(o)); } catch (Exception e) { return 0; } }
    private double toDouble(Object o) { try { return Double.parseDouble(String.valueOf(o)); } catch (Exception e) { return 0; } }
    public String msg(String key) { return ColorUtil.color(configFile.get().getString("messages." + key, "&cMessage not found: " + key).replace("%prefix%", configFile.get().getString("messages.prefix", "&8【&aVelioraFishing&8】"))); }
}
