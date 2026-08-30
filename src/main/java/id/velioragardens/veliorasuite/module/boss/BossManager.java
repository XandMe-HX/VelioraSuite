package id.velioragardens.veliorasuite.module.boss;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.module.adventure.AdventureModule;
import id.velioragardens.veliorasuite.module.boss.model.BossDefinition;
import id.velioragardens.veliorasuite.module.boss.model.BossRarity;
import id.velioragardens.veliorasuite.module.boss.model.BossSkillType;
import id.velioragardens.veliorasuite.module.boss.model.BossSpawnPoint;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.PiglinAbstract;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public final class BossManager implements Listener {

    private static final ZoneId JAKARTA_ZONE = ZoneId.of("Asia/Jakarta");
    private static final double NATIVE_BOSS_HEALTH = 1024.0D;

    private final VelioraSuite plugin;
    private final BossConfigManager config;
    private final BossDataManager data;
    private final BossScaleHelper scaleHelper;
    private final BossDamageTracker damageTracker = new BossDamageTracker();
    private final BossBarManager bossBarManager;
    private final BossRewardManager rewardManager;
    private final BossSkillManager skillManager;
    private final BossTargetManager targetManager;
    private final Random random = new Random();
    private final Set<Integer> sentWarnings = new HashSet<>();
    private final Map<UUID, MaceSmashState> maceSmashes = new HashMap<>();
    private final Map<UUID, List<Long>> invalidReachHits = new HashMap<>();
    private final Map<UUID, Long> bossDamageLocks = new HashMap<>();
    // Feedback needs a per-player limiter. A global limiter made one player's hit
    // hide another player's critical feedback during busy boss fights.
    private final Map<UUID, Long> lastHitEffects = new HashMap<>();
    private final NamespacedKey bossIdKey;
    private final NamespacedKey bossNameKey;
    private final NamespacedKey bossRarityKey;
    private final NamespacedKey bossExpiresAtKey;
    private final NamespacedKey bossVirtualHealthKey;
    private final NamespacedKey bossVirtualMaxHealthKey;
    private final NamespacedKey minionOwnerKey;
    private BukkitTask schedulerTask;
    private LivingEntity activeBoss;
    private BossDefinition activeDefinition;
    private Location lastKnownLocation;
    private Location arenaCenter;
    private long nextSpawnAt;
    private long despawnAt;
    private long lastRetargetAt;
    private long lastMinionCleanupAt;
    private double activeVirtualHealth;
    private double activeVirtualMaxHealth;
    private Chunk forcedBossChunk;

    public BossManager(VelioraSuite plugin) {
        this.plugin = plugin;
        this.config = new BossConfigManager(plugin);
        this.data = new BossDataManager(plugin);
        this.scaleHelper = new BossScaleHelper(plugin);
        this.bossBarManager = new BossBarManager(config);
        this.rewardManager = new BossRewardManager(plugin, config);
        this.targetManager = new BossTargetManager(plugin, config);
        this.skillManager = new BossSkillManager(plugin, config, this);
        this.bossIdKey = new NamespacedKey(plugin, "velioraboss_id");
        this.bossNameKey = new NamespacedKey(plugin, "velioraboss_name");
        this.bossRarityKey = new NamespacedKey(plugin, "velioraboss_rarity");
        this.bossExpiresAtKey = new NamespacedKey(plugin, "velioraboss_expires_at");
        this.bossVirtualHealthKey = new NamespacedKey(plugin, "velioraboss_virtual_health");
        this.bossVirtualMaxHealthKey = new NamespacedKey(plugin, "velioraboss_virtual_max_health");
        this.minionOwnerKey = new NamespacedKey(plugin, "velioraboss_minion_owner");
    }

    public void load() {
        config.load();
        data.load();
        scheduleNextSpawn();
    }

    public void start() {
        stopScheduler();
        recoverActiveBoss();
        schedulerTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void shutdown() {
        stopScheduler();
        persistActiveState();
        clearRuntime();
        data.shutdown();
    }

    public void reload() {
        config.load();
        data.load();
        sentWarnings.clear();
        lastRetargetAt = 0L;
        lastMinionCleanupAt = 0L;
        if (!config.isEnabled()) {
            stopActive(false);
            return;
        }
        refreshActiveBoss();
        scheduleNextSpawn();
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
        scheduleNextSpawn();
        String message = config.message("spawn-set", "%prefix% &aSpawn boss &f%name% &aberhasil diset. Jadwal boss mulai aktif dan mengikuti WIB setiap 1 jam.");
        player.sendMessage(config.color(message.replace("%name%", name)));
        player.sendMessage(config.color(config.message("boss-next", "%prefix% &eBoss belum muncul. Spawn berikutnya dalam &f%time%&e.").replace("%time%", timeLeft(nextSpawnAt))));
    }

    public boolean spawnRandom(CommandSender sender) {
        Location location = randomSpawnPoint();
        if (location == null) {
            sender.sendMessage(config.color(config.message("no-spawn-point", "%prefix% &cBelum ada spawn point boss. Gunakan &f/boss set <nama>&c dulu.")));
            return false;
        }
        BossDefinition definition = randomDefinition();
        if (definition == null) {
            sender.sendMessage(config.color(config.message("boss-not-found", "%prefix% &cTidak ada definisi boss yang valid di boss.yml.")));
            return false;
        }
        return spawn(definition, location, true);
    }

    public boolean spawnByName(String input, CommandSender sender) {
        BossDefinition definition = resolveBoss(input);
        if (definition == null) {
            sender.sendMessage(config.color(config.message("boss-not-found", "%prefix% &cBoss tidak ditemukan: &f%boss%").replace("%boss%", input)));
            return false;
        }
        Location location = randomSpawnPoint();
        if (location == null) {
            sender.sendMessage(config.color(config.message("no-spawn-point", "%prefix% &cBelum ada spawn point boss. Gunakan &f/boss set <nama>&c dulu.")));
            return false;
        }
        return spawn(definition, location, true);
    }

    public BossDefinition resolveBoss(String input) {
        if (input == null || input.isBlank()) return null;
        String exact = normalizeId(input);
        BossDefinition definition = config.bosses().get(exact);
        if (definition != null) return definition;
        String compact = normalizeLoose(input);
        BossDefinition prefixMatch = null;
        for (BossDefinition boss : config.bosses().values()) {
            String id = normalizeLoose(boss.id());
            String name = normalizeLoose(config.plain(boss.displayName()));
            if (id.equals(compact) || name.equals(compact)) return boss;
            if (id.startsWith(compact) || name.startsWith(compact)) {
                if (prefixMatch != null) return null; // Prefix ambigu: minta nama lebih lengkap.
                prefixMatch = boss;
            }
        }
        return prefixMatch;
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
        infoLine(sender, "Size", config.randomScaleEnabled() ? "Random " + config.randomScaleMin() + " - " + config.randomScaleMax() + " block" : String.valueOf(boss.scale()));
        infoLine(sender, "Defense", "Armor " + config.bossArmor() + ", Toughness " + config.bossArmorToughness() + ", Knockback Resist " + config.bossKnockbackResistance());
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
            if (message) notifyPlayers(config.color(config.message("no-active-boss", "%prefix% &eTidak ada boss aktif.")));
            clearRuntime();
            return;
        }
        if (activeBoss != null && !activeBoss.isDead()) activeBoss.remove();
        cleanupTaggedEntities();
        if (message && activeDefinition != null) notifyPlayers(config.color(config.message("boss-despawn", "%prefix% &e%boss% menghilang.").replace("%boss%", config.color(activeDefinition.displayName()))));
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
                    .replace("%health%", String.valueOf((int) Math.ceil(activeVirtualHealth)))
                    .replace("%max_health%", String.valueOf((int) Math.ceil(activeVirtualMaxHealth)))));
            sender.sendMessage(config.color("&7Rarity: &f" + activeDefinition.rarity().displayName() + " &8| &7Despawn: &f" + timeLeft(despawnAt)));
            sender.sendMessage(config.color(config.message("top-damage-header", "&cTop Damage Boss:")));
            sender.sendMessage(config.color("&f" + damageTracker.topText(5)));
        } else {
            if (config.requireSpawnPoint() && data.spawnPoints().isEmpty()) {
                sender.sendMessage(config.color(config.message("no-spawn-point", "%prefix% &cBelum ada spawn point boss. Gunakan &f/boss set <nama>&c dulu. Notif dan timer boss belum aktif.")));
                return;
            }
            sender.sendMessage(config.color(config.message("boss-next", "%prefix% &eBoss belum muncul. Spawn berikutnya dalam &f%time%&e.").replace("%time%", timeLeft(nextSpawnAt))));
            sender.sendMessage(config.color("&7Spawn point tersedia: &f" + data.spawnPoints().size()));
        }
    }

    // Boss memakai HP virtual. Hit pemain tetap harus diproses walaupun event
    // vanilla lebih dulu dibatalkan oleh proteksi region; damage lingkungan
    // dan hit non-player tetap tidak dapat masuk.
    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDamage(EntityDamageByEntityEvent event) {
        boolean damagedBoss = activeBoss != null && event.getEntity().getUniqueId().equals(activeBoss.getUniqueId());
        boolean damagedMinion = event.getEntity().getScoreboardTags().contains("velioraboss_minion");
        boolean damagerBoss = event.getDamager().getScoreboardTags().contains("velioraboss_boss");
        boolean damagerMinion = event.getDamager().getScoreboardTags().contains("velioraboss_minion");
        if ((damagedBoss && damagerMinion) || (damagedMinion && (damagerBoss || damagerMinion))) {
            event.setCancelled(true);
            return;
        }
        if (damagedBoss) {
            Player player = damager(event.getDamager());
            if (player == null) {
                event.setCancelled(true);
                return;
            }
            if (isBossDamageLocked(player)) {
                event.setCancelled(true);
                player.sendMessage(config.color("&8[&6VelioraBoss&8] &cDamage boss dikunci sementara: &f" + timeLeft(bossDamageLocks.get(player.getUniqueId()))));
                return;
            }
            // Projectiles remain valid; only impossible direct melee hits are rejected.
            if (config.bossAntiReachEnabled() && event.getDamager() instanceof Player && isMeleeReachViolation(player)) {
                event.setCancelled(true);
                recordInvalidReach(player);
                return;
            }

            AttackKind attack = attackKind(event.getDamager(), player);
            boolean mace = attack == AttackKind.MACE;
            boolean chargedMace = mace && isMaceSmash(player) && consumeMaceSmashCharge(player);
            double adjustedDamage = event.getFinalDamage();
            if (chargedMace) adjustedDamage = Math.min(config.maceMaxDamagePerHit(), adjustedDamage * config.maceDamageMultiplier());
            double virtualDamage = adjustedDamage * config.virtualDamageMultiplier();
            event.setCancelled(true);
            damageActiveBoss(player, virtualDamage);
            showHitFeedback(player, attack, chargedMace, virtualDamage);
            if (chargedMace) player.sendMessage(config.color("&8[&6VelioraBoss&8] &eMace Smash &f"
                    + maceSmashes.get(player.getUniqueId()).charges() + "&7/" + config.maceSmashCharges()));
            if (activeBoss instanceof Mob mob && targetManager.isValidCurrentTarget(player, activeBoss.getLocation(), arenaCenter)) mob.setTarget(player);
            return;
        }
        if (damagerBoss) {
            double damage = activeDefinition == null ? event.getDamage() : activeDefinition.damage();
            event.setDamage(damage * skillManager.outgoingDamageMultiplier());
        }
    }

    private boolean isMaceSmash(Player player) {
        return player.getFallDistance() > 1.5F && !player.isOnGround();
    }

    private boolean consumeMaceSmashCharge(Player player) {
        long now = System.currentTimeMillis();
        MaceSmashState state = maceSmashes.get(player.getUniqueId());
        if (state == null || now >= state.cooldownUntil()) state = new MaceSmashState(config.maceSmashCharges(), 0L);
        if (state.charges() <= 0) {
            player.sendMessage(config.color("&8[&6VelioraBoss&8] &cMace Smash habis. &7Pulih dalam &f" + timeLeft(state.cooldownUntil())));
            maceSmashes.put(player.getUniqueId(), state);
            return false;
        }
        int remaining = state.charges() - 1;
        long cooldownUntil = remaining == 0 ? now + config.maceSmashCooldownSeconds() * 1000L : state.cooldownUntil();
        maceSmashes.put(player.getUniqueId(), new MaceSmashState(remaining, cooldownUntil));
        return true;
    }

    private boolean isBossDamageLocked(Player player) {
        return bossDamageLocks.getOrDefault(player.getUniqueId(), 0L) > System.currentTimeMillis();
    }

    private boolean isMeleeReachViolation(Player player) {
        if (activeBoss == null) return false;
        Location eye = player.getEyeLocation();
        org.bukkit.util.BoundingBox box = activeBoss.getBoundingBox();
        double x = Math.max(box.getMinX(), Math.min(eye.getX(), box.getMaxX()));
        double y = Math.max(box.getMinY(), Math.min(eye.getY(), box.getMaxY()));
        double z = Math.max(box.getMinZ(), Math.min(eye.getZ(), box.getMaxZ()));
        double dx = eye.getX() - x, dy = eye.getY() - y, dz = eye.getZ() - z;
        return dx * dx + dy * dy + dz * dz > config.bossMeleeReach() * config.bossMeleeReach();
    }

    private void recordInvalidReach(Player player) {
        long now = System.currentTimeMillis();
        List<Long> hits = invalidReachHits.computeIfAbsent(player.getUniqueId(), ignored -> new ArrayList<>());
        hits.removeIf(time -> now - time > config.invalidReachWindowSeconds() * 1000L);
        hits.add(now);
        player.sendMessage(config.color("&8[&6VelioraBoss&8] &cHit terlalu jauh dibatalkan. &7(" + hits.size() + "/" + config.invalidReachLimit() + ")"));
        if (hits.size() < config.invalidReachLimit()) return;
        long until = now + config.invalidReachDamageLockSeconds() * 1000L;
        bossDamageLocks.put(player.getUniqueId(), until);
        hits.clear();
        String message = config.color("&8[&6VelioraBoss&8] &e" + player.getName()
                + " &cmemicu anti-reach. Damage boss dikunci &f" + config.invalidReachDamageLockSeconds() + " detik&c.");
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.isOp() || online.hasPermission("veliorasuite.security.alerts")) online.sendMessage(message);
        }
    }

    /** Keeps environmental damage from bypassing virtual boss health. */
    @EventHandler(ignoreCancelled = true)
    public void onBossEnvironmentalDamage(EntityDamageEvent event) {
        if (event instanceof EntityDamageByEntityEvent) return;
        if (activeBoss != null && event.getEntity().getUniqueId().equals(activeBoss.getUniqueId())) event.setCancelled(true);
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        if (activeBoss == null || !event.getEntity().getUniqueId().equals(activeBoss.getUniqueId())) return;
        Location death = event.getEntity().getLocation();
        if (config.announceDeath()) notifyPlayers(config.color(config.message("boss-death", "%prefix% &a%boss% berhasil dikalahkan!").replace("%boss%", config.color(activeDefinition.displayName()))));
        death.getWorld().playSound(death, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.0F);
        rewardManager.distribute(activeDefinition, death, damageTracker);
        for (BossDamageTracker.Entry entry : damageTracker.top()) {
            if (entry.damage() >= config.minDamageToReward()) {
                Player player = Bukkit.getPlayer(entry.uuid());
                if (player != null) {
                    data.addParticipation(player);
                    AdventureModule adventure = plugin.getModuleManager().getModule("adventure")
                            .filter(AdventureModule.class::isInstance).map(AdventureModule.class::cast).orElse(null);
                    if (adventure != null && adventure.getManager() != null) adventure.getManager().addBossProgress(player, 1);
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

    // FIX 1: Cancel zombification transform for the active boss (Piglin Brute -> Zombified Piglin)
    @EventHandler(ignoreCancelled = true)
    public void onTransform(EntityTransformEvent event) {
        if (activeBoss == null) return;
        if (!event.getEntity().getUniqueId().equals(activeBoss.getUniqueId())) return;
        if (event.getTransformReason() == EntityTransformEvent.TransformReason.PIGLIN_ZOMBIFIED) {
            event.setCancelled(true);
        }
    }

    private void tick() {
        if (!config.isEnabled()) {
            if (isActive()) stopActive(false);
            return;
        }
        if (config.requireSpawnPoint() && data.spawnPoints().isEmpty()) {
            if (nextSpawnAt != 0L) {
                nextSpawnAt = 0L;
                sentWarnings.clear();
            }
            return;
        }
        if (nextSpawnAt <= 0L) scheduleNextSpawn();
        // FIX 2: If activeBoss exists but is dead or invalid, clearRuntime() to remove BossBar
        if (activeBoss != null && (activeBoss.isDead() || !activeBoss.isValid())) {
            cleanupTaggedEntities();
            clearRuntime();
            scheduleNextSpawn();
            return;
        }
        if (isActive()) {
            lastKnownLocation = activeBoss.getLocation();
            enforceArena();
            // FIX 3: Always retarget every tick to keep boss in combat state (immune to EAR deactivation)
            retarget(false);
            bossBarManager.tick(activeDefinition, activeBoss, activeVirtualHealth, activeVirtualMaxHealth, despawnAt);
            emitAura();
            if (System.currentTimeMillis() >= despawnAt) stopActive(true);
            return;
        }
        if (!config.isSpawnEnabled()) return;
        // Do not create entities or send schedule warnings while the server is empty.
        // Keep the expired schedule intact so the next online player receives the boss immediately.
        if (config.skipSpawnWhenNoPlayers() && Bukkit.getOnlinePlayers().isEmpty()) return;
        sendSpawnWarnings();
        if (System.currentTimeMillis() >= nextSpawnAt) {
            boolean spawned = spawn(randomDefinition(), randomSpawnPoint(), true);
            if (!spawned) rescheduleSpawnRetry("auto spawn gagal");
        }
    }

    private boolean spawn(BossDefinition definition, Location location, boolean announce) {
        if (definition == null || location == null || location.getWorld() == null) return false;
        if (isActive() && !config.allowMultiple()) return false;
        location.getChunk().load(true);
        holdBossChunk(location.getChunk());
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
        living.getPersistentDataContainer().set(bossExpiresAtKey, PersistentDataType.LONG, despawnAt);
        living.setCustomName(config.color(definition.displayName()));
        living.setCustomNameVisible(true);
        living.setRemoveWhenFarAway(false);
        living.setVisualFire(false);
        living.setCollidable(config.bossCollisionEnabled());

        if (living instanceof org.bukkit.entity.Zombie zombie) {
            zombie.setShouldBurnInDay(false);
        }

        if (living instanceof org.bukkit.entity.Skeleton skeleton) {
            skeleton.setShouldBurnInDay(false);
        }
        double spawnHealth = calculateSpawnHealth(definition.health(), location);
        activeVirtualMaxHealth = spawnHealth;
        activeVirtualHealth = spawnHealth;
        persistActiveState();
        double spawnScale = calculateSpawnScale(definition);
        scaleHelper.setMaxHealth(living, NATIVE_BOSS_HEALTH);
        scaleHelper.applyCombatDefense(living, 0.0D, 0.0D, config.bossKnockbackResistance());
        scaleHelper.apply(living, spawnScale);
        if (living instanceof Mob mob) mob.setTarget(findBestTarget(location));
        bossBarManager.create(definition);
        damageTracker.clear();
        skillManager.start(definition);
        notifyConsole("spawned: " + definition.id() + " virtual-health=" + (int) spawnHealth + " size=" + String.format(Locale.US, "%.2f", spawnScale));
        plugin.getEffects().sound(location, config.sound("effects.spawn.sound", "ENTITY_WARDEN_ROAR"), 1.0F, 0.8F);
        plugin.getEffects().spiral(location, config.particle("effects.spawn.particle", "SOUL"), 2.4D, Math.min(24, config.spawnParticleCount() / 4), 2.5D);
        if (config.spawnTitleEnabled()) sendSpawnTitle(definition, location);
        if (announce && config.announceSpawn()) notifyPlayers(config.color(config.message("boss-spawn", "%prefix% &c%boss% &7muncul di &f%world% %x% %y% %z%&7!")
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
        long now = System.currentTimeMillis();
        if (now - lastMinionCleanupAt >= 5_000L) {
            lastMinionCleanupAt = now;
            cleanupMinionsByArena();
        }
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
        retarget(true);
    }

    private void cleanupMinionsByArena() {
        if (arenaCenter == null || arenaCenter.getWorld() == null) return;
        double scanRadius = config.minionScanRadius();
        for (Entity entity : arenaCenter.getWorld().getNearbyEntities(arenaCenter, scanRadius, 48, scanRadius)) {
            if (!entity.getScoreboardTags().contains("velioraboss_minion")) continue;
            boolean outside = !entity.getWorld().equals(arenaCenter.getWorld()) || horizontalDistance(entity.getLocation(), arenaCenter) > config.arenaRadius();
            if (outside && config.removeMinionOutsideRadius()) entity.remove();
            else if (entity instanceof Mob mob && (mob.getTarget() == null || !(mob.getTarget() instanceof Player player) || !targetManager.isValidCurrentTarget(player, entity.getLocation(), arenaCenter))) mob.setTarget(findBestTarget(entity.getLocation()));
        }
    }

    private void sendSpawnWarnings() {
        if (nextSpawnAt <= 0L) return;
        long millis = nextSpawnAt - System.currentTimeMillis();
        if (millis <= 0) return;
        for (int minute : config.warningTimesMinutes()) {
            if (sentWarnings.contains(minute)) continue;
            if (millis <= minute * 60_000L) {
                String key = "boss-warning-" + minute;
                String fallback = minute == 1 ? "%prefix% &cBoss akan muncul dalam &f1 menit&c!" : "%prefix% &eBoss akan muncul dalam &f" + minute + " menit&e!";
                notifyPlayers(config.color(config.message(key, fallback)));
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

    private double calculateSpawnScale(BossDefinition definition) {
        if (!config.randomScaleEnabled()) return definition.scale();
        double min = config.randomScaleMin();
        double max = config.randomScaleMax();
        if (max <= min) return min;
        return min + (random.nextDouble() * (max - min));
    }

    private double calculateSpawnHealth(double baseHealth, Location location) {
        double cappedBaseHealth = Math.min(config.maximumBossHealth(), Math.max(1.0D, baseHealth * config.globalHealthMultiplier()));
        if (!config.healthScalingEnabled()) return cappedBaseHealth;
        int nearbyPlayers = Math.max(1, targetManager.validPlayers(location, location, config.targetRadius()).size());
        double multiplier = Math.min(config.maxHealthMultiplier(), 1.0D + ((nearbyPlayers - 1) * config.healthPerPlayerMultiplier()));
        return Math.min(config.maximumBossHealth(), Math.max(cappedBaseHealth, cappedBaseHealth * multiplier));
    }

    /** Applies changed boss.yml values to the currently spawned boss without deleting it. */
    private void refreshActiveBoss() {
        if (!isActive() || activeDefinition == null) return;

        BossDefinition refreshed = config.bosses().get(activeDefinition.id());
        if (refreshed == null) {
            plugin.getLogger().warning("VelioraBoss: boss aktif '" + activeDefinition.id() + "' tidak ada lagi di modules/boss.yml; boss dihentikan.");
            stopActive(false);
            return;
        }

        double healthPercent = activeVirtualMaxHealth <= 0.0D ? 1.0D : Math.max(0.0D, Math.min(1.0D, activeVirtualHealth / activeVirtualMaxHealth));
        activeDefinition = refreshed;
        activeBoss.setCustomName(config.color(refreshed.displayName()));
        activeBoss.setCollidable(config.bossCollisionEnabled());
        activeBoss.getPersistentDataContainer().set(bossNameKey, PersistentDataType.STRING, org.bukkit.ChatColor.stripColor(config.color(refreshed.displayName())));
        activeBoss.getPersistentDataContainer().set(bossRarityKey, PersistentDataType.STRING, refreshed.rarity().name());

        double refreshedHealth = calculateSpawnHealth(refreshed.health(), activeBoss.getLocation());
        activeVirtualMaxHealth = refreshedHealth;
        activeVirtualHealth = refreshedHealth * healthPercent;
        scaleHelper.setMaxHealth(activeBoss, NATIVE_BOSS_HEALTH);
        scaleHelper.applyCombatDefense(activeBoss, 0.0D, 0.0D, config.bossKnockbackResistance());
        scaleHelper.apply(activeBoss, calculateSpawnScale(refreshed));
        bossBarManager.create(refreshed);
        skillManager.start(refreshed);
        retarget(true);
    }

    private void rescheduleSpawnRetry(String reason) {
        nextSpawnAt = System.currentTimeMillis() + (config.spawnRetryMinutes() * 60_000L);
        sentWarnings.clear();
        notifyConsole(reason + ", retry dalam " + config.spawnRetryMinutes() + " menit. Cek spawn point, world, dan entity boss.");
    }

    private void infoLine(CommandSender sender, String key, String value) {
        sender.sendMessage(config.color(config.message("boss-info-line", "&7%key%: &f%value%").replace("%key%", key).replace("%value%", value)));
    }

    private void notifyPlayers(String message) {
        if (!config.playerNotificationsEnabled()) return;
        for (Player player : Bukkit.getOnlinePlayers()) player.sendMessage(message);
    }

    private void notifyConsole(String message) {
        if (config.consoleNotificationsEnabled()) plugin.getLogger().info("VelioraBoss: " + message);
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
        lastMinionCleanupAt = 0L;
        lastHitEffects.clear();
        activeVirtualHealth = 0.0D;
        activeVirtualMaxHealth = 0.0D;
        releaseBossChunk();
    }

    public double activeBossHealthPercent() {
        return activeVirtualMaxHealth <= 0.0D ? 1.0D : Math.max(0.0D, Math.min(1.0D, activeVirtualHealth / activeVirtualMaxHealth));
    }

    public boolean healActiveBoss(double maxHealthPercent) {
        if (!isActive() || maxHealthPercent <= 0.0D) return false;
        activeVirtualHealth = Math.min(activeVirtualMaxHealth, activeVirtualHealth + (activeVirtualMaxHealth * maxHealthPercent));
        return true;
    }

    private void damageActiveBoss(Player player, double damage) {
        if (!isActive() || player == null || damage <= 0.0D) return;
        double applied = Math.min(activeVirtualHealth, damage);
        activeVirtualHealth -= applied;
        persistActiveState();
        damageTracker.add(player, applied);
        data.addDamage(player, applied);
        if (activeVirtualHealth <= 0.0D) activeBoss.setHealth(0.0D);
    }

    /** Shows a real hit reaction even though damage is stored in virtual health. */
    private AttackKind attackKind(Entity damager, Player player) {
        // Only a direct player swing may count as a mace smash. Projectiles must
        // never inherit the item the shooter happens to hold after firing.
        if (damager instanceof Player) {
            return player.getInventory().getItemInMainHand().getType() == Material.MACE
                    ? AttackKind.MACE : AttackKind.MELEE;
        }
        if (damager instanceof Trident) return AttackKind.TRIDENT;
        if (damager instanceof Projectile) return AttackKind.ARROW;
        return AttackKind.MELEE;
    }

    private void showHitFeedback(Player player, AttackKind attack, boolean chargedMace, double damage) {
        if (!isActive()) return;
        try { activeBoss.playHurtAnimation(player.getLocation().getYaw()); } catch (Exception ignored) { }
        String weapon = switch (attack) {
            case MACE -> chargedMace ? "MACE SMASH CRIT" : "MACE HIT";
            case TRIDENT -> "TRIDENT HIT";
            case ARROW -> "PANAH HIT";
            case MELEE -> "MELEE HIT";
        };
        player.sendActionBar(Component.text(String.format(Locale.US, "%s  -%.1f HP  |  Boss: %.0f/%.0f", weapon, damage, activeVirtualHealth, activeVirtualMaxHealth)));
        long now = System.currentTimeMillis();
        Long lastEffect = lastHitEffects.put(player.getUniqueId(), now);
        if (lastEffect != null && now - lastEffect < 80L) return;
        Location hit = activeBoss.getLocation().add(0.0D, Math.max(0.8D, activeBoss.getHeight() * 0.55D), 0.0D);
        boolean mace = attack == AttackKind.MACE;
        boolean ranged = attack == AttackKind.ARROW || attack == AttackKind.TRIDENT;
        Particle particle = mace || ranged ? Particle.CRIT : Particle.DAMAGE_INDICATOR;
        int amount = mace ? (chargedMace ? 32 : 18) : attack == AttackKind.TRIDENT ? 20 : ranged ? 14 : 10;
        spawnHitParticle(particle, hit, amount, 0.72D, 0.85D, 0.72D, 0.12D);
        if (chargedMace) spawnHitParticle(Particle.FLASH, hit, 2, 0.3D, 0.3D, 0.3D, 0.0D);
        Sound sound = mace ? (chargedMace ? Sound.ENTITY_GENERIC_EXPLODE : Sound.ENTITY_PLAYER_ATTACK_CRIT)
                : attack == AttackKind.TRIDENT ? Sound.ITEM_TRIDENT_HIT
                : ranged ? Sound.ENTITY_ARROW_HIT_PLAYER : Sound.ENTITY_PLAYER_ATTACK_CRIT;
        activeBoss.getWorld().playSound(hit, sound, mace ? (chargedMace ? 0.85F : 0.75F) : ranged ? 0.65F : 0.45F,
                mace ? (chargedMace ? 1.15F : 1.65F) : attack == AttackKind.TRIDENT ? 1.05F : ranged ? 1.25F : 1.15F);
    }

    private enum AttackKind { MELEE, MACE, ARROW, TRIDENT }

    /** Paper may change a particle's required payload between minor versions.
     * A visual effect must never break the boss damage event. */
    private void spawnHitParticle(Particle particle, Location location, int count, double offsetX, double offsetY, double offsetZ, double extra) {
        try {
            activeBoss.getWorld().spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, extra);
        } catch (IllegalArgumentException ignored) {
            activeBoss.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, location, Math.max(4, count / 2), offsetX, offsetY, offsetZ, 0.02D);
        }
    }

    /** Sends a small orbiting aura only every two seconds and scales it down when the arena is busy. */
    private void emitAura() {
        if (activeBoss == null || activeDefinition == null || (System.currentTimeMillis() / 1000L) % 2L != 0L) return;
        List<Player> viewers = nearbyTargetPlayers(activeBoss.getLocation(), config.bossBarRadius());
        int count = viewers.size() > 12 ? 2 : viewers.size() > 6 ? 4 : 8;
        Particle particle = activeDefinition.rarity() == BossRarity.MYTHIC ? Particle.SOUL_FIRE_FLAME : Particle.SOUL;
        Location center = activeBoss.getLocation().add(0.0D, Math.min(2.6D, Math.max(1.1D, activeBoss.getHeight() * 0.34D)), 0.0D);
        double phase = (System.currentTimeMillis() % 4000L) / 4000.0D * Math.PI * 2.0D;
        for (Player viewer : viewers) {
            for (int point = 0; point < count; point++) {
                double angle = phase + Math.PI * 2.0D * point / count;
                Location orbit = center.clone().add(Math.cos(angle) * 1.15D, Math.sin(angle * 2.0D) * 0.30D, Math.sin(angle) * 1.15D);
                viewer.spawnParticle(particle, orbit, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
        }
    }

    private void cleanupTaggedEntities() {
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity.getScoreboardTags().contains("velioraboss_boss") || entity.getScoreboardTags().contains("velioraboss_minion")) entity.remove();
            }
        }
    }

    /**
     * Expiration must keep running even when every player leaves the dungeon.
     * Only the boss chunk is held, so this is cheap and minions remain capped.
     */
    private void holdBossChunk(Chunk chunk) {
        if (chunk == null) return;
        if (forcedBossChunk != null && !forcedBossChunk.equals(chunk)) forcedBossChunk.setForceLoaded(false);
        forcedBossChunk = chunk;
        forcedBossChunk.setForceLoaded(true);
    }

    private void releaseBossChunk() {
        if (forcedBossChunk != null) forcedBossChunk.setForceLoaded(false);
        forcedBossChunk = null;
    }

    /** Removes old copies as soon as an old/unloaded chunk becomes available. */
    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        long now = System.currentTimeMillis();
        for (Entity entity : event.getEntities()) {
            if (entity.getScoreboardTags().contains("velioraboss_boss")) {
                Long expires = entity.getPersistentDataContainer().get(bossExpiresAtKey, PersistentDataType.LONG);
                boolean duplicate = activeBoss != null && !activeBoss.getUniqueId().equals(entity.getUniqueId());
                if (expires == null || expires <= now || duplicate) entity.remove();
                continue;
            }
            if (entity.getScoreboardTags().contains("velioraboss_minion")) {
                String owner = entity.getPersistentDataContainer().get(minionOwnerKey, PersistentDataType.STRING);
                if (activeDefinition == null || owner == null || !activeDefinition.id().equals(owner)) entity.remove();
            }
        }
    }

    /** Recovers one valid boss after reload/restart and removes only expired or duplicate copies. */
    private void recoverActiveBoss() {
        long now = System.currentTimeMillis();
        LivingEntity recovered = null;
        BossDefinition recoveredDefinition = null;
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity.getScoreboardTags().contains("velioraboss_minion")) {
                    entity.remove();
                    continue;
                }
                if (!(entity instanceof LivingEntity living) || !entity.getScoreboardTags().contains("velioraboss_boss")) continue;
                String bossId = living.getPersistentDataContainer().get(bossIdKey, PersistentDataType.STRING);
                Long expiresAt = living.getPersistentDataContainer().get(bossExpiresAtKey, PersistentDataType.LONG);
                BossDefinition definition = bossId == null ? null : config.bosses().get(bossId);
                if (definition == null || expiresAt == null || expiresAt <= now || recovered != null) {
                    living.remove();
                    continue;
                }
                recovered = living;
                recoveredDefinition = definition;
                despawnAt = expiresAt;
            }
        }
        if (recovered == null || recoveredDefinition == null) {
            clearRuntime();
            scheduleNextSpawn();
            return;
        }
        activeBoss = recovered;
        activeDefinition = recoveredDefinition;
        lastKnownLocation = recovered.getLocation();
        arenaCenter = recovered.getLocation().clone();
        holdBossChunk(recovered.getChunk());
        Double storedMax = recovered.getPersistentDataContainer().get(bossVirtualMaxHealthKey, PersistentDataType.DOUBLE);
        Double storedHealth = recovered.getPersistentDataContainer().get(bossVirtualHealthKey, PersistentDataType.DOUBLE);
        activeVirtualMaxHealth = storedMax == null ? calculateSpawnHealth(recoveredDefinition.health(), recovered.getLocation()) : Math.max(1.0D, storedMax);
        activeVirtualHealth = storedHealth == null ? activeVirtualMaxHealth : Math.max(1.0D, Math.min(activeVirtualMaxHealth, storedHealth));
        bossBarManager.create(recoveredDefinition);
        skillManager.start(recoveredDefinition);
        retarget(true);
        notifyConsole("recovered: " + recoveredDefinition.id() + " expires-in=" + timeLeft(despawnAt));
    }

    private void persistActiveState() {
        if (activeBoss == null || activeBoss.isDead()) return;
        activeBoss.getPersistentDataContainer().set(bossExpiresAtKey, PersistentDataType.LONG, despawnAt);
        activeBoss.getPersistentDataContainer().set(bossVirtualHealthKey, PersistentDataType.DOUBLE, activeVirtualHealth);
        activeBoss.getPersistentDataContainer().set(bossVirtualMaxHealthKey, PersistentDataType.DOUBLE, activeVirtualMaxHealth);
    }

    private void scheduleNextSpawn() {
        if (!config.isSpawnEnabled() || (config.requireSpawnPoint() && data.spawnPoints().isEmpty())) {
            nextSpawnAt = 0L;
            sentWarnings.clear();
            return;
        }
        nextSpawnAt = nextSpawnMillis();
        sentWarnings.clear();
    }

    private long nextSpawnMillis() {
        if (config.dailyScheduleEnabled()) {
            ZonedDateTime now = ZonedDateTime.now(JAKARTA_ZONE);
            ZonedDateTime nearest = null;
            for (String raw : config.dailySpawnTimes()) {
                try {
                    LocalTime time = LocalTime.parse(raw, DateTimeFormatter.ofPattern("HH:mm"));
                    ZonedDateTime candidate = now.withHour(time.getHour()).withMinute(time.getMinute()).withSecond(0).withNano(0);
                    if (!candidate.isAfter(now)) candidate = candidate.plusDays(1L);
                    if (nearest == null || candidate.isBefore(nearest)) nearest = candidate;
                } catch (DateTimeParseException ignored) {
                    plugin.getLogger().warning("VelioraBoss: jam tidak valid di settings.spawn.daily-times: " + raw);
                }
            }
            if (nearest != null) return nearest.toInstant().toEpochMilli();
        }
        int interval = config.intervalMinutes();
        if (interval == 60) {
            return ZonedDateTime.now(JAKARTA_ZONE).truncatedTo(ChronoUnit.HOURS).plusHours(1L).toInstant().toEpochMilli();
        }
        return System.currentTimeMillis() + interval * 60_000L;
    }

    private double horizontalDistance(Location a, Location b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private String normalizeId(String input) { return input.trim().toLowerCase(Locale.ROOT).replace(' ', '_'); }
    private String normalizeLoose(String input) { return input == null ? "" : config.plain(input).toLowerCase(Locale.ROOT).replace("_", "").replace("-", "").replace(" ", ""); }
    private void stopScheduler() { if (schedulerTask != null) schedulerTask.cancel(); schedulerTask = null; }

    private record MaceSmashState(int charges, long cooldownUntil) { }

    private String timeLeft(long target) {
        if (target <= 0L) return "belum aktif";
        long seconds = Math.max(0L, (target - System.currentTimeMillis()) / 1000L);
        return (seconds / 60L) + "m " + (seconds % 60L) + "s";
    }
}
