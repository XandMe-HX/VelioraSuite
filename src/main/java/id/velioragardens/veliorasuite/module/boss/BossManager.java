package id.velioragardens.veliorasuite.module.boss;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.module.boss.model.BossDefinition;
import id.velioragardens.veliorasuite.module.boss.model.BossRarity;
import id.velioragardens.veliorasuite.module.boss.model.BossSpawnPoint;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

public final class BossManager implements Listener {

    private final VelioraSuite plugin;
    private final BossConfigManager config;
    private final BossDataManager data;
    private final BossScaleHelper scaleHelper;
    private final BossDamageTracker damageTracker = new BossDamageTracker();
    private final BossBarManager bossBarManager;
    private final BossRewardManager rewardManager;
    private final BossSkillManager skillManager;
    private final Random random = new Random();
    private final NamespacedKey bossIdKey;
    private final NamespacedKey bossNameKey;
    private final NamespacedKey bossRarityKey;
    private final NamespacedKey minionOwnerKey;
    private BukkitTask schedulerTask;
    private LivingEntity activeBoss;
    private BossDefinition activeDefinition;
    private Location lastKnownLocation;
    private long nextSpawnAt;
    private long despawnAt;

    public BossManager(VelioraSuite plugin) {
        this.plugin = plugin;
        this.config = new BossConfigManager(plugin);
        this.data = new BossDataManager(plugin);
        this.scaleHelper = new BossScaleHelper(plugin);
        this.bossBarManager = new BossBarManager(config);
        this.rewardManager = new BossRewardManager(plugin, config);
        this.skillManager = new BossSkillManager(plugin, config, this);
        this.bossIdKey = new NamespacedKey(plugin, "velioraboss_id");
        this.bossNameKey = new NamespacedKey(plugin, "velioraboss_name");
        this.bossRarityKey = new NamespacedKey(plugin, "velioraboss_rarity");
        this.minionOwnerKey = new NamespacedKey(plugin, "velioraboss_minion_owner");
    }

    public void load() {
        config.load();
        data.load();
        nextSpawnAt = System.currentTimeMillis() + config.intervalMinutes() * 60_000L;
    }

    public void start() {
        stopScheduler();
        cleanupTaggedEntities();
        schedulerTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L * 5L);
    }

    public void shutdown() {
        stopScheduler();
        stopActive(false);
        cleanupTaggedEntities();
    }

    public void reload() {
        config.load();
        data.load();
    }

    public BossConfigManager config() { return config; }
    public BossDataManager data() { return data; }
    public LivingEntity getActiveBoss() { return activeBoss; }
    public String getActiveBossId() { return activeDefinition == null ? "" : activeDefinition.id(); }
    public NamespacedKey getMinionOwnerKey() { return minionOwnerKey; }
    public Location getLastKnownLocation() { return lastKnownLocation; }

    public void setSpawnPoint(Player player, String name) {
        data.setSpawnPoint(BossSpawnPoint.from(name.toLowerCase(java.util.Locale.ROOT), player.getLocation()));
        player.sendMessage(config.color(config.message("spawn-set", "%prefix% &aSpawn boss &f%name% &aberhasil diset.").replace("%name%", name)));
    }

    public boolean spawnById(String id, CommandSender sender) {
        BossDefinition definition = config.bosses().get(id.toLowerCase(java.util.Locale.ROOT));
        if (definition == null) {
            sender.sendMessage(config.color(config.prefix() + "&cBoss tidak ditemukan: &f" + id));
            return false;
        }
        return spawn(definition, randomSpawnPoint(), true);
    }

    public void stopActive(boolean message) {
        if (activeBoss == null && activeDefinition == null) {
            if (message) Bukkit.broadcastMessage(config.color(config.message("no-active-boss", "%prefix% &eTidak ada boss aktif.")));
            clearRuntime();
            return;
        }
        if (activeBoss != null && !activeBoss.isDead()) activeBoss.remove();
        if (message && activeDefinition != null) Bukkit.broadcastMessage(config.color(config.message("boss-despawn", "%prefix% &e%boss% menghilang.").replace("%boss%", config.color(activeDefinition.displayName()))));
        clearRuntime();
        nextSpawnAt = System.currentTimeMillis() + config.intervalMinutes() * 60_000L;
    }

    public void sendStatus(CommandSender sender) {
        if (isActive()) {
            sender.sendMessage(config.color(config.message("boss-active", "%prefix% &cBoss aktif: &f%boss% &7di &f%world% %x% %y% %z%&7. HP: &f%health%/%max_health%")
                    .replace("%boss%", config.color(activeDefinition.displayName()))
                    .replace("%world%", activeBoss.getWorld().getName())
                    .replace("%x%", String.valueOf(activeBoss.getLocation().getBlockX()))
                    .replace("%y%", String.valueOf(activeBoss.getLocation().getBlockY()))
                    .replace("%z%", String.valueOf(activeBoss.getLocation().getBlockZ()))
                    .replace("%health%", String.valueOf((int) Math.ceil(activeBoss.getHealth())))
                    .replace("%max_health%", String.valueOf((int) Math.ceil(activeBoss.getMaxHealth())))));
            sender.sendMessage(config.color("&7Rarity: &f" + activeDefinition.rarity().displayName() + " &8| &7Despawn: &f" + timeLeft(despawnAt)));
            sender.sendMessage(config.color(config.message("top-damage-header", "&cTop Damage Boss:")));
            sender.sendMessage(config.color("&f" + damageTracker.topText(5)));
        } else {
            sender.sendMessage(config.color(config.message("boss-next", "%prefix% &eBoss belum muncul. Spawn berikutnya dalam &f%time%&e.").replace("%time%", timeLeft(nextSpawnAt))));
            sender.sendMessage(config.color("&7Spawn point tersedia: &f" + data.spawnPoints().size()));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (activeBoss != null && event.getEntity().getUniqueId().equals(activeBoss.getUniqueId())) {
            Player player = damager(event.getDamager());
            if (player != null) damageTracker.add(player, event.getFinalDamage());
            return;
        }
        if (event.getDamager() != null && event.getDamager().getScoreboardTags().contains("velioraboss_boss")) event.setDamage(activeDefinition == null ? event.getDamage() : activeDefinition.damage());
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        if (activeBoss == null || !event.getEntity().getUniqueId().equals(activeBoss.getUniqueId())) return;
        Location death = event.getEntity().getLocation();
        if (config.announceDeath()) Bukkit.broadcastMessage(config.color(config.message("boss-death", "%prefix% &a%boss% berhasil dikalahkan!").replace("%boss%", config.color(activeDefinition.displayName()))));
        death.getWorld().playSound(death, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.0F);
        rewardManager.distribute(activeDefinition, death, damageTracker);
        data.addKill(activeDefinition.id());
        clearRuntime();
        nextSpawnAt = System.currentTimeMillis() + config.intervalMinutes() * 60_000L;
    }

    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        if (config.preventBlockDamage()) event.blockList().clear();
    }

    private void tick() {
        if (!config.isEnabled() || !config.isSpawnEnabled()) return;
        if (isActive()) {
            lastKnownLocation = activeBoss.getLocation();
            bossBarManager.tick(activeDefinition, activeBoss, despawnAt);
            if (System.currentTimeMillis() >= despawnAt) stopActive(true);
            return;
        }
        if (System.currentTimeMillis() >= nextSpawnAt) {
            if (data.spawnPoints().isEmpty() && config.requireSpawnPoint()) {
                plugin.getLogger().warning("VelioraBoss: Belum ada spawn point. Gunakan /boss set <nama>.");
                nextSpawnAt = System.currentTimeMillis() + config.intervalMinutes() * 60_000L;
                return;
            }
            spawn(randomDefinition(), randomSpawnPoint(), true);
        }
    }

    private boolean spawn(BossDefinition definition, Location location, boolean announce) {
        if (definition == null || location == null || location.getWorld() == null) return false;
        if (isActive() && !config.allowMultiple()) return false;
        location.getChunk().load(true);
        Entity entity = location.getWorld().spawnEntity(location, definition.entityType(), CreatureSpawnEvent.SpawnReason.CUSTOM);
        if (!(entity instanceof LivingEntity living)) {
            entity.remove();
            return false;
        }
        activeBoss = living;
        activeDefinition = definition;
        lastKnownLocation = location.clone();
        despawnAt = System.currentTimeMillis() + config.despawnMinutes() * 60_000L;
        living.addScoreboardTag("velioraboss_boss");
        living.getPersistentDataContainer().set(bossIdKey, PersistentDataType.STRING, definition.id());
        living.getPersistentDataContainer().set(bossNameKey, PersistentDataType.STRING, ChatColorStrip.strip(config.color(definition.displayName())));
        living.getPersistentDataContainer().set(bossRarityKey, PersistentDataType.STRING, definition.rarity().name());
        living.setCustomName(config.color(definition.displayName()));
        living.setCustomNameVisible(true);
        living.setRemoveWhenFarAway(false);
        living.setPersistent(true);
        scaleHelper.setMaxHealth(living, definition.health());
        scaleHelper.apply(living, definition.scale());
        if (living instanceof Mob mob) mob.setTarget(nearestPlayer(location));
        bossBarManager.create(definition);
        damageTracker.clear();
        skillManager.start();
        location.getWorld().playSound(location, config.sound("effects.spawn.sound", "ENTITY_WARDEN_ROAR"), 1.0F, 0.8F);
        location.getWorld().spawnParticle(config.particle("effects.spawn.particle", "SOUL"), location, 80, 2.5D, 1.5D, 2.5D, 0.05D);
        if (announce && config.announceSpawn()) Bukkit.broadcastMessage(config.color(config.message("boss-spawn", "%prefix% &c%boss% &7muncul di &f%world% %x% %y% %z%&7!")
                .replace("%boss%", config.color(definition.displayName()))
                .replace("%world%", location.getWorld().getName())
                .replace("%x%", String.valueOf(location.getBlockX()))
                .replace("%y%", String.valueOf(location.getBlockY()))
                .replace("%z%", String.valueOf(location.getBlockZ()))));
        return true;
    }

    private boolean isActive() { return activeBoss != null && !activeBoss.isDead(); }

    private BossDefinition randomDefinition() {
        BossRarity rarity = rollRarity();
        List<BossDefinition> list = new ArrayList<>();
        for (BossDefinition definition : config.bosses().values()) if (definition.rarity() == rarity) list.add(definition);
        if (list.isEmpty()) list.addAll(config.bosses().values());
        return list.isEmpty() ? null : list.get(random.nextInt(list.size()));
    }

    private BossRarity rollRarity() {
        double total = 0.0D;
        for (double value : config.rarityChance().values()) total += value;
        double roll = random.nextDouble() * Math.max(1.0D, total);
        double current = 0.0D;
        for (Map.Entry<BossRarity, Double> entry : config.rarityChance().entrySet()) {
            current += entry.getValue();
            if (roll <= current) return entry.getKey();
        }
        return BossRarity.COMMON;
    }

    private Location randomSpawnPoint() {
        if (data.spawnPoints().isEmpty()) return null;
        List<BossSpawnPoint> points = new ArrayList<>(data.spawnPoints().values());
        Location location = points.get(random.nextInt(points.size())).toLocation();
        if (location != null) location.getChunk().load(true);
        return location;
    }

    private Player nearestPlayer(Location location) {
        Player nearest = null;
        double distance = Double.MAX_VALUE;
        for (Player player : location.getWorld().getPlayers()) {
            double check = player.getLocation().distanceSquared(location);
            if (check < distance) { distance = check; nearest = player; }
        }
        return nearest;
    }

    private Player damager(Entity entity) {
        if (entity instanceof Player player) return player;
        if (entity instanceof org.bukkit.entity.Projectile projectile && projectile.getShooter() instanceof Player player) return player;
        return null;
    }

    private void clearRuntime() {
        skillManager.stop();
        skillManager.cleanupMinions();
        bossBarManager.clear();
        damageTracker.clear();
        activeBoss = null;
        activeDefinition = null;
        despawnAt = 0L;
    }

    private void cleanupTaggedEntities() {
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity.getScoreboardTags().contains("velioraboss_boss") || entity.getScoreboardTags().contains("velioraboss_minion")) entity.remove();
            }
        }
    }

    private void stopScheduler() { if (schedulerTask != null) schedulerTask.cancel(); schedulerTask = null; }

    private String timeLeft(long target) {
        long seconds = Math.max(0L, (target - System.currentTimeMillis()) / 1000L);
        return (seconds / 60L) + "m " + (seconds % 60L) + "s";
    }

    private static final class ChatColorStrip {
        private static String strip(String input) { return org.bukkit.ChatColor.stripColor(input == null ? "" : input); }
    }
}
