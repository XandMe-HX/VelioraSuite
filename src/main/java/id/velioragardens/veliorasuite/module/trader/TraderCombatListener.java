package id.velioragardens.veliorasuite.module.trader;

import org.bukkit.entity.Player;
import org.bukkit.entity.LivingEntity;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class TraderCombatListener implements Listener {

    private static final long EXCALIBUR_LIGHTNING_COOLDOWN_MILLIS = 60L * 60L * 1000L;
    private final TraderItemFactory itemFactory;
    private final Map<UUID, Long> excaliburLightningCooldowns = new HashMap<>();

    public TraderCombatListener(TraderItemFactory itemFactory) {
        this.itemFactory = itemFactory;
    }

    /**
     * Excalibur active: right-click a non-player living target to call lightning.
     * It is deliberately private-per-player and only usable once per hour.
     */
    @EventHandler(ignoreCancelled = true)
    public void onExcaliburLightning(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        if (!(event.getRightClicked() instanceof LivingEntity target) || target instanceof Player) return;
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || !item.hasItemMeta()) return;
        String id = item.getItemMeta().getPersistentDataContainer().get(itemFactory.getItemIdKey(), PersistentDataType.STRING);
        if (!"excalibur".equalsIgnoreCase(id)) return;

        long now = System.currentTimeMillis();
        long readyAt = excaliburLightningCooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (readyAt > now) {
            long left = Math.max(1L, (readyAt - now) / 1000L);
            player.sendMessage("§8[§6Excalibur§8] §ePetir masih cooldown: §f" + (left / 60L) + "m " + (left % 60L) + "s");
            return;
        }
        excaliburLightningCooldowns.put(player.getUniqueId(), now + EXCALIBUR_LIGHTNING_COOLDOWN_MILLIS);
        target.getWorld().strikeLightningEffect(target.getLocation());
        target.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, target.getLocation().add(0.0D, 1.0D, 0.0D), 42, 0.8D, 1.0D, 0.8D, 0.06D);
        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.8F, 1.1F);
        target.damage(16.0D, player);
        player.sendMessage("§8[§6Excalibur§8] §bWrath of the Sky aktif! §7Cooldown: §f1 jam");
    }

    @EventHandler
    public void onShoot(EntityShootBowEvent event) {
        if (!(event.getProjectile() instanceof Projectile projectile)) return;
        ItemStack bow = event.getBow();
        int damage = itemFactory.customDamage(bow);
        if (damage <= 0) return;
        projectile.getPersistentDataContainer().set(itemFactory.getCustomDamageKey(), PersistentDataType.INTEGER, damage);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            int damage = itemFactory.customDamage(player.getInventory().getItemInMainHand());
            if (damage > 0) event.setDamage(damage);
            return;
        }
        if (event.getDamager() instanceof Projectile projectile) {
            Integer damage = projectile.getPersistentDataContainer().get(itemFactory.getCustomDamageKey(), PersistentDataType.INTEGER);
            if (damage != null && damage > 0) event.setDamage(damage);
        }
    }
}
