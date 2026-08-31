package id.velioragardens.veliorasuite.module.race;

import id.velioragardens.veliorasuite.VelioraSuite;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/** Event-driven race bonuses. It has no repeating task and does not scan worlds or chunks. */
final class RaceBenefits implements Listener {
    private final RaceManager manager;
    private final NamespacedKey elfSpeed;
    private final NamespacedKey beastSpeed;
    private final NamespacedKey dwarfSpeed;
    private final NamespacedKey dwarfKnockback;
    private final NamespacedKey orcSpeed;
    private final NamespacedKey orcHealth;
    private final NamespacedKey orcKnockback;
    private final NamespacedKey goblinHealth;
    private final NamespacedKey vampireDaySpeed;
    private final Map<UUID, Long> angelRegenCooldown = new ConcurrentHashMap<>();
    private final Map<UUID, Long> vampireRegenCooldown = new ConcurrentHashMap<>();
    private final Map<UUID, Long> vampireLifestealCooldown = new ConcurrentHashMap<>();
    private final Map<UUID, Long> dragonLavaCooldown = new ConcurrentHashMap<>();
    private final Map<UUID, Long> passiveRefreshCooldown = new ConcurrentHashMap<>();

    RaceBenefits(VelioraSuite plugin, RaceManager manager) {
        this.manager = manager;
        elfSpeed = new NamespacedKey(plugin, "race_elf_speed");
        beastSpeed = new NamespacedKey(plugin, "race_beast_speed");
        dwarfSpeed = new NamespacedKey(plugin, "race_dwarf_speed");
        dwarfKnockback = new NamespacedKey(plugin, "race_dwarf_knockback");
        orcSpeed = new NamespacedKey(plugin, "race_orc_speed");
        orcHealth = new NamespacedKey(plugin, "race_orc_health");
        orcKnockback = new NamespacedKey(plugin, "race_orc_knockback");
        goblinHealth = new NamespacedKey(plugin, "race_goblin_health");
        vampireDaySpeed = new NamespacedKey(plugin, "race_vampire_day_speed");
    }

    void applyPassive(Player player) {
        clearPassive(player);
        if (!manager.selected(player.getUniqueId())) return;
        switch (race(player)) {
            case "ELF" -> modifier(player, Attribute.MOVEMENT_SPEED, elfSpeed, 0.15D);
            case "BEASTMAN" -> modifier(player, Attribute.MOVEMENT_SPEED, beastSpeed, 0.21D);
            case "DWARF" -> {
                modifier(player, Attribute.MOVEMENT_SPEED, dwarfSpeed, -0.12D);
                modifier(player, Attribute.KNOCKBACK_RESISTANCE, dwarfKnockback, 0.50D);
            }
            case "GOBLIN" -> modifier(player, Attribute.MAX_HEALTH, goblinHealth, -8.0D, AttributeModifier.Operation.ADD_NUMBER);
            case "ORC" -> {
                modifier(player, Attribute.MAX_HEALTH, orcHealth, 8.0D, AttributeModifier.Operation.ADD_NUMBER);
                modifier(player, Attribute.MOVEMENT_SPEED, orcSpeed, -0.18D);
                modifier(player, Attribute.KNOCKBACK_RESISTANCE, orcKnockback, 0.50D);
            }
            case "VAMPIRE" -> { if (isDay(player)) modifier(player, Attribute.MOVEMENT_SPEED, vampireDaySpeed, -0.15D); }
            default -> { }
        }
    }

    void clearPassive(Player player) {
        remove(player, Attribute.MOVEMENT_SPEED, elfSpeed);
        remove(player, Attribute.MOVEMENT_SPEED, beastSpeed);
        remove(player, Attribute.MOVEMENT_SPEED, dwarfSpeed);
        remove(player, Attribute.KNOCKBACK_RESISTANCE, dwarfKnockback);
        remove(player, Attribute.MOVEMENT_SPEED, orcSpeed);
        remove(player, Attribute.MAX_HEALTH, orcHealth);
        remove(player, Attribute.KNOCKBACK_RESISTANCE, orcKnockback);
        remove(player, Attribute.MAX_HEALTH, goblinHealth);
        remove(player, Attribute.MOVEMENT_SPEED, vampireDaySpeed);
    }

    /** Clears attributes and per-player cooldown state when a player leaves or is reset. */
    void forget(Player player) {
        clearPassive(player);
        angelRegenCooldown.remove(player.getUniqueId());
        vampireRegenCooldown.remove(player.getUniqueId());
        vampireLifestealCooldown.remove(player.getUniqueId());
        dragonLavaCooldown.remove(player.getUniqueId());
        passiveRefreshCooldown.remove(player.getUniqueId());
    }

    private void modifier(Player player, Attribute attribute, NamespacedKey key, double amount) {
        modifier(player, attribute, key, amount, AttributeModifier.Operation.ADD_SCALAR);
    }
    private void modifier(Player player, Attribute attribute, NamespacedKey key, double amount, AttributeModifier.Operation operation) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null || instance.getModifier(key) != null) return;
        instance.addModifier(new AttributeModifier(key, amount, operation));
    }
    private void remove(Player player, Attribute attribute, NamespacedKey key) {
        AttributeInstance instance = player.getAttribute(attribute);
        AttributeModifier modifier = instance == null ? null : instance.getModifier(key);
        if (modifier != null) instance.removeModifier(modifier);
    }
    private String race(Player player) { return manager.race(player.getUniqueId()).toUpperCase(Locale.ROOT); }
    private boolean isDay(Player player) { long time = player.getWorld().getTime(); return time >= 0L && time < 12300L; }
    private boolean isNight(Player player) { long time = player.getWorld().getTime(); return time >= 13000L && time < 23000L; }

    @EventHandler(ignoreCancelled = true) public void experience(PlayerExpChangeEvent event) {
        if (!manager.selected(event.getPlayer().getUniqueId())) return;
        double multiplier = switch (race(event.getPlayer())) { case "HUMAN" -> 1.24D; case "ANGEL" -> 1.15D; default -> 1.0D; };
        if (multiplier != 1.0D) event.setAmount((int) Math.ceil(event.getAmount() * multiplier));
    }
    @EventHandler(ignoreCancelled = true) public void blockExperience(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!manager.selected(player.getUniqueId())) return;
        String race = race(player);
        if (race.equals("DWARF")) event.setExpToDrop((int) Math.ceil(event.getExpToDrop() * 1.36D));
        if (race.equals("GOBLIN")) event.setExpToDrop((int) Math.ceil(event.getExpToDrop() * 1.30D));
    }
    @EventHandler(ignoreCancelled = true) public void itemDamage(PlayerItemDamageEvent event) {
        if (!manager.selected(event.getPlayer().getUniqueId())) return;
        String race = race(event.getPlayer());
        if ((race.equals("DWARF") || race.equals("GOBLIN")) && ThreadLocalRandom.current().nextInt(100) < 24) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true) public void damage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || !manager.selected(player.getUniqueId())) return;
        String race = race(player);
        if (race.equals("ANGEL") && event.getCause() == EntityDamageEvent.DamageCause.FALL) { event.setCancelled(true); return; }
        if (race.equals("BEASTMAN") && event.getCause() == EntityDamageEvent.DamageCause.FALL) event.setDamage(event.getDamage() * 0.55D);
        if (race.equals("DEMON") && isDay(player)) event.setDamage(event.getDamage() * 1.15D);
        if (race.equals("DEMON") && (event.getCause() == EntityDamageEvent.DamageCause.FIRE || event.getCause() == EntityDamageEvent.DamageCause.FIRE_TICK || event.getCause() == EntityDamageEvent.DamageCause.LAVA || event.getCause() == EntityDamageEvent.DamageCause.HOT_FLOOR)) event.setCancelled(true);
        if (race.equals("DRAGONKIN") && isFire(event.getCause())) {
            long now = System.currentTimeMillis();
            if (now >= dragonLavaCooldown.getOrDefault(player.getUniqueId(), 0L)) {
                dragonLavaCooldown.put(player.getUniqueId(), now + 45_000L);
                player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 240, 0, true, false, true));
                event.setCancelled(true);
                player.sendActionBar(net.kyori.adventure.text.Component.text("Sisik naga melindungimu dari api selama 12 detik."));
            }
        }
    }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true) public void attack(EntityDamageByEntityEvent event) {
        Player player = event.getDamager() instanceof Player direct ? direct : event.getDamager() instanceof org.bukkit.entity.Projectile projectile && projectile.getShooter() instanceof Player shooter ? shooter : null;
        if (player == null || !manager.selected(player.getUniqueId())) return;
        String race = race(player);
        boolean bow = event.getDamager() instanceof org.bukkit.entity.Projectile;
        if (race.equals("ELF") && bow) event.setDamage(event.getDamage() * 1.24D);
        if (race.equals("BEASTMAN") && !bow) event.setDamage(event.getDamage() * 1.18D);
        if (race.equals("BEASTMAN") && bow) event.setDamage(event.getDamage() * 0.88D);
        if (race.equals("DEMON") && isNight(player)) event.setDamage(event.getDamage() * 1.24D);
        if (race.equals("ANGEL") && !bow && isNight(player)) event.setDamage(event.getDamage() * 0.88D);
        if (race.equals("ORC") && !bow) event.setDamage(event.getDamage() * 1.21D);
        if (race.equals("DRAGONKIN") && event.getEntity() instanceof org.bukkit.entity.Monster) event.setDamage(event.getDamage() * 1.18D);
        if (race.equals("VAMPIRE") && event.getEntity() instanceof org.bukkit.entity.Monster) {
            long now = System.currentTimeMillis();
            if (now >= vampireLifestealCooldown.getOrDefault(player.getUniqueId(), 0L)) {
                vampireLifestealCooldown.put(player.getUniqueId(), now + 8_000L);
                AttributeInstance health = player.getAttribute(Attribute.MAX_HEALTH);
                player.setHealth(Math.min(health == null ? 20.0D : health.getValue(), player.getHealth() + 3.0D));
            }
        }
    }
    @EventHandler(ignoreCancelled = true) public void angelMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!manager.selected(player.getUniqueId())) return;
        long now = System.currentTimeMillis();
        if (now - passiveRefreshCooldown.getOrDefault(player.getUniqueId(), 0L) >= 5_000L) { applyPassive(player); passiveRefreshCooldown.put(player.getUniqueId(), now); }
        if (race(player).equals("ANGEL") && isDay(player) && now - angelRegenCooldown.getOrDefault(player.getUniqueId(), 0L) >= 5_000L) {
            angelRegenCooldown.put(player.getUniqueId(), now);
            player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 0, true, false, true));
        }
        if (race(player).equals("VAMPIRE") && isNight(player)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 220, 0, true, false, true));
            if (now - vampireRegenCooldown.getOrDefault(player.getUniqueId(), 0L) >= 5_000L) { vampireRegenCooldown.put(player.getUniqueId(), now); player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 1, true, false, true)); }
        }
    }
    @EventHandler(ignoreCancelled = true) public void hunger(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player) || !manager.selected(player.getUniqueId()) || !race(player).equals("DRAGONKIN")) return;
        if (event.getFoodLevel() < player.getFoodLevel() && ThreadLocalRandom.current().nextInt(100) < 23) event.setFoodLevel(Math.max(0, event.getFoodLevel() - 1));
    }
    private boolean isFire(EntityDamageEvent.DamageCause cause) { return cause == EntityDamageEvent.DamageCause.FIRE || cause == EntityDamageEvent.DamageCause.FIRE_TICK || cause == EntityDamageEvent.DamageCause.LAVA || cause == EntityDamageEvent.DamageCause.HOT_FLOOR; }
}
