package id.velioragardens.veliorasuite.module.mobpanic;

import id.velioragardens.veliorasuite.VelioraSuite;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Sittable;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.io.File;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Bounded, event-driven herd panic that safely coexists with AggressiveAnimals. */
public final class MobPanicListener implements Listener {
    private static final String PET_TAG = "veliorapets_pet";
    private final VelioraSuite plugin;
    private final Map<UUID, Long> panickedAt = new HashMap<>();
    private final Set<UUID> pendingCenters = new java.util.HashSet<>();
    private Set<EntityType> herdTypes = EnumSet.noneOf(EntityType.class);
    private boolean enabled;
    private int radius;
    private int maxAnimals;
    private long cooldownMillis;
    private long delayTicks;
    private double fleeSpeed;
    private int speedDuration;
    private int speedAmplifier;
    private boolean affectTamed;
    private boolean affectNamed;
    private Particle particle;
    private int particleCount;
    private Sound sound;
    private float soundVolume;
    private float soundPitch;

    public MobPanicListener(VelioraSuite plugin) { this.plugin = plugin; }

    public void load() {
        FileConfiguration config = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "modules/mobpanic.yml"));
        enabled = config.getBoolean("settings.enabled", true);
        radius = clamp(config.getInt("settings.radius", 16), 1, 32);
        maxAnimals = clamp(config.getInt("settings.max-animals-per-trigger", 24), 1, 48);
        cooldownMillis = clamp(config.getLong("settings.cooldown-millis", 1800L), 250L, 10_000L);
        delayTicks = clamp(config.getLong("settings.delay-ticks", 1L), 0L, 20L);
        fleeSpeed = Math.max(.05D, Math.min(1.1D, config.getDouble("settings.flee-speed", .42D)));
        speedDuration = clamp(config.getInt("settings.speed-duration-ticks", 45), 1, 200);
        speedAmplifier = clamp(config.getInt("settings.speed-amplifier", 1), 0, 3);
        affectTamed = config.getBoolean("settings.affect-tamed", false);
        affectNamed = config.getBoolean("settings.affect-named", false);
        particle = parseParticle(config.getString("settings.particle", "CLOUD"));
        particleCount = clamp(config.getInt("settings.particle-count", 5), 0, 12);
        sound = parseSound(config.getString("settings.sound", "ENTITY_HORSE_AMBIENT"));
        soundVolume = (float) Math.max(0D, Math.min(1D, config.getDouble("settings.sound-volume", .35D)));
        soundPitch = (float) Math.max(.5D, Math.min(2D, config.getDouble("settings.sound-pitch", 1.35D)));
        Set<EntityType> parsed = EnumSet.noneOf(EntityType.class);
        for (String raw : config.getStringList("herd-types")) {
            try { parsed.add(EntityType.valueOf(raw.toUpperCase(java.util.Locale.ROOT))); }
            catch (IllegalArgumentException ignored) { plugin.getLogger().warning("MobPanic: EntityType tidak dikenal: " + raw); }
        }
        herdTypes = Set.copyOf(parsed);
        panickedAt.clear();
        pendingCenters.clear();
    }

    public boolean enabled() { return enabled; }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamaged(EntityDamageByEntityEvent event) {
        if (!enabled || !(event.getEntity() instanceof Animals hit) || !herdTypes.contains(hit.getType())) return;
        Player attacker = playerDamager(event.getDamager());
        if (attacker == null || !eligible(hit)) return;
        if (!pendingCenters.add(hit.getUniqueId())) return;
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            pendingCenters.remove(hit.getUniqueId());
            panicHerd(hit, attacker);
        }, delayTicks);
    }

    private void panicHerd(Animals center, Player attacker) {
        if (!center.isValid() || center.isDead() || !attacker.isOnline() || !center.getWorld().equals(attacker.getWorld())) return;
        long now = System.currentTimeMillis();
        int affected = 0;
        affected += panic(center, attacker, now) ? 1 : 0;
        for (Entity nearby : center.getNearbyEntities(radius, radius, radius)) {
            if (affected >= maxAnimals) break;
            if (!(nearby instanceof Animals animal) || animal.getType() != center.getType() || !eligible(animal)) continue;
            if (panic(animal, attacker, now)) affected++;
        }
        if (affected > 0 && sound != null) center.getWorld().playSound(center.getLocation(), sound, soundVolume, soundPitch);
        if (panickedAt.size() > 1024) panickedAt.entrySet().removeIf(entry -> now - entry.getValue() > cooldownMillis);
    }

    private boolean panic(Animals animal, Player attacker, long now) {
        if (now - panickedAt.getOrDefault(animal.getUniqueId(), 0L) < cooldownMillis) return false;
        Vector away = animal.getLocation().toVector().subtract(attacker.getLocation().toVector());
        away.setY(0D);
        if (away.lengthSquared() < .01D) away = attacker.getLocation().getDirection().multiply(-1D).setY(0D);
        if (away.lengthSquared() < .01D) away = new Vector(1D, 0D, 0D);
        away.normalize().multiply(fleeSpeed);
        away.setY(Math.max(.08D, animal.getVelocity().getY()));
        animal.setVelocity(away);
        animal.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, speedDuration, speedAmplifier, true, false, false));
        if (animal instanceof Mob mob && mob.getTarget() != null && mob.getTarget().getUniqueId().equals(attacker.getUniqueId())) mob.setTarget(null);
        if (particle != null && particleCount > 0) animal.getWorld().spawnParticle(particle, animal.getLocation().add(0D, .45D, 0D), particleCount, .18D, .18D, .18D, .01D);
        panickedAt.put(animal.getUniqueId(), now);
        return true;
    }

    private boolean eligible(Animals animal) {
        if (animal.getScoreboardTags().contains(PET_TAG) || animal.isLeashed() || animal.isInsideVehicle()) return false;
        if (!affectNamed && animal.customName() != null) return false;
        if (!affectTamed && animal instanceof Tameable tameable && tameable.isTamed()) return false;
        return !(animal instanceof Sittable sittable) || !sittable.isSitting();
    }

    private Player playerDamager(Entity damager) {
        if (damager instanceof Player player) return player;
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) return player;
        return null;
    }
    private Particle parseParticle(String raw) { try { return Particle.valueOf(raw.toUpperCase(java.util.Locale.ROOT)); } catch (RuntimeException ignored) { return Particle.CLOUD; } }
    private Sound parseSound(String raw) { try { return Sound.valueOf(raw.toUpperCase(java.util.Locale.ROOT)); } catch (RuntimeException ignored) { return Sound.ENTITY_HORSE_AMBIENT; } }
    private int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
    private long clamp(long value, long min, long max) { return Math.max(min, Math.min(max, value)); }
}
