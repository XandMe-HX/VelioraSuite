package id.velioragardens.veliorasuite.module.trader;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Villager;
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

    public boolean spawn(Location origin) {
        if (origin == null || origin.getWorld() == null) return false;
        remove();
        Location npcLocation = origin.clone().add(configManager.getNpcOffsetX(), configManager.getNpcOffsetY(), configManager.getNpcOffsetZ());
        npcLocation.getChunk().load(true);
        traderEntity = origin.getWorld().spawnEntity(npcLocation, configManager.getNpcType());
        traderEntity.teleport(npcLocation);
        traderEntity.addScoreboardTag("velioratrader_npc");
        traderEntity.setCustomName(configManager.color(configManager.getNpcName()));
        traderEntity.setCustomNameVisible(true);
        traderEntity.setGlowing(configManager.isNpcGlowing());
        traderEntity.setPersistent(true);
        if (traderEntity instanceof LivingEntity living) freeze(living, configManager.isNpcGravity(), true);
        if (traderEntity instanceof Villager villager) configureVillager(villager);
        traderEntities.add(traderEntity.getUniqueId());

        if (configManager.isCompanionEnabled()) {
            Location companionLocation = origin.clone().add(configManager.getCompanionOffsetX(), configManager.getCompanionOffsetY(), configManager.getCompanionOffsetZ());
            companionLocation.getChunk().load(true);
            companionEntity = origin.getWorld().spawnEntity(companionLocation, configManager.getCompanionType());
            companionEntity.teleport(companionLocation);
            companionEntity.addScoreboardTag("velioratrader_companion");
            companionEntity.setCustomName(configManager.color(configManager.getCompanionName()));
            companionEntity.setCustomNameVisible(configManager.isCompanionNameVisible());
            companionEntity.setPersistent(true);
            if (companionEntity instanceof LivingEntity living) freeze(living, true, false);
            traderEntities.add(companionEntity.getUniqueId());
        }

        if (configManager.isDebugSpawn()) {
            org.bukkit.Bukkit.getLogger().info("VelioraTrader debug: NPC " + configManager.getNpcType() + " at " + format(npcLocation) + " uuid=" + traderEntity.getUniqueId());
            if (companionEntity != null) org.bukkit.Bukkit.getLogger().info("VelioraTrader debug: companion " + configManager.getCompanionType() + " at " + format(companionEntity.getLocation()) + " uuid=" + companionEntity.getUniqueId());
        }
        return true;
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

    private void freeze(LivingEntity entity, boolean gravity, boolean useNpcSilent) {
        entity.setAI(false);
        entity.setInvulnerable(true);
        entity.setSilent(useNpcSilent ? configManager.isNpcSilent() : true);
        entity.setGravity(gravity);
        entity.setCollidable(false);
        entity.setRemoveWhenFarAway(false);
        entity.setPersistent(true);
    }

    private void configureVillager(Villager villager) {
        villager.setAdult();
        try { villager.setProfession(Villager.Profession.CARTOGRAPHER); } catch (Exception ignored) { }
        try { villager.setVillagerType(Villager.Type.PLAINS); } catch (Exception ignored) { }
    }

    private String format(Location location) {
        return (location.getWorld() == null ? "world" : location.getWorld().getName()) + " " + location.getBlockX() + " " + location.getBlockY() + " " + location.getBlockZ();
    }
}
