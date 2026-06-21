package id.velioragardens.veliorasuite.module.fishing;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.config.ConfigFile;
import id.velioragardens.veliorasuite.util.ColorUtil;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public final class FishingManager {
    private final VelioraSuite plugin;
    private final ConfigFile configFile;
    private final Random random = new Random();
    private final NamespacedKey priceKey;
    private final NamespacedKey rarityKey;
    private final Map<UUID, MinigameSession> sessions = new HashMap<>();
    private File dataFile;
    private FileConfiguration data;

    public FishingManager(VelioraSuite plugin, ConfigFile configFile) {
        this.plugin = plugin;
        this.configFile = configFile;
        this.priceKey = new NamespacedKey(plugin, "fish_price");
        this.rarityKey = new NamespacedKey(plugin, "fish_rarity");
    }

    public void load() {
        File folder = new File(plugin.getDataFolder(), "data");
        if (!folder.exists()) folder.mkdirs();
        dataFile = new File(folder, "fishing-stats.yml");
        if (!dataFile.exists()) {
            try { dataFile.createNewFile(); } catch (IOException e) { plugin.getLogger().warning(e.getMessage()); }
        }
        data = YamlConfiguration.loadConfiguration(dataFile);
        if (!data.isConfigurationSection("players")) data.createSection("players");
        save();
    }

    public void save() {
        try { if (data != null && dataFile != null) data.save(dataFile); }
        catch (IOException e) { plugin.getLogger().warning("Gagal simpan fishing-stats.yml: " + e.getMessage()); }
    }

    public void reload() {
        configFile.reload();
        load();
    }

    public void shutdown() {
        for (MinigameSession session : new ArrayList<>(sessions.values())) session.cancel(false);
        sessions.clear();
        save();
    }

    public void handleCaughtFish(Player player) {
        CatchResult result = rollCatch(player);
        if (!requiresMinigame(result.rarity())) {
            giveCatch(player, result);
            return;
        }
        startMinigame(player, result);
    }

    private boolean requiresMinigame(String rarity) {
        if (!configFile.get().getBoolean("minigame.enabled", true)) return false;
        List<String> list = configFile.get().getStringList("minigame.rarities");
        return list.stream().anyMatch(s -> s.equalsIgnoreCase(rarity));
    }

    public boolean isInMinigame(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    public void processClick(Player player) {
        MinigameSession session = sessions.get(player.getUniqueId());
        if (session != null) session.click();
    }

    private void startMinigame(Player player, CatchResult result) {
        if (sessions.containsKey(player.getUniqueId())) return;
        int barLength = Math.max(10, configFile.get().getInt("minigame.bar-length", 20));
        int targetWidth = Math.max(1, configFile.get().getInt("minigame.target-width." + result.rarity().toLowerCase(Locale.ROOT), 4));
        int timeSeconds = Math.max(2, configFile.get().getInt("minigame.time-seconds." + result.rarity().toLowerCase(Locale.ROOT), 6));
        MinigameSession session = new MinigameSession(player, result, barLength, Math.min(targetWidth, barLength), timeSeconds);
        sessions.put(player.getUniqueId(), session);
        player.sendMessage(msg("minigame-start").replace("%rarity%", result.rarity()).replace("%fish%", result.name()));
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
        session.task = Bukkit.getScheduler().runTaskTimer(plugin, session::tick, 0L, 2L);
    }

    private void finishMinigame(Player player, boolean success, CatchResult result) {
        MinigameSession session = sessions.remove(player.getUniqueId());
        if (session != null && session.task != null) session.task.cancel();
        if (success) {
            player.sendMessage(msg("minigame-success").replace("%fish%", result.name()).replace("%rarity%", result.rarity()));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.5f);
            giveCatch(player, result);
        } else {
            player.sendMessage(msg("minigame-fail"));
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 0.8f);
        }
    }

    private CatchResult rollCatch(Player player) {
        String rarity = chooseRarity();
        List<Map<?, ?>> catches = configFile.get().getMapList("catches." + rarity.toLowerCase(Locale.ROOT));
        if (catches.isEmpty()) catches = configFile.get().getMapList("catches.vanilla");
        Map<?, ?> map = catches.get(random.nextInt(catches.size()));
        return createCatchResult(player, rarity, map);
    }

    private CatchResult createCatchResult(Player player, String rarity, Map<?, ?> map) {
        Material material = Material.matchMaterial(String.valueOf(value(map, "material", "COD")));
        if (material == null) material = Material.COD;
        String name = String.valueOf(value(map, "name", "Unknown Fish"));
        String origin = String.valueOf(value(map, "origin", "Unknown"));
        double weight = randomDouble(toDouble(value(map, "weight-min", 1)), toDouble(value(map, "weight-max", 5)));
        int price = randomInt(toInt(value(map, "price-min", 10)), toInt(value(map, "price-max", 100)));
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ColorUtil.color(color(rarity) + name));
            List<String> lore = new ArrayList<>();
            for (String line : configFile.get().getStringList("item-lore")) {
                lore.add(ColorUtil.color(line
                        .replace("%rarity%", rarity)
                        .replace("%origin%", origin)
                        .replace("%weight%", String.format(Locale.US, "%.1f", weight))
                        .replace("%price%", String.valueOf(price))
                        .replace("%player%", player.getName())));
            }
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(priceKey, PersistentDataType.INTEGER, price);
            meta.getPersistentDataContainer().set(rarityKey, PersistentDataType.STRING, rarity);
            item.setItemMeta(meta);
        }
        return new CatchResult(item, rarity.toUpperCase(Locale.ROOT), name, price);
    }

    private void giveCatch(Player player, CatchResult result) {
        player.getInventory().addItem(result.item()).values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        addStats(player, result.rarity(), result.price());
        broadcast(player, result.rarity(), result.name());
    }

    private void addStats(Player player, String rarity, int score) {
        String path = "players." + player.getUniqueId();
        data.set(path + ".name", player.getName());
        data.set(path + ".catches", data.getInt(path + ".catches", 0) + 1);
        data.set(path + ".score", data.getInt(path + ".score", 0) + score);
        data.set(path + ".rarity." + rarity.toLowerCase(Locale.ROOT), data.getInt(path + ".rarity." + rarity.toLowerCase(Locale.ROOT), 0) + 1);
        save();
    }

    public void openSellGui(Player player) {
        int size = Math.max(9, Math.min(54, configFile.get().getInt("sell-gui.size", 54)));
        Inventory inventory = Bukkit.createInventory(new FishingSellHolder(), size, ColorUtil.color(configFile.get().getString("sell-gui.title", "&8Jual Ikan Veliora")));
        player.openInventory(inventory);
    }

    public void handleSellClose(Player player, Inventory inventory) {
        Economy economy = plugin.getHookManager().getEconomy();
        if (economy == null) {
            player.sendMessage(msg("no-economy"));
            returnItems(player, inventory.getContents());
            return;
        }
        int total = 0;
        List<ItemStack> returns = new ArrayList<>();
        for (ItemStack item : inventory.getContents()) {
            if (item == null || item.getType().isAir()) continue;
            Integer price = getFishPrice(item);
            if (price == null) returns.add(item);
            else total += price * item.getAmount();
        }
        if (total > 0) economy.depositPlayer(player, total);
        returnItems(player, returns.toArray(new ItemStack[0]));
        player.sendMessage(msg("sell-success").replace("%price%", String.valueOf(total)));
    }

    private void returnItems(Player player, ItemStack[] items) {
        for (ItemStack item : items) {
            if (item == null || item.getType().isAir()) continue;
            player.getInventory().addItem(item).values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        }
    }

    public Integer getFishPrice(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(priceKey, PersistentDataType.INTEGER);
    }

    public boolean sellHand(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        Integer price = getFishPrice(item);
        if (price == null) {
            player.sendMessage(msg("not-fish"));
            return false;
        }
        Economy economy = plugin.getHookManager().getEconomy();
        if (economy == null) {
            player.sendMessage(msg("no-economy"));
            return false;
        }
        int total = price * item.getAmount();
        economy.depositPlayer(player, total);
        player.getInventory().setItemInMainHand(null);
        player.sendMessage(msg("sell-success").replace("%price%", String.valueOf(total)));
        return true;
    }

    public void sendStats(Player player) {
        String path = "players." + player.getUniqueId();
        player.sendMessage(ColorUtil.color("&8&m------------------------------"));
        player.sendMessage(ColorUtil.color("&aVelioraFishing Stats"));
        player.sendMessage(ColorUtil.color("&7Total Catch: &f" + data.getInt(path + ".catches", 0)));
        player.sendMessage(ColorUtil.color("&7Score: &f" + data.getInt(path + ".score", 0)));
        player.sendMessage(ColorUtil.color("&8&m------------------------------"));
    }

    public void sendTop(Player player) {
        ConfigurationSection section = data.getConfigurationSection("players");
        player.sendMessage(ColorUtil.color("&8&m------------------------------"));
        player.sendMessage(ColorUtil.color("&aTop Angler"));
        if (section == null) {
            player.sendMessage(ColorUtil.color("&7Belum ada data."));
            return;
        }
        List<String> ids = new ArrayList<>(section.getKeys(false));
        ids.sort(Comparator.comparingInt(id -> -data.getInt("players." + id + ".score", 0)));
        int rank = 1;
        for (String id : ids) {
            if (rank > 10) break;
            player.sendMessage(ColorUtil.color("&e#" + rank + " &f" + data.getString("players." + id + ".name", "Unknown") + " &7- &a" + data.getInt("players." + id + ".score", 0)));
            rank++;
        }
        player.sendMessage(ColorUtil.color("&8&m------------------------------"));
    }

    private String chooseRarity() {
        double total = 0;
        ConfigurationSection section = configFile.get().getConfigurationSection("rarity-chance");
        if (section == null) return "vanilla";
        for (String key : section.getKeys(false)) total += section.getDouble(key);
        double roll = random.nextDouble() * total;
        double current = 0;
        for (String key : section.getKeys(false)) {
            current += section.getDouble(key);
            if (roll <= current) return key.toUpperCase(Locale.ROOT);
        }
        return "VANILLA";
    }

    private void broadcast(Player player, String rarity, String name) {
        List<String> broadcast = configFile.get().getStringList("broadcast-rarities");
        if (!broadcast.contains(rarity.toUpperCase(Locale.ROOT))) return;
        String message = msg("broadcast").replace("%player%", player.getName()).replace("%rarity%", rarity).replace("%fish%", name);
        Bukkit.broadcastMessage(message);
    }

    private String color(String rarity) {
        return switch (rarity.toUpperCase(Locale.ROOT)) {
            case "SECRET" -> "&d&l";
            case "MYTHIC" -> "&5&l";
            case "LEGEND" -> "&6&l";
            case "EPIC" -> "&b";
            case "COMMON" -> "&a";
            case "TRASH" -> "&7";
            default -> "&f";
        };
    }

    private Object value(Map<?, ?> map, String key, Object fallback) { return map.containsKey(key) ? map.get(key) : fallback; }
    private int randomInt(int min, int max) { return min >= max ? min : min + random.nextInt(max - min + 1); }
    private double randomDouble(double min, double max) { return min >= max ? min : min + (max - min) * random.nextDouble(); }
    private int toInt(Object object) { try { return Integer.parseInt(String.valueOf(object)); } catch (Exception e) { return 0; } }
    private double toDouble(Object object) { try { return Double.parseDouble(String.valueOf(object)); } catch (Exception e) { return 0; } }

    public String msg(String key) {
        return ColorUtil.color(configFile.get().getString("messages." + key, "&cMessage not found: " + key)
                .replace("%prefix%", configFile.get().getString("messages.prefix", "&8【&aVelioraFishing&8】")));
    }

    private record CatchResult(ItemStack item, String rarity, String name, int price) { }

    private final class MinigameSession {
        private final Player player;
        private final CatchResult result;
        private final int barLength;
        private final int targetStart;
        private final int targetEnd;
        private final long endAt;
        private int cursor;
        private int direction = 1;
        private BukkitTask task;

        private MinigameSession(Player player, CatchResult result, int barLength, int targetWidth, int timeSeconds) {
            this.player = player;
            this.result = result;
            this.barLength = barLength;
            this.targetStart = random.nextInt(Math.max(1, barLength - targetWidth + 1));
            this.targetEnd = targetStart + targetWidth - 1;
            this.endAt = System.currentTimeMillis() + (timeSeconds * 1000L);
        }

        private void tick() {
            if (!player.isOnline() || System.currentTimeMillis() > endAt) {
                cancel(true);
                return;
            }
            cursor += direction;
            if (cursor >= barLength - 1 || cursor <= 0) direction *= -1;
            sendBar();
        }

        private void click() {
            boolean success = cursor >= targetStart && cursor <= targetEnd;
            finishMinigame(player, success, result);
        }

        private void sendBar() {
            StringBuilder builder = new StringBuilder("&8[");
            for (int i = 0; i < barLength; i++) {
                if (i == cursor) builder.append("&f┃");
                else if (i >= targetStart && i <= targetEnd) builder.append("&e■");
                else builder.append("&7-");
            }
            builder.append("&8]");
            String action = configFile.get().getString("minigame.actionbar", "&aTarik ikan! &f%bar% &7Klik saat penanda di kuning!")
                    .replace("%bar%", builder.toString())
                    .replace("%fish%", result.name())
                    .replace("%rarity%", result.rarity());
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(ColorUtil.color(action)));
        }

        private void cancel(boolean failed) {
            if (task != null) task.cancel();
            sessions.remove(player.getUniqueId());
            if (failed) finishMinigame(player, false, result);
        }
    }
}
