package id.velioragardens.veliorasuite.module.boss;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.module.AbstractModule;
import id.velioragardens.veliorasuite.util.ColorUtil;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
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
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

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

public final class BossModule extends AbstractModule implements Listener, CommandExecutor, TabCompleter {
    private UUID activeBoss;
    private String activeBossId;
    private String activeRarity;
    private BossBar bossBar;
    private BukkitTask updateTask;
    private BukkitTask skillTask;
    private BukkitTask autoTask;
    private final Map<UUID, Double> damage = new HashMap<>();
    private final Random random = new Random();
    private File dataFile;
    private FileConfiguration data;

    public BossModule(VelioraSuite plugin) {
        super(plugin, "boss", "boss");
    }

    @Override
    protected void onEnable() {
        loadData();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        PluginCommand command = plugin.getCommand("vboss");
        if (command != null) {
            command.setExecutor(this);
            command.setTabCompleter(this);
        }
        startAutoSpawn();
        plugin.getLogger().info("VelioraBoss module started.");
    }

    @Override
    protected void onDisable() {
        HandlerList.unregisterAll(this);
        stopTasks();
        if (bossBar != null) bossBar.removeAll();
        Entity entity = activeBoss == null ? null : Bukkit.getEntity(activeBoss);
        if (entity != null) entity.remove();
        saveData();
        plugin.getLogger().info("VelioraBoss module stopped.");
    }

    private void loadData() {
        File folder = new File(plugin.getDataFolder(), "data");
        if (!folder.exists()) folder.mkdirs();
        dataFile = new File(folder, "boss-locations.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);
        if (!data.isConfigurationSection("locations")) data.createSection("locations");
        saveData();
    }

    private void saveData() {
        try { if (data != null && dataFile != null) data.save(dataFile); }
        catch (IOException e) { plugin.getLogger().warning("Gagal save boss-locations.yml: " + e.getMessage()); }
    }

    private void startAutoSpawn() {
        if (!configFile.get().getBoolean("settings.auto-spawn", false)) return;
        long interval = Math.max(10, configFile.get().getLong("settings.spawn-interval-minutes", 120)) * 1200L;
        autoTask = Bukkit.getScheduler().runTaskTimer(plugin, this::spawnRandomAtRandomLocation, interval, interval);
    }

    private void stopTasks() {
        if (updateTask != null) updateTask.cancel();
        if (skillTask != null) skillTask.cancel();
        if (autoTask != null) autoTask.cancel();
        updateTask = null;
        skillTask = null;
        autoTask = null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("veliorasuite.boss.admin")) {
            sender.sendMessage(color(msg("no-permission", Map.of())));
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            sender.sendMessage(color("&8【&aVelioraBoss&8】 &fActive: &a" + (activeBoss != null ? activeBossId + " &7(" + activeRarity + ")" : "none")));
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> {
                configFile.reload();
                loadData();
                if (autoTask != null) autoTask.cancel();
                startAutoSpawn();
                sender.sendMessage(color(msg("reload", Map.of())));
            }
            case "spawn" -> {
                if (!(sender instanceof Player player)) { sender.sendMessage("Only player can spawn boss here."); return true; }
                String id = args.length >= 2 ? args[1] : chooseBossId();
                spawn(player.getLocation(), id, true);
            }
            case "set" -> {
                if (!(sender instanceof Player player)) { sender.sendMessage("Only player."); return true; }
                if (args.length < 2) { sender.sendMessage(color("&8【&aVelioraBoss&8】 &cGunakan: &f/vboss set <nama>")); return true; }
                saveLocation(args[1].toLowerCase(Locale.ROOT), player.getLocation());
                sender.sendMessage(color(msg("location-saved", Map.of("id", args[1].toLowerCase(Locale.ROOT)))));
            }
            case "delete" -> {
                if (args.length < 2) { sender.sendMessage(color("&8【&aVelioraBoss&8】 &cGunakan: &f/vboss delete <nama>")); return true; }
                data.set("locations." + args[1].toLowerCase(Locale.ROOT), null);
                saveData();
                sender.sendMessage(color(msg("location-deleted", Map.of("id", args[1].toLowerCase(Locale.ROOT)))));
            }
            case "list" -> sendLocations(sender);
            case "kill", "despawn" -> {
                killActive(false);
                sender.sendMessage(color("&8【&aVelioraBoss&8】 &cActive boss removed."));
            }
            default -> sender.sendMessage(color("&8【&aVelioraBoss&8】 &f/vboss spawn [id], set <nama>, delete <nama>, list, status, kill, reload"));
        }
        return true;
    }

    private void saveLocation(String id, Location location) {
        String path = "locations." + id;
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
        sender.sendMessage(color("&aVelioraBoss Locations"));
        ConfigurationSection locations = data.getConfigurationSection("locations");
        if (locations == null || locations.getKeys(false).isEmpty()) sender.sendMessage(color("&7Belum ada lokasi."));
        else for (String id : locations.getKeys(false)) sender.sendMessage(color("&7- &f" + id));
        sender.sendMessage(color("&8&m------------------------------"));
    }

    private void spawnRandomAtRandomLocation() {
        ConfigurationSection locations = data.getConfigurationSection("locations");
        if (locations == null || locations.getKeys(false).isEmpty()) return;
        List<String> ids = new ArrayList<>(locations.getKeys(false));
        Location location = loadLocation(ids.get(random.nextInt(ids.size())));
        if (location != null) spawn(location, chooseBossId(), true);
    }

    private Location loadLocation(String id) {
        String path = "locations." + id;
        World world = Bukkit.getWorld(data.getString(path + ".world", "world"));
        if (world == null) return null;
        return new Location(world, data.getDouble(path + ".x"), data.getDouble(path + ".y"), data.getDouble(path + ".z"), (float) data.getDouble(path + ".yaw"), (float) data.getDouble(path + ".pitch"));
    }

    private String chooseBossId() {
        ConfigurationSection bosses = configFile.get().getConfigurationSection("bosses");
        if (bosses == null || bosses.getKeys(false).isEmpty()) return configFile.get().getString("settings.default-boss", "ancient_guardian");
        List<String> pool = new ArrayList<>();
        for (String id : bosses.getKeys(false)) {
            int chance = Math.max(1, configFile.get().getInt("bosses." + id + ".chance", 1));
            for (int i = 0; i < chance; i++) pool.add(id);
        }
        return pool.get(random.nextInt(pool.size()));
    }

    private String chooseRarity() {
        ConfigurationSection section = configFile.get().getConfigurationSection("rarities");
        if (section == null) return "COMMON";
        double total = 0;
        for (String key : section.getKeys(false)) total += section.getDouble(key + ".chance", 0);
        double roll = random.nextDouble() * Math.max(1, total);
        double current = 0;
        for (String key : section.getKeys(false)) {
            current += section.getDouble(key + ".chance", 0);
            if (roll <= current) return key.toUpperCase(Locale.ROOT);
        }
        return "COMMON";
    }

    private void spawn(Location location, String id, boolean broadcast) {
        if (activeBoss != null) killActive(false);
        String path = "bosses." + id;
        EntityType type;
        try { type = EntityType.valueOf(configFile.get().getString(path + ".type", "ZOMBIE").toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException exception) { type = EntityType.ZOMBIE; }
        Entity entity = location.getWorld().spawnEntity(location, type);
        if (!(entity instanceof LivingEntity living)) return;
        String rarity = chooseRarity();
        double multiplier = configFile.get().getDouble("rarities." + rarity.toLowerCase(Locale.ROOT) + ".multiplier", 1.0);
        String rarityName = configFile.get().getString("rarities." + rarity.toLowerCase(Locale.ROOT) + ".display", rarity);
        String rawName = configFile.get().getString(path + ".name", "&cVeliora Boss");
        String name = color(rarityName + " &7- " + rawName);
        double health = configFile.get().getDouble(path + ".health", 300) * multiplier;
        living.setCustomName(name);
        living.setCustomNameVisible(true);
        living.addScoreboardTag("veliora_boss");
        living.addScoreboardTag("veliora_protected");
        living.setRemoveWhenFarAway(false);
        try { living.setMaxHealth(health); } catch (Throwable ignored) { }
        living.setHealth(Math.min(health, living.getMaxHealth()));
        activeBoss = living.getUniqueId();
        activeBossId = id;
        activeRarity = rarity;
        damage.clear();
        bossBar = Bukkit.createBossBar(name, BarColor.RED, BarStyle.SEGMENTED_10);
        bossBar.setProgress(1.0);
        Bukkit.getOnlinePlayers().forEach(bossBar::addPlayer);
        updateTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> updateBar(living), 20L, 20L);
        skillTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> useRandomSkill(living), 80L, Math.max(20L, configFile.get().getLong("settings.skill-interval-ticks", 120L)));
        if (broadcast) Bukkit.broadcastMessage(color(msg("spawn", Map.of("boss", strip(name), "rarity", rarity))));
    }

    private void updateBar(LivingEntity living) {
        if (living.isDead() || !living.isValid()) { finishBoss(null); return; }
        double max = living.getMaxHealth();
        double progress = Math.max(0.0, Math.min(1.0, living.getHealth() / max));
        if (bossBar != null) {
            bossBar.setProgress(progress);
            Bukkit.getOnlinePlayers().forEach(player -> { if (!bossBar.getPlayers().contains(player)) bossBar.addPlayer(player); });
        }
    }

    private void useRandomSkill(LivingEntity boss) {
        if (activeBoss == null || boss.isDead() || !boss.isValid()) return;
        List<String> skills = configFile.get().getStringList("skills.enabled");
        if (skills.isEmpty()) skills = List.of("stomp", "knockback", "poison_nova");
        String skill = skills.get(random.nextInt(skills.size())).toLowerCase(Locale.ROOT);
        double radius = configFile.get().getDouble("skills.radius", 6.0);
        for (Player player : boss.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(boss.getLocation()) > radius * radius) continue;
            switch (skill) {
                case "stomp" -> player.damage(configFile.get().getDouble("skills.stomp-damage", 4.0), boss);
                case "knockback" -> {
                    Vector vector = player.getLocation().toVector().subtract(boss.getLocation().toVector()).normalize().multiply(configFile.get().getDouble("skills.knockback-power", 1.2));
                    vector.setY(0.6);
                    player.setVelocity(vector);
                }
                case "poison_nova" -> player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 100, 0));
                default -> { }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (activeBoss == null || !event.getEntity().getUniqueId().equals(activeBoss)) return;
        Player player = event.getDamager() instanceof Player p ? p : null;
        if (player == null) return;
        damage.put(player.getUniqueId(), damage.getOrDefault(player.getUniqueId(), 0.0) + event.getFinalDamage());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBossAttack(EntityDamageByEntityEvent event) {
        if (activeBoss == null || !event.getDamager().getUniqueId().equals(activeBoss)) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (!configFile.get().getBoolean("settings.true-damage.enabled", true)) return;
        double damage = configFile.get().getDouble("settings.true-damage.amount", 2.0);
        event.setCancelled(true);
        player.damage(0.01);
        player.setHealth(Math.max(0.0, player.getHealth() - damage));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent event) {
        if (activeBoss != null && event.getEntity().getUniqueId().equals(activeBoss)) finishBoss(event.getEntity().getKiller());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGenericDamage(EntityDamageEvent event) {
        if (activeBoss != null && event.getEntity().getUniqueId().equals(activeBoss) && event.getEntity() instanceof LivingEntity living) updateBar(living);
    }

    private void finishBoss(Player killer) {
        if (activeBoss == null) return;
        String id = activeBossId == null ? "boss" : activeBossId;
        Economy economy = plugin.getHookManager().getEconomy();
        if (killer != null) {
            double lastHit = configFile.get().getDouble("bosses." + id + ".rewards.last-hit-money", 1000) * rarityMultiplier();
            if (economy != null && lastHit > 0) economy.depositPlayer(killer, lastHit);
            killer.sendMessage(color(msg("last-hit-reward", Map.of("money", String.valueOf((int) lastHit)))));
        }
        damage.entrySet().stream().sorted(Map.Entry.<UUID, Double>comparingByValue(Comparator.reverseOrder())).limit(3).forEach(entry -> {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                double money = configFile.get().getDouble("bosses." + id + ".rewards.top-damage-money", 500) * rarityMultiplier();
                if (economy != null && money > 0) economy.depositPlayer(player, money);
                player.sendMessage(color(msg("top-damage-reward", Map.of("money", String.valueOf((int) money)))));
            }
        });
        Bukkit.broadcastMessage(color(msg("defeated", Map.of("boss", id))));
        clearActiveState();
    }

    private double rarityMultiplier() {
        return configFile.get().getDouble("rarities." + (activeRarity == null ? "common" : activeRarity.toLowerCase(Locale.ROOT)) + ".multiplier", 1.0);
    }

    private void killActive(boolean reward) {
        if (activeBoss != null) {
            Entity entity = Bukkit.getEntity(activeBoss);
            if (entity != null) entity.remove();
            if (reward) finishBoss(null); else clearActiveState();
        }
    }

    private void clearActiveState() {
        if (bossBar != null) { bossBar.removeAll(); bossBar = null; }
        if (updateTask != null) { updateTask.cancel(); updateTask = null; }
        if (skillTask != null) { skillTask.cancel(); skillTask = null; }
        activeBoss = null;
        activeBossId = null;
        activeRarity = null;
        damage.clear();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("spawn", "set", "delete", "list", "status", "kill", "reload").stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        if (args.length == 2 && args[0].equalsIgnoreCase("spawn")) {
            ConfigurationSection section = configFile.get().getConfigurationSection("bosses");
            return section == null ? List.of() : new ArrayList<>(section.getKeys(false));
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("delete")) {
            ConfigurationSection section = data.getConfigurationSection("locations");
            return section == null ? List.of() : new ArrayList<>(section.getKeys(false));
        }
        return List.of();
    }

    private String color(String s) { return ColorUtil.color(s); }
    private String strip(String s) { return s.replaceAll("§[0-9A-FK-ORa-fk-or]", ""); }
    private String msg(String key, Map<String,String> vars) { String s = configFile.get().getString("messages." + key, "&8【&aVelioraBoss&8】 &cMessage not found: " + key); for (var e : vars.entrySet()) s = s.replace("%" + e.getKey() + "%", e.getValue()); return s; }
}
