package id.velioragardens.veliorasuite.module.boss;

import id.velioragardens.veliorasuite.VelioraSuite;
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
    private boolean rageMode;

    public BossSkillManager(VelioraSuite plugin, BossConfigManager config, BossManager manager) {
        this.plugin = plugin;
        this.config = config;
        this.manager = manager;
    }

    public void start() {
        stop();
        rageMode = false;
        if (!config.skillsEnabled()) return;
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::castRandomSkill, 20L * config.skillCooldownSeconds(), 20L * config.skillCooldownSeconds());
    }

    public void stop() {
        if (task != null) task.cancel();
        task = null;
        rageMode = false;
    }

    public void cleanupMinions() {
        LivingEntity boss = manager.getActiveBoss();
        Location center = boss == null ? manager.getLastKnownLocation() : boss.getLocation();
        if (center == null || center.getWorld() == null) return;
        for (Entity entity : center.getWorld().getNearbyEntities(center, 96, 64, 96)) {
            if (entity.getScoreboardTags().contains("velioraboss_minion")) entity.remove();
        }
    }

    private void castRandomSkill() {
        LivingEntity boss = manager.getActiveBoss();
        if (boss == null || boss.isDead()) return;
        maybeRage(boss);
        switch (random.nextInt(5)) {
            case 0 -> groundSlam(boss);
            case 1 -> summonMinions(boss);
            case 2 -> fireBomb(boss);
            case 3 -> pullAura(boss);
            default -> poisonCloud(boss);
        }
    }

    private void maybeRage(LivingEntity boss) {
        if (rageMode) return;
        if (boss.getHealth() > boss.getMaxHealth() * 0.30D) return;
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
        Location target = boss.getLocation().clone().add(random.nextInt(9) - 4, 0, random.nextInt(9) - 4);
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
        List<Player> players = nearbyPlayers(location, 24.0D);
        return players.isEmpty() ? null : players.get(random.nextInt(players.size()));
    }

    private List<Player> nearbyPlayers(Location location, double radius) {
        List<Player> players = new ArrayList<>();
        if (location == null || location.getWorld() == null) return players;
        double squared = radius * radius;
        for (Player player : location.getWorld().getPlayers()) if (player.getLocation().distanceSquared(location) <= squared) players.add(player);
        return players;
    }
}
