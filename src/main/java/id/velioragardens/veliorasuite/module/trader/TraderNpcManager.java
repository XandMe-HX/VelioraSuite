package id.velioragardens.veliorasuite.module.trader;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class TraderNpcManager implements Listener {

    private final TraderConfigManager configManager;
    private final TraderManager traderManager;
    private final Set<UUID> traderEntities = new HashSet<>();
    private Entity traderEntity;
    private Entity companionEntity;

    public TraderNpcManager(TraderConfigManager configManager, TraderManager traderManager) {
        this.configManager = configManager;
        this.traderManager = traderManager;
    }

    public void spawn(Location location) {
        if (location == null || location.getWorld() == null) return;
        remove();
        Location npcLocation = location.clone().add(0.5D, 1.0D, 0.5D);
        traderEntity = location.getWorld().spawnEntity(npcLocation, configManager.getNpcType());
        if (traderEntity instanceof LivingEntity living) freeze(living, configManager.isNpcGravity());
        traderEntity.setCustomName(configManager.color(configManager.getNpcName()));
        traderEntity.setCustomNameVisible(true);
        traderEntities.add(traderEntity.getUniqueId());

        if (configManager.isCompanionEnabled()) {
            Location companionLocation = npcLocation.clone().add(1.5D, 0.0D, 0.0D);
            companionEntity = location.getWorld().spawnEntity(companionLocation, configManager.getCompanionType());
            if (companionEntity instanceof LivingEntity living) freeze(living, true);
            companionEntity.setCustomName(configManager.color("&eTrader Companion"));
            companionEntity.setCustomNameVisible(false);
            traderEntities.add(companionEntity.getUniqueId());
        }
    }

    public void remove() {
        if (traderEntity != null && !traderEntity.isDead()) traderEntity.remove();
        if (companionEntity != null && !companionEntity.isDead()) companionEntity.remove();
        traderEntities.clear();
        traderEntity = null;
        companionEntity = null;
    }

    public boolean isTraderEntity(Entity entity) {
        return entity != null && traderEntities.contains(entity.getUniqueId());
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        if (!isTraderEntity(event.getRightClicked())) return;
        event.setCancelled(true);
        if (event.getRightClicked().equals(traderEntity)) traderManager.openGui(event.getPlayer());
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (!isTraderEntity(event.getEntity())) return;
        event.setCancelled(true);
    }

    private void freeze(LivingEntity entity, boolean gravity) {
        entity.setAI(false);
        entity.setInvulnerable(true);
        entity.setSilent(configManager.isNpcSilent());
        entity.setGravity(gravity);
        entity.setCollidable(false);
        entity.setRemoveWhenFarAway(false);
    }
}
