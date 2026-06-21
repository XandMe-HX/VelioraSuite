package id.velioragardens.veliorasuite.module.trader;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.module.AbstractModule;
import id.velioragardens.veliorasuite.util.ColorUtil;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public final class TraderModule extends AbstractModule implements Listener, CommandExecutor, TabCompleter {
    private BukkitTask spawnTask;
    private BukkitTask despawnTask;
    private UUID activeTrader;
    private String activeLocationId;
    private File dataFile;
    private FileConfiguration data;
    private final Random random = new Random();

    public TraderModule(VelioraSuite plugin) {
        super(plugin, "trader", "trader");
    }

    @Override
    protected void onEnable() {
        loadData();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        PluginCommand command = plugin.getCommand("vtrader");
        if (command != null) {
            command.setExecutor(this);
            command.setTabCompleter(this);
        }
        startAutoSpawn();
        plugin.getLogger().info("VelioraTrader module started.");
    }

    @Override
    protected void onDisable() {
        HandlerList.unregisterAll(this);
        if (spawnTask != null) spawnTask.cancel();
        if (despawnTask != null) despawnTask.cancel();
        despawnTrader(false);
        saveData();
        plugin.getLogger().info("VelioraTrader module stopped.");
    }

    private void loadData() {
        File folder = new File(plugin.getDataFolder(), "data");
        if (!folder.exists()) folder.mkdirs();
        dataFile = new File(folder, "trader-locations.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);
        if (!data.isConfigurationSection("locations")) data.createSection("locations");
        saveData();
    }

    private void saveData() {
        try { if (data != null && dataFile != null) data.save(dataFile); }
        catch (IOException e) { plugin.getLogger().warning("Gagal save trader-locations.yml: " + e.getMessage()); }
    }

    private void startAutoSpawn() {
        if (!configFile.get().getBoolean("settings.auto-spawn", true)) return;
        long interval = Math.max(5, configFile.get().getLong("settings.spawn-interval-minutes", 60)) * 1200L;
        spawnTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> spawnRandom(false), interval, interval);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("open")) {
            if (!(sender instanceof Player player)) { sender.sendMessage("Only player."); return true; }
            if (!player.hasPermission("veliorasuite.trader.use")) { player.sendMessage(color(msg("no-permission", Map.of()))); return true; }
            open(player);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (!sender.hasPermission("veliorasuite.trader.admin")) {
            sender.sendMessage(color(msg("no-permission", Map.of())));
            return true;
        }
        switch (sub) {
            case "create", "set" -> {
                if (!(sender instanceof Player player)) { sender.sendMessage("Only player."); return true; }
                if (args.length < 3) { sender.sendMessage(color("&8【&aVelioraTrader&8】 &cGunakan: &f/vtr create <id> <name>")); return true; }
                String id = args[1].toLowerCase(Locale.ROOT);
                String name = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
                saveLocation(id, name, player.getLocation());
                sender.sendMessage(color(msg("location-saved", Map.of("id", id, "name", name))));
            }
            case "spawn" -> {
                if (args.length >= 2) spawnAt(args[1], true); else spawnRandom(true);
            }
            case "despawn" -> despawnTrader(true);
            case "reload" -> {
                configFile.reload();
                loadData();
                if (spawnTask != null) spawnTask.cancel();
                startAutoSpawn();
                sender.sendMessage(color(msg("reload", Map.of())));
            }
            case "list" -> sendLocations(sender);
            default -> sender.sendMessage(color("&8【&aVelioraTrader&8】 &f/vtr open, create <id> <name>, spawn [id], despawn, list, reload"));
        }
        return true;
    }

    private void saveLocation(String id, String name, Location location) {
        String path = "locations." + id;
        data.set(path + ".name", name);
        data.set(path + ".world", location.getWorld().getName());
        data.set(path + ".x", location.getX());
        data.set(path + ".y", location.getY());
        data.set(path + ".z", location.getZ());
        data.set(path + ".yaw", location.getYaw());
        data.set(path + ".pitch", location.getPitch());
        saveData();
    }

    private void sendLocations(CommandSender sender) {
        sender.sendMessage(color("&8&m------------------------------"));
        sender.sendMessage(color("&aVelioraTrader Locations"));
        ConfigurationSection locations = data.getConfigurationSection("locations");
        if (locations == null || locations.getKeys(false).isEmpty()) sender.sendMessage(color("&7Belum ada lokasi."));
        else for (String id : locations.getKeys(false)) sender.sendMessage(color("&7- &f" + id + " &8| &a" + data.getString("locations." + id + ".name", id)));
        sender.sendMessage(color("&8&m------------------------------"));
    }

    private void spawnRandom(boolean manual) {
        ConfigurationSection locations = data.getConfigurationSection("locations");
        if (locations == null || locations.getKeys(false).isEmpty()) {
            if (manual) Bukkit.broadcastMessage(color(msg("no-location", Map.of())));
            return;
        }
        List<String> ids = new ArrayList<>(locations.getKeys(false));
        spawnAt(ids.get(random.nextInt(ids.size())), true);
    }

    private void spawnAt(String id, boolean broadcast) {
        Location location = loadLocation(id);
        if (location == null) return;
        despawnTrader(false);
        Villager trader = (Villager) location.getWorld().spawnEntity(location, EntityType.VILLAGER);
        trader.setAI(false);
        trader.setInvulnerable(true);
        trader.setRemoveWhenFarAway(false);
        trader.setCustomNameVisible(true);
        trader.setCustomName(color(configFile.get().getString("settings.npc-name", "&6Veliora Trader")));
        trader.addScoreboardTag("veliora_trader");
        trader.addScoreboardTag("veliora_protected");
        activeTrader = trader.getUniqueId();
        activeLocationId = id;
        int despawnSeconds = configFile.get().getInt("settings.despawn-seconds", 900);
        if (despawnTask != null) despawnTask.cancel();
        despawnTask = Bukkit.getScheduler().runTaskLater(plugin, () -> despawnTrader(true), Math.max(20L, despawnSeconds * 20L));
        if (broadcast) Bukkit.broadcastMessage(color(msg("spawn", Map.of("location", data.getString("locations." + id + ".name", id)))));
    }

    private Location loadLocation(String id) {
        String path = "locations." + id;
        if (!data.isConfigurationSection(path)) return null;
        World world = Bukkit.getWorld(data.getString(path + ".world", "world"));
        if (world == null) return null;
        return new Location(world, data.getDouble(path + ".x"), data.getDouble(path + ".y"), data.getDouble(path + ".z"), (float) data.getDouble(path + ".yaw"), (float) data.getDouble(path + ".pitch"));
    }

    private void despawnTrader(boolean broadcast) {
        if (activeTrader != null) {
            Entity entity = Bukkit.getEntity(activeTrader);
            if (entity != null) entity.remove();
            activeTrader = null;
        }
        activeLocationId = null;
        if (despawnTask != null) { despawnTask.cancel(); despawnTask = null; }
        if (broadcast) Bukkit.broadcastMessage(color(msg("despawn", Map.of())));
    }

    private void open(Player player) {
        int size = Math.max(9, Math.min(54, configFile.get().getInt("gui.size", 27)));
        Inventory inv = Bukkit.createInventory(new TraderGuiHolder(), size, color(configFile.get().getString("gui.title", "&8Veliora Trader")));
        ConfigurationSection items = configFile.get().getConfigurationSection("items");
        if (items != null) for (String id : items.getKeys(false)) {
            String path = "items." + id;
            if (!configFile.get().getBoolean(path + ".enabled", true)) continue;
            Material material = Material.matchMaterial(configFile.get().getString(path + ".material", "EMERALD"));
            if (material == null) material = Material.EMERALD;
            ItemStack stack = new ItemStack(material, Math.max(1, configFile.get().getInt(path + ".amount", 1)));
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(color(configFile.get().getString(path + ".name", "&a" + id)));
                List<String> lore = new ArrayList<>();
                for (String line : configFile.get().getStringList(path + ".lore")) lore.add(color(line.replace("%price%", String.valueOf(configFile.get().getInt(path + ".price", 0)))));
                lore.add(color("&7Harga: &e" + configFile.get().getInt(path + ".price", 0) + " coin"));
                lore.add(color("&8ID: " + id));
                meta.setLore(lore);
                stack.setItemMeta(meta);
            }
            int slot = configFile.get().getInt(path + ".slot", 0);
            if (slot >= 0 && slot < inv.getSize()) inv.setItem(slot, stack);
        }
        player.openInventory(inv);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (activeTrader == null || !event.getRightClicked().getUniqueId().equals(activeTrader)) return;
        event.setCancelled(true);
        open(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof TraderGuiHolder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        String id = findItemBySlot(event.getSlot());
        if (id == null) return;
        String path = "items." + id;
        int price = configFile.get().getInt(path + ".price", 0);
        Economy eco = plugin.getHookManager().getEconomy();
        if (price > 0) {
            if (eco == null) { player.sendMessage(color(msg("economy-not-ready", Map.of()))); return; }
            if (eco.getBalance(player) < price) { player.sendMessage(color(msg("not-enough-money", Map.of("price", String.valueOf(price))))); return; }
            eco.withdrawPlayer(player, price);
        }
        ItemStack buy = clicked.clone();
        if (buy.hasItemMeta() && buy.getItemMeta().getLore() != null) {
            ItemMeta meta = buy.getItemMeta();
            meta.setLore(null);
            buy.setItemMeta(meta);
        }
        player.getInventory().addItem(buy).values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        player.sendMessage(color(msg("bought", Map.of("item", id, "price", String.valueOf(price)))));
    }

    private String findItemBySlot(int slot) {
        ConfigurationSection items = configFile.get().getConfigurationSection("items");
        if (items == null) return null;
        for (String id : items.getKeys(false)) if (configFile.get().getInt("items." + id + ".slot", -1) == slot) return id;
        return null;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("open", "create", "spawn", "despawn", "list", "reload").stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        if (args.length == 2 && args[0].equalsIgnoreCase("spawn")) {
            ConfigurationSection locations = data.getConfigurationSection("locations");
            return locations == null ? List.of() : new ArrayList<>(locations.getKeys(false));
        }
        return List.of();
    }

    private String color(String s) { return ColorUtil.color(s); }
    private String msg(String key, Map<String,String> vars) { String s = configFile.get().getString("messages." + key, "&8【&aVelioraTrader&8】 &cMessage not found: " + key); for (var e : vars.entrySet()) s = s.replace("%" + e.getKey() + "%", e.getValue()); return s; }
}
