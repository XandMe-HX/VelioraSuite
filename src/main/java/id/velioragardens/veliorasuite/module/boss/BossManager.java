package id.velioragardens.veliorasuite.module.boss;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.module.boss.model.BossDefinition;
import id.velioragardens.veliorasuite.module.boss.model.BossRarity;
import id.velioragardens.veliorasuite.module.boss.model.BossSkillType;
import id.velioragardens.veliorasuite.module.boss.model.BossSpawnPoint;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public final class BossManager implements Listener {

    private final VelioraSuite plugin;
    private final BossConfigManager config;
    private final BossDataManager data;
    private final BossScaleHelper scaleHelper;
    private final BossDamageTracker damageTracker = new BossDamageTracker();
    private final BossBarManager bossBarManager;
    private final BossRewardManager rewardManager;
    private final BossSkillManager skillManager;
    private final BossTargetManager targetManager;
    private final BossQuestHook questHook;
    private final Random random = new Random();
    private final Set<Integer> sentWarnings = new HashSet<>();
    private final NamespacedKey bossIdKey;
    private final NamespacedKey bossNameKey;
    private final NamespacedKey bossRarityKey;
    private final NamespacedKey minionOwnerKey;
    private BukkitTask schedulerTask;
    private LivingEntity activeBoss;
    private BossDefinition activeDefinition;
    private Location lastKnownLocation;
    private Location arenaCenter;
    private long nextSpawnAt;
    private long despawnAt;
    private long lastRetargetAt;

    public BossManager(VelioraSuite plugin) {
        this.plugin = plugin;
        this.config = new BossConfigManager(plugin);
        this.data = new BossDataManager(plugin);
        this.scaleHelper = new BossScaleHelper(plugin);
        this.bossBarManager = new BossBarManager(config);
        this.rewardManager = new BossRewardManager(plugin, config);
        this.targetManager = new BossTargetManager(plugin, config);
        this.skillManager = new BossSkillManager(plugin, config, this);
        this.questHook = new BossQuestHook(plugin);
        this.bossIdKey = new NamespacedKey(plugin, "velioraboss_id");
        this.bossNameKey = new NamespacedKey(plugin, "velioraboss_name");
        this.bossRarityKey = new NamespacedKey(plugin, "velioraboss_rarity");
        this.minionOwnerKey = new NamespacedKey(plugin, "velioraboss_minion_owner");
    }

    public void load() {
        config.load();
        data.load();
        scheduleNextSpawn();
    }

    public void start() {
        stopScheduler();
        cleanupTaggedEntities();
        schedulerTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void shutdown() {
        stopScheduler();
        stopActive(false);
        cleanupTaggedEntities();
    }

    public void reload() {
        config.load();
        data.load();
        sentWarnings.clear();
        lastRetargetAt = 0L;
    }

    public BossConfigManager config() { return config; }
    public BossDataManager data() { return data; }
    public LivingEntity getActiveBoss() { return activeBoss; }
    public String getActiveBossId() { return activeDefinition == null ? "" : activeDefinition.id(); }
    public NamespacedKey getMinionOwnerKey() { return minionOwnerKey; }
    public Location getLastKnownLocation() { return lastKnownLocation; }
    public Location getArenaCenter() { return arenaCenter; }

    public void setSpawnPoint(Player player, String name) {
        data.setSpawnPoint(BossSpawnPoint.from(name.toLowerCase(Locale.ROOT), player.getLocation()));
        player.sendMessage(config.color(config.message("spawn-set", "%prefix% &aSpawn boss &f%name% &aberhasil diset.").replace("%name%", name)));
    }

    public boolean spawnByName(String input, CommandSender sender) {
        BossDefinition definition = resolveBoss(input);
        if (definition == null) {
            sender.sendMessage(config.color(config.message("boss-not-found", "%prefix% &cBoss tidak ditemukan: &f%boss%").replace("%boss%", input)));
            return false;
        }
        return spawn(definition, randomSpawnPoint(), true);
    }

    public BossDefinition resolveBoss(String input) {
        if (input == null || input.isBlank()) return null;
        String exact = normalizeId(input);
        BossDefinition definition = config.bosses().get(exact);
        if (definition != null) return definition;
        String compact = normalizeLoose(input);
        for (BossDefinition boss : config.bosses().values()) {
            if (normalizeLoose(boss.id()).equals(compact)) return boss;
            if (normalizeLoose(config.plain(boss.displayName())).equals(compact)) return boss;
        }
        return null;
    }

    public void sendBossList(CommandSender sender) {
        sender.sendMessage(config.color(config.message("boss-list-header", "%prefix% &eBoss tersedia:")));
        for (BossDefinition boss : config.bosses().values()) {
            sender.sendMessage(config.color(config.message("boss-list-line", "&7- &f%boss% &8| &e%rarity% &8| &cHP %health% &8| &cDMG %damage%")
                    .replace("%boss%", config.color(boss.displayName()))
                    .replace("%rarity%", boss.rarity().name())
                    .replace("%health%", String.valueOf((int) boss.health()))
                    .replace("%damage%", String.valueOf((int) boss.damage()))));
        }
    }

    public void sendBossInfo(CommandSender sender, String input) {
        BossDefinition boss = resolveBoss(input);
        if (boss == null) {
            sender.sendMessage(config.color(config.message("boss-not-found", "%prefix% &cBoss tidak ditemukan: &f%boss%").replace("%boss%", input)));
            return;
        }
        sender.sendMessage(config.color(config.message("boss-info-header", "%prefix% &eInfo Boss: &f%boss%").replace("%boss%", config.color(boss.displayName()))));
        infoLine(sender, "ID", boss.id());
        infoLine(sender, "Entity", boss.entityType().name());
        infoLine(sender, "Rarity", boss.rarity().name());
        infoLine(sender, "Health", String.valueOf((int) boss.health()));
        infoLine(sender, "Damage", String.valueOf((int) boss.damage()));
        infoLine(sender, "Scale", String.valueOf(boss.scale()));
        infoLine(sender, "Money", config.bossMoneyMin(boss) + " - " + config.bossMoneyMax(boss));
        infoLine(sender, "Top Bonus", "1: " + config.topBonusMin(0) + "-" + config.topBonusMax(0) + ", 2: " + config.topBonusMin(1) + "-" + config.topBonusMax(1) + ", 3: " + config.topBonusMin(2) + "-" + config.topBonusMax(2));
        infoLine(sender, "Material", "Diamond " + config.rewardMaterial(boss.rarity(), "diamond") + ", Ancient Debris " + config.rewardMaterial(boss.rarity(), "ancient-debris"));
        infoLine(sender, "Skills", skillText(boss));
    }

    public void sendTop(CommandSender sender) {
        sender.sendMessage(config.color(config.message("boss-top-header", "%prefix% &eTop Boss Damage:")));
        sender.sendMessage(config.color("&7Total boss kill server: &f" + data.totalKills()));
        if (!data.bossKills().isEmpty()) {
            sender.sendMessage(config.color("&7Kill per boss:"));
            for (Map.Entry<String, Integer> entry : data.bossKills().entrySet()) sender.sendMessage(config.color("&8- &f" + entry.getKey() + " &7= &e" + entry.getValue()));
        }
        List<BossDataManager.PlayerDamageStat> top = data.topDamage(10);
        if (top.isEmpty()) {
            sender.sendMessage(config.color("&7Belum ada data damage boss."));
            return;
        }
        for (int i = 0; i < top.size(); i++) {
            BossDataManager.PlayerDamageStat stat = top.get(i);
            sender.sendMessage(config.color(config.message("boss-top-line", "&7%rank%. &f%player% &8- &c%damage% damage")
                    .replace("%rank%", String.valueOf(i + 1))
                    .replace("%player%", stat.name())
                    .replace("%damage%", String.format("%.1f", stat.damage()))));
        }
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
        scheduleNextSpawn();
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
            if (player != null) {
                damageTracker.add(player, event.getFinalDamage());
                data.addDamage(player, event.getFinalDamage());
                if (activeBoss instanceof Mob mob && targetManager.isValidCurrentTarget(player, activeBoss.getLocation(), arenaCenter)) mob.setTarget(player);
            }
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
        for (BossDamageTracker.Entry entry : damageTracker.top()) {
            if (entry.damage() >= config.minDamageToReward()) {
                Player player = Bukkit.getPlayer(entry.uuid());
                if (player != null) {
                    questHook.addMonsterHunterProgress(player);
                    data.addParticipation(player);
                }
            }
        }
        data.addKill(activeDefinition.id());
        clearRuntime();
        scheduleNextSpawn();
    }

    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        if (config.preventBlockDamage()) event.blockList().clear();
    }

    private void tick() {
        if (!config.isEnabled() || !config.isSpawnEnabled()) return;
        if (isActive()) {
            lastKnownLocation = activeBoss.getLocation();
            enforceArena();
            bossBarManager.tick(activeDefinition, activeBoss, despawnAt);
            if (System.currentTimeMillis() >= despawnAt) stopActive(true);
            return;
        }
        sendSpawnWarnings();
        if (System.currentTimeMillis() >= nextSpawnAt) {
            if (data.spawnPoints().isEmpty() && config.requireSpawnPoint()) {
                plugin.getLogger().warning("VelioraBoss: Belum ada spawn point. Gunakan /boss set <nama>.");
                scheduleNextSpawn();
                return;
            }
            spawn(randomDefinition(), randomSpawnPoint(), true);
        }
    }

    private boolean spawn(BossDefinition definition, Location location, boolean announce) {
        if (definition == null || location == null || location.getWorld() == null) return false;
        if (isActive() && !config.allowMultiple()) return false;
        location.getChunk().load(true);
        Entity entity = location.getWorld().spawnEntity(location, definition.entityType());
        if (!(entity instanceof LivingEntity living)) {
            entity.remove();
            return false;
        }
        activeBoss = living;
        activeDefinition = definition;
        lastKnownLocation = location.clone();
        arenaCenter = location.clone();
        despawnAt = System.currentTimeMillis() + config.despawnMinutes() * 60_000L;
        lastRetargetAt = 0L;
        sentWarnings.clear();
        living.addScoreboardTag("velioraboss_boss");
        living.getPersistentDataContainer().set(bossIdKey, PersistentDataType.STRING, definition.id());
        living.getPersistentDataContainer().set(bossNameKey, PersistentDataType.STRING, org.bukkit.ChatColor.stripColor(config.color(definition.displayName())));
        living.getPersistentDataContainer().set(bossRarityKey, PersistentDataType.STRING, definition.rarity().name());
        living.setCustomName(config.color(definition.displayName()));
        living.setCustomNameVisible(true);
        living.setRemoveWhenFarAway(false);
        living.setPersistent(true);
        scaleHelper.setMaxHealth(living, definition.health());
        scaleHelper.apply(living, definition.scale());
        if (living instanceof Mob mob) mob.setTarget(findBestTarget(location));
        bossBarManager.create(definition);
        damageTracker.clear();
        skillManager.start(definition);
        location.getWorld().playSound(location, config.sound("effects.spawn.sound", "ENTITY_WARDEN_ROAR"), 1.0F, 0.8F);
        location.getWorld().spawnParticle(config.particle("effects.spawn.particle", "SOUL"), location, config.spawnParticleCount(), 3.0D, 1.7D, 3.0D, 0.07D);
        if (config.spawnTitleEnabled()) sendSpawnTitle(definition, location);
        if (announce && config.announceSpawn()) Bukkit.broadcastMessage(config.color(config.message("boss-spawn", "%prefix% &c%boss% &7muncul di &f%world% %x% %y% %z%&7!")
                .replace("%boss%", config.color(definition.displayName()))
                .replace("%world%", location.getWorld().getName())
                .replace("%x%", String.valueOf(location.getBlockX()))
                .replace("%y%", String.valueOf(location.getBlockY()))
                .replace("%z%", String.valueOf(location.getBlockZ()))));
        return true;
    }

    public boolean isPlayerInsideArena(Player player) {
        if (!config.arenaEnabled() || arenaCenter == null || player == null || player.getWorld() == null || !player.getWorld().equals(arenaCenter.getWorld())) return true;
        return horizontalDistance(arenaCenter, player.getLocation()) <= config.arenaRadius() + 6.0D;
    }

    public Player findBestTarget(Location center) {
        return targetManager.findBestTarget(center == null ? (activeBoss == null ? arenaCenter : activeBoss.getLocation()) : center, arenaCenter);
    }

    public List<Player> nearbyTargetPlayers(Location center, double horizontalRadius) {
        return targetManager.validPlayers(center, arenaCenter, horizontalRadius);
    }

    private void enforceArena() {
        if (activeBoss == null || activeBoss.isDead() || arenaCenter == null) return;
        Location current = activeBoss.getLocation();
        boolean far = config.arenaEnabled() && config.teleportBackIfFar() && current.getWorld().equals(arenaCenter.getWorld()) && horizontalDistance(current, arenaCenter) > config.teleportBackDistance();
        boolean below = config.arenaEnabled() && config.teleportBackIfBelowSpawnY() && current.getY() < arenaCenter.getY() - config.belowYOffset();
        boolean stuck = !current.getBlock().isPassable() || !current.getBlock().getRelative(0, 1, 0).isPassable();
        if (far || below || stuck) teleportBossBack();
        retarget(false);
        cleanupMinionsByArena();
    }

    private void retarget(boolean force) {
        if (!config.targetingEnabled() || !(activeBoss instanceof Mob mob)) return;
        long now = System.currentTimeMillis();
        if (!force && now - lastRetargetAt < config.retargetIntervalSeconds() * 1000L) return;
        lastRetargetAt = now;
        Player current = mob.getTarget() instanceof Player player ? player : null;
        if (!force && !config.forceTargetNearest() && targetManager.isValidCurrentTarget(current, activeBoss.getLocation(), arenaCenter)) return;
        if (!force && current != null && targetManager.isValidCurrentTarget(current, activeBoss.getLocation(), arenaCenter) && !config.forceTargetNearest()) return;
        Player target = findBestTarget(activeBoss.getLocation());
        if (target != null) {
            mob.setTarget(target);
            return;
        }
        mob.setTarget(null);
        if (config.noTargetTeleportBack() && arenaCenter != null && activeBoss.getLocation().distanceSquared(arenaCenter) > 4.0D) teleportBossBack();
    }

    private void teleportBossBack() {
        if (activeBoss == null || arenaCenter == null) return;
        activeBoss.teleport(arenaCenter);
        arenaCenter.getWorld().spawnParticle(Particle.PORTAL, arenaCenter, 60, 1.5D, 1.0D, 1.5D, 0.1D);
        arenaCenter.getWorld().playSound(arenaCenter, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0F, 0.8F);
        String message = config.color(config.message("boss-return-arena", "%prefix% &e%boss% kembali ke arena!").replace("%boss%", activeDefinition == null ? "Boss" : config.color(activeDefinition.displayName())));
        for (Player player : arenaCenter.getWorld().getPlayers()) if (horizontalDistance(player.getLocation(), arenaCenter) <= config.arenaRadius()) player.sendMessage(message);
        retarget(true);
    }

    private void cleanupMinionsByArena() {
        if (arenaCenter == null || arenaCenter.getWorld() == null) return;
        for (Entity entity : arenaCenter.getWorld().getNearbyEntities(arenaCenter, 96, 64, 96)) {
            if (!entity.getScoreboardTags().contains("velioraboss_minion")) continue;
            boolean outside = !entity.getWorld().equals(arenaCenter.getWorld()) || horizontalDistance(entity.getLocation(), arenaCenter) > config.arenaRadius();
            if (outside && config.removeMinionOutsideRadius()) entity.remove();
            else if (entity instanceof Mob mob && (mob.getTarget() == null || !(mob.getTarget() instanceof Player player) || !targetManager.isValidCurrentTarget(player, entity.getLocation(), arenaCenter))) mob.setTarget(findBestTarget(entity.getLocation()));
        }
    }

    private void sendSpawnWarnings() {
        long millis = nextSpawnAt - System.currentTimeMillis();
        if (millis <= 0) return;
        for (int minute : config.warningTimesMinutes()) {
            if (sentWarnings.contains(minute)) continue;
            if (millis <= minute * 60_000L) {
                String key = "boss-warning-" + minute;
                String fallback = minute == 1 ? "%prefix% &cBoss akan muncul dalam &f1 menit&c!" : "%prefix% &eBoss akan muncul dalam &f" + minute + " menit&e!";
                Bukkit.broadcastMessage(config.color(config.message(key, fallback)));
                sentWarnings.add(minute);
            }
        }
    }

    private void sendSpawnTitle(BossDefinition definition, Location location) {
        String title = config.spawnTitle().replace("%boss%", config.color(definition.displayName())).replace("%rarity%", definition.rarity().displayName()).replace("%world%", location.getWorld().getName());
        String subtitle = config.spawnSubtitle().replace("%boss%", config.color(definition.displayName())).replace("%rarity%", definition.rarity().displayName()).replace("%world%", location.getWorld().getName());
        for (Player player : Bukkit.getOnlinePlayers()) player.sendTitle(config.color(title), config.color(subtitle), 10, 60, 20);
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

    private void infoLine(CommandSender sender, String key, String value) {
        sender.sendMessage(config.color(config.message("boss-info-line", "&7%key%: &f%value%").replace("%key%", key).replace("%value%", value)));
    }

    private String skillText(BossDefinition boss) {
        List<String> names = new ArrayList<>();
        for (BossSkillType skill : boss.skills()) names.add(skill.name());
        return String.join(", ", names);
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
        arenaCenter = null;
        despawnAt = 0L;
        lastRetargetAt = 0L;
    }

    private void cleanupTaggedEntities() {
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity.getScoreboardTags().contains("velioraboss_boss") || entity.getScoreboardTags().contains("velioraboss_minion")) entity.remove();
            }
        }
    }

    private void scheduleNextSpawn() {
        nextSpawnAt = System.currentTimeMillis() + config.intervalMinutes() * 60_000L;
        sentWarnings.clear();
    }

    private double horizontalDistance(Location a, Location b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private String normalizeId(String input) { return input.trim().toLowerCase(Locale.ROOT).replace(' ', '_'); }
    private String normalizeLoose(String input) { return input == null ? "" : config.plain(input).toLowerCase(Locale.ROOT).replace("_", "").replace("-", "").replace(" ", ""); }
    private void stopScheduler() { if (schedulerTask != null) schedulerTask.cancel(); schedulerTask = null; }

    private String timeLeft(long target) {
        long seconds = Math.max(0L, (target - System.currentTimeMillis()) / 1000L);
        return (seconds / 60L) + "m " + (seconds % 60L) + "s";
    }
}
