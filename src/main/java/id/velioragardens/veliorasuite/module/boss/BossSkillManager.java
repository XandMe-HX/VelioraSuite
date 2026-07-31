package id.velioragardens.veliorasuite.module.boss;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.module.boss.model.BossDefinition;
import id.velioragardens.veliorasuite.module.boss.model.BossSkillType;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public final class BossSkillManager {

    private final VelioraSuite plugin;
    private final BossConfigManager config;
    private final BossManager manager;
    private final Random random = new Random();
    private BukkitTask task;
    private BossDefinition definition;
    private boolean rageMode;

    public BossSkillManager(VelioraSuite plugin, BossConfigManager config, BossManager manager) {
        this.plugin = plugin;
        this.config = config;
        this.manager = manager;
    }

    public void start(BossDefinition definition) {
        stop();
        this.definition = definition;
        rageMode = false;
        if (!config.skillsEnabled()) return;
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::castRandomSkill, 20L * config.skillCooldownSeconds(), 20L * config.skillCooldownSeconds());
    }

    public void stop() {
        if (task != null) task.cancel();
        task = null;
        definition = null;
        rageMode = false;
    }

    public void cleanupMinions() {
        Location center = manager.getArenaCenter();
        if (center == null || center.getWorld() == null) center = manager.getLastKnownLocation();
        if (center == null || center.getWorld() == null) return;
        for (Entity entity : center.getWorld().getNearbyEntities(center, 96, 64, 96)) if (entity.getScoreboardTags().contains("velioraboss_minion")) entity.remove();
    }

    private void castRandomSkill() {
        LivingEntity boss = manager.getActiveBoss();
        if (boss == null || boss.isDead() || definition == null) return;
        if (definition.skills().contains(BossSkillType.RAGE_MODE)) maybeRage(boss);
        if (rageMode) healBoss(boss, config.rageHealPercentPerCast());
        List<BossSkillType> usable = new ArrayList<>();
        for (BossSkillType skill : definition.skills()) if (skill != BossSkillType.RAGE_MODE) usable.add(skill);
        if (usable.isEmpty()) return;
        BossSkillType skill = usable.get(random.nextInt(usable.size()));
        switch (skill) {
            case GROUND_SLAM -> groundSlam(boss);
            case SUMMON_MINIONS -> summonMinions(boss);
            case FIRE_BOMB -> fireBomb(boss);
            case PULL_AURA -> pullAura(boss);
            case POISON_CLOUD -> poisonCloud(boss);
            case LIGHTNING_CHAIN -> lightningChain(boss);
            case SHADOW_PULSE -> shadowPulse(boss);
            case HEAL_PULSE -> healPulse(boss);
            case SOUL_CAGE -> soulCage(boss);
            case RAGE_MODE -> maybeRage(boss);
        }
    }

    private void maybeRage(LivingEntity boss) {
        if (rageMode) return;
        if (boss.getHealth() > boss.getMaxHealth() * config.rageThreshold()) return;
        rageMode = true;
        boss.setGlowing(true);
        PotionEffectType speed = PotionEffectType.getByName("SPEED");
        PotionEffectType resistance = PotionEffectType.getByName("DAMAGE_RESISTANCE");
        if (speed != null) boss.addPotionEffect(new PotionEffect(speed, 20 * 60, 0));
        if (resistance != null) boss.addPotionEffect(new PotionEffect(resistance, 20 * 60, 0));
        healBoss(boss, config.healPulsePercent());
        boss.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, boss.getLocation().add(0.0D, 1.0D, 0.0D), 90, 2.5D, 1.2D, 2.5D, 0.05D);
        boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_WARDEN_ROAR, 1.3F, 0.65F);
        if (config.playerNotificationsEnabled()) {
            String message = config.color("&c" + boss.getCustomName() + " &cmemasuki Rage Mode! &7Damage meningkat dan boss mulai memulihkan HP.");
            for (Player player : nearbyPlayers(boss.getLocation(), config.targetingRadiusHorizontal())) player.sendMessage(message);
        }
    }

    public double outgoingDamageMultiplier() {
        return rageMode ? config.rageDamageMultiplier() : 1.0D;
    }

    private void groundSlam(LivingEntity boss) {
        Location location = boss.getLocation();
        location.getWorld().spawnParticle(Particle.CLOUD, location, 60, 3.0D, 0.4D, 3.0D, 0.05D);
        location.getWorld().playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 1.0F, 0.8F);
        for (Player player : nearbyPlayers(location, 6.0D)) {
            player.damage(config.groundSlamDamage() * outgoingDamageMultiplier());
            Vector knock = player.getLocation().toVector().subtract(location.toVector()).normalize().multiply(1.2D).setY(0.55D);
            player.setVelocity(knock);
        }
    }

    private void summonMinions(LivingEntity boss) {
        int current = countMinions(boss.getLocation());
        if (current >= config.maxMinions()) return;
        for (int i = 0; i < config.minionsPerCast() && current + i < config.maxMinions(); i++) {
            EntityType type = minionType();
            Location spawn = boss.getLocation().clone().add(random.nextInt(5) - 2, 0, random.nextInt(5) - 2);
            Entity entity = boss.getWorld().spawnEntity(spawn, type);
            entity.addScoreboardTag("velioraboss_minion");
            entity.getPersistentDataContainer().set(manager.getMinionOwnerKey(), PersistentDataType.STRING, manager.getActiveBossId());
            if (entity instanceof Mob mob) mob.setTarget(targetNear(boss.getLocation()));
        }
        boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_EVOKER_PREPARE_SUMMON, 1.0F, 1.0F);
    }

    private void fireBomb(LivingEntity boss) {
        List<Player> targets = nearbyPlayers(boss.getLocation(), config.targetingRadiusHorizontal());
        Location target = targets.isEmpty() ? boss.getLocation().clone().add(random.nextInt(9) - 4, 0, random.nextInt(9) - 4) : targets.get(random.nextInt(targets.size())).getLocation();
        target.getWorld().spawnParticle(Particle.FLAME, target, 40, 1.5D, 0.4D, 1.5D, 0.05D);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            target.getWorld().spawnParticle(Particle.EXPLOSION, target, 1);
            target.getWorld().playSound(target, Sound.ENTITY_GENERIC_EXPLODE, 1.0F, 1.0F);
            for (Player player : nearbyPlayers(target, 4.0D)) player.damage(config.fireBombDamage() * outgoingDamageMultiplier());
        }, 30L);
    }

    private void pullAura(LivingEntity boss) {
        Location location = boss.getLocation();
        location.getWorld().playSound(location, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0F, 0.6F);
        for (Player player : nearbyPlayers(location, 10.0D)) {
            Vector pull = location.toVector().subtract(player.getLocation().toVector()).normalize().multiply(0.7D).setY(0.25D);
            player.setVelocity(pull);
        }
    }

    private void poisonCloud(LivingEntity boss) {
        Location location = boss.getLocation();
        location.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, location, 80, 4.0D, 1.0D, 4.0D, 0.05D);
        PotionEffectType poison = PotionEffectType.getByName("POISON");
        PotionEffectType slow = PotionEffectType.getByName("SLOW");
        for (Player player : nearbyPlayers(location, 7.0D)) {
            if (poison != null) player.addPotionEffect(new PotionEffect(poison, 80, 0));
            if (slow != null) player.addPotionEffect(new PotionEffect(slow, 80, 0));
        }
    }

    /** Arena-wide ranged counter with a visible warning before damage lands. */
    private void lightningChain(LivingEntity boss) {
        List<Player> targets = new ArrayList<>(nearbyPlayers(boss.getLocation(), config.targetingRadiusHorizontal()));
        if (targets.isEmpty()) return;
        boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_WARDEN_SONIC_CHARGE, 1.0F, 1.15F);
        for (Player player : targets) {
            player.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, player.getLocation().add(0.0D, 1.0D, 0.0D), 18, 0.7D, 0.9D, 0.7D, 0.03D);
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!isCurrentBoss(boss)) return;
            for (Player player : targets) {
                if (!isValidDelayedTarget(player)) continue;
                player.getWorld().strikeLightningEffect(player.getLocation());
                player.damage(config.lightningChainDamage() * outgoingDamageMultiplier());
            }
        }, config.skillTelegraphTicks());
    }

    /** Scary arena pulse that reaches bow users but only deals moderate damage. */
    private void shadowPulse(LivingEntity boss) {
        Location center = boss.getLocation();
        center.getWorld().playSound(center, Sound.ENTITY_WITHER_AMBIENT, 1.0F, 0.55F);
        center.getWorld().spawnParticle(Particle.PORTAL, center.clone().add(0.0D, 1.0D, 0.0D), 140, 4.0D, 1.2D, 4.0D, 0.25D);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!isCurrentBoss(boss)) return;
            for (Player player : nearbyPlayers(center, config.targetingRadiusHorizontal())) {
                player.getWorld().spawnParticle(Particle.SOUL, player.getLocation(), 24, 0.8D, 0.5D, 0.8D, 0.04D);
                player.damage(config.shadowPulseDamage() * outgoingDamageMultiplier());
                Vector pull = boss.getLocation().toVector().subtract(player.getLocation().toVector());
                if (pull.lengthSquared() > 0.01D) player.setVelocity(pull.normalize().multiply(0.35D).setY(0.18D));
            }
        }, config.skillTelegraphTicks());
    }

    /** Marks every arena participant, then briefly slows and damages them. */
    private void soulCage(LivingEntity boss) {
        List<Player> targets = new ArrayList<>(nearbyPlayers(boss.getLocation(), config.targetingRadiusHorizontal()));
        if (targets.isEmpty()) return;
        boss.getWorld().playSound(boss.getLocation(), Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 1.0F, 0.8F);
        for (Player player : targets) {
            player.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, player.getLocation().add(0.0D, 0.8D, 0.0D), 28, 1.2D, 0.9D, 1.2D, 0.02D);
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!isCurrentBoss(boss)) return;
            PotionEffectType slow = PotionEffectType.getByName("SLOWNESS");
            if (slow == null) slow = PotionEffectType.getByName("SLOW");
            for (Player player : targets) {
                if (!isValidDelayedTarget(player)) continue;
                player.damage(config.soulCageDamage() * outgoingDamageMultiplier());
                if (slow != null) player.addPotionEffect(new PotionEffect(slow, 60, 0));
            }
        }, config.skillTelegraphTicks());
    }

    private void healPulse(LivingEntity boss) {
        boss.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, boss.getLocation().add(0.0D, 1.0D, 0.0D), 55, 1.6D, 1.0D, 1.6D, 0.04D);
        boss.getWorld().playSound(boss.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.9F, 0.75F);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (isCurrentBoss(boss)) healBoss(boss, config.healPulsePercent());
        }, config.skillTelegraphTicks());
    }

    private void healBoss(LivingEntity boss, double maxHealthPercent) {
        if (boss == null || boss.isDead() || maxHealthPercent <= 0.0D) return;
        double healed = Math.min(boss.getMaxHealth(), boss.getHealth() + boss.getMaxHealth() * maxHealthPercent);
        boss.setHealth(healed);
    }

    private boolean isCurrentBoss(LivingEntity boss) {
        LivingEntity active = manager.getActiveBoss();
        return active != null && !active.isDead() && active.getUniqueId().equals(boss.getUniqueId());
    }

    private boolean isValidDelayedTarget(Player player) {
        return player != null && player.isOnline() && !player.isDead() && manager.isPlayerInsideArena(player);
    }

    private int countMinions(Location center) {
        int amount = 0;
        for (Entity entity : center.getWorld().getNearbyEntities(center, 96, 64, 96)) if (entity.getScoreboardTags().contains("velioraboss_minion")) amount++;
        return amount;
    }

    private EntityType minionType() {
        List<String> types = config.minionTypes();
        String raw = types.get(random.nextInt(types.size()));
        try { return EntityType.valueOf(raw.toUpperCase(Locale.ROOT)); } catch (Exception ignored) { return EntityType.ZOMBIE; }
    }

    private Player targetNear(Location location) {
        return manager.findBestTarget(location);
    }

    private List<Player> nearbyPlayers(Location location, double horizontalRadius) {
        if (location == null || location.getWorld() == null) return List.of();
        return manager.nearbyTargetPlayers(location, horizontalRadius);
    }
