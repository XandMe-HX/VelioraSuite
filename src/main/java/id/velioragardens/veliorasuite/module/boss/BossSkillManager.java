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
    private BukkitTask skillTask;
    private BukkitTask regenerationTask;
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
        if (config.skillsEnabled()) {
            skillTask = plugin.getServer().getScheduler().runTaskTimer(
                    plugin,
                    this::castRandomSkill,
                    20L * config.skillCooldownSeconds(),
                    20L * config.skillCooldownSeconds());
        }
        if (config.regenerationEnabled()) {
            regenerationTask = plugin.getServer().getScheduler().runTaskTimer(
                    plugin,
                    this::regenerateBoss,
                    20L * config.regenerationIntervalSeconds(),
                    20L * config.regenerationIntervalSeconds());
        }
    }

    public void stop() {
        if (skillTask != null) skillTask.cancel();
        if (regenerationTask != null) regenerationTask.cancel();
        skillTask = null;
        regenerationTask = null;
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
            case HEAL_PULSE -> healBoss(boss, config.healPulsePercent(), true);
            case SOUL_CAGE -> soulCage(boss);
            case RAGE_MODE -> maybeRage(boss);
        }
    }

    private void regenerateBoss() {
        LivingEntity boss = manager.getActiveBoss();
        if (boss == null || boss.isDead()) return;
        healBoss(boss, config.regenerationPercent(), false);
    }

    private void maybeRage(LivingEntity boss) {
        if (rageMode) return;
        if (manager.getActiveHealth() > manager.getActiveMaxHealth() * 0.30D) return;
        rageMode = true;
        boss.setGlowing(true);
        PotionEffectType speed = PotionEffectType.getByName("SPEED");
        PotionEffectType resistance = PotionEffectType.getByName("DAMAGE_RESISTANCE");
        if (speed != null) boss.addPotionEffect(new PotionEffect(speed, 20 * 60, 0));
        if (resistance != null) boss.addPotionEffect(new PotionEffect(resistance, 20 * 60, 0));
        plugin.getServer().broadcastMessage(config.color("&c" + boss.getCustomName() + " &cmemasuki Rage Mode!"));
    }

    private void groundSlam(LivingEntity boss) {
        Location location = boss.getLocation();
        location.getWorld().spawnParticle(Particle.CLOUD, location, 60, 3.0D, 0.4D, 3.0D, 0.05D);
        location.getWorld().playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 1.0F, 0.8F);
        for (Player player : nearbyPlayers(location, 6.0D)) {
            player.damage(config.groundSlamDamage(), boss);
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
            target.getWorld().createExplosion(target, 2.0F, false, false, boss);
            for (Player player : nearbyPlayers(target, 4.0D)) player.damage(config.fireBombDamage(), boss);
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

    private void lightningChain(LivingEntity boss) {
        List<Player> targets = nearbyPlayers(boss.getLocation(), config.targetingRadiusHorizontal());
        if (targets.isEmpty()) return;
        targets.sort((left, right) -> Double.compare(
                left.getLocation().distanceSquared(boss.getLocation()),
                right.getLocation().distanceSquared(boss.getLocation())));
        int limit = Math.min(config.lightningChainMaxTargets(), targets.size());
        for (int i = 0; i < limit; i++) {
            Player target = targets.get(i);
            target.getWorld().strikeLightningEffect(target.getLocation());
            target.damage(config.lightningChainDamage(), boss);
        }
    }

    private void shadowPulse(LivingEntity boss) {
        Location location = boss.getLocation();
        location.getWorld().spawnParticle(Particle.SOUL, location, 90, 5.0D, 1.2D, 5.0D, 0.08D);
        location.getWorld().playSound(location, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.8F, 0.7F);
        PotionEffectType darkness = PotionEffectType.getByName("DARKNESS");
        for (Player player : nearbyPlayers(location, 8.0D)) {
            player.damage(config.shadowPulseDamage(), boss);
            if (darkness != null) player.addPotionEffect(new PotionEffect(darkness, 60, 0));
            Vector knock = player.getLocation().toVector().subtract(location.toVector()).normalize().multiply(0.75D).setY(0.25D);
            player.setVelocity(knock);
        }
    }

    private void soulCage(LivingEntity boss) {
        Player target = targetNear(boss.getLocation());
        if (target == null) return;
        Location center = target.getLocation().clone();
        center.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, center, 80, 2.0D, 1.2D, 2.0D, 0.04D);
        center.getWorld().playSound(center, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.9F, 0.6F);
        target.damage(config.soulCageDamage(), boss);
        PotionEffectType slowness = PotionEffectType.getByName("SLOWNESS");
        PotionEffectType weakness = PotionEffectType.getByName("WEAKNESS");
        int duration = config.soulCageDurationSeconds() * 20;
        if (slowness != null) target.addPotionEffect(new PotionEffect(slowness, duration, 1));
        if (weakness != null) target.addPotionEffect(new PotionEffect(weakness, duration, 0));
    }

    private void healBoss(LivingEntity boss, double percent, boolean showEffect) {
        if (!manager.healActiveBoss(percent) || !showEffect) return;
        boss.getWorld().spawnParticle(Particle.HEART, boss.getLocation().add(0.0D, 1.5D, 0.0D), 20, 1.0D, 1.0D, 1.0D, 0.05D);
        boss.getWorld().playSound(boss.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.8F, 1.3F);
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
}
