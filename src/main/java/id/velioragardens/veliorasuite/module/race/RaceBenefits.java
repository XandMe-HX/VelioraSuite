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
    private final Map<UUID, Long> angelRegenCooldown = new ConcurrentHashMap<>();

    RaceBenefits(VelioraSuite plugin, RaceManager manager) {
        this.manager = manager;
        elfSpeed = new NamespacedKey(plugin, "race_elf_speed");
        beastSpeed = new NamespacedKey(plugin, "race_beast_speed");
        dwarfSpeed = new NamespacedKey(plugin, "race_dwarf_speed");
        dwarfKnockback = new NamespacedKey(plugin, "race_dwarf_knockback");
    }

    void applyPassive(Player player) {
        clearPassive(player);
        if (!manager.selected(player.getUniqueId())) return;
        switch (race(player)) {
            case "ELF" -> modifier(player, Attribute.MOVEMENT_SPEED, elfSpeed, 0.10D);
            case "BEASTMAN" -> modifier(player, Attribute.MOVEMENT_SPEED, beastSpeed, 0.14D);
            case "DWARF" -> {
                modifier(player, Attribute.MOVEMENT_SPEED, dwarfSpeed, -0.08D);
                modifier(player, Attribute.KNOCKBACK_RESISTANCE, dwarfKnockback, 0.35D);
            }
            default -> { }
        }
    }

    void clearPassive(Player player) {
        remove(player, Attribute.MOVEMENT_SPEED, elfSpeed);
        remove(player, Attribute.MOVEMENT_SPEED, beastSpeed);
        remove(player, Attribute.MOVEMENT_SPEED, dwarfSpeed);
        remove(player, Attribute.KNOCKBACK_RESISTANCE, dwarfKnockback);
        angelRegenCooldown.remove(player.getUniqueId());
    }

    private void modifier(Player player, Attribute attribute, NamespacedKey key, double amount) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null || instance.getModifier(key) != null) return;
        instance.addModifier(new AttributeModifier(key, amount, AttributeModifier.Operation.ADD_SCALAR));
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
        double multiplier = switch (race(event.getPlayer())) { case "HUMAN" -> 1.16D; case "ANGEL" -> 1.10D; default -> 1.0D; };
        if (multiplier != 1.0D) event.setAmount((int) Math.ceil(event.getAmount() * multiplier));
    }
    @EventHandler(ignoreCancelled = true) public void blockExperience(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (manager.selected(player.getUniqueId()) && race(player).equals("DWARF")) event.setExpToDrop((int) Math.ceil(event.getExpToDrop() * 1.24D));
    }
    @EventHandler(ignoreCancelled = true) public void itemDamage(PlayerItemDamageEvent event) {
        if (manager.selected(event.getPlayer().getUniqueId()) && race(event.getPlayer()).equals("DWARF") && ThreadLocalRandom.current().nextInt(100) < 16) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true) public void damage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || !manager.selected(player.getUniqueId())) return;
        String race = race(player);
        if (race.equals("ANGEL") && event.getCause() == EntityDamageEvent.DamageCause.FALL) { event.setCancelled(true); return; }
        if (race.equals("BEASTMAN") && event.getCause() == EntityDamageEvent.DamageCause.FALL) event.setDamage(event.getDamage() * 0.70D);
        if (race.equals("DEMON") && isDay(player)) event.setDamage(event.getDamage() * 1.10D);
        if (race.equals("DEMON") && (event.getCause() == EntityDamageEvent.DamageCause.FIRE || event.getCause() == EntityDamageEvent.DamageCause.FIRE_TICK || event.getCause() == EntityDamageEvent.DamageCause.LAVA || event.getCause() == EntityDamageEvent.DamageCause.HOT_FLOOR)) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true) public void attack(EntityDamageByEntityEvent event) {
        Player player = event.getDamager() instanceof Player direct ? direct : event.getDamager() instanceof org.bukkit.entity.Projectile projectile && projectile.getShooter() instanceof Player shooter ? shooter : null;
        if (player == null || !manager.selected(player.getUniqueId())) return;
        String race = race(player);
        boolean bow = event.getDamager() instanceof org.bukkit.entity.Projectile;
        if (race.equals("ELF") && bow) event.setDamage(event.getDamage() * 1.16D);
        if (race.equals("BEASTMAN") && !bow) event.setDamage(event.getDamage() * 1.12D);
        if (race.equals("BEASTMAN") && bow) event.setDamage(event.getDamage() * 0.92D);
        if (race.equals("DEMON") && isNight(player)) event.setDamage(event.getDamage() * 1.16D);
        if (race.equals("ANGEL") && !bow && isNight(player)) event.setDamage(event.getDamage() * 0.92D);
    }
    @EventHandler(ignoreCancelled = true) public void angelMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!manager.selected(player.getUniqueId()) || !race(player).equals("ANGEL") || !isDay(player)) return;
        long now = System.currentTimeMillis();
        if (now - angelRegenCooldown.getOrDefault(player.getUniqueId(), 0L) < 5_000L) return;
        angelRegenCooldown.put(player.getUniqueId(), now);
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 0, true, false, true));
    }
}
