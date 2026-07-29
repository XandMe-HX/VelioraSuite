package id.velioragardens.veliorasuite.module.trader;

import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public final class TraderCombatListener implements Listener {

    private final TraderItemFactory itemFactory;

    public TraderCombatListener(TraderItemFactory itemFactory) {
        this.itemFactory = itemFactory;
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
