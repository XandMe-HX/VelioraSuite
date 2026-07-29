package id.velioragardens.veliorasuite.module.trader;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
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
        removeNear(origin);
        Location npcLocation = safeLocation(origin, configManager.getNpcOffsetX(), configManager.getNpcOffsetY(), configManager.getNpcOffsetZ());
        if (npcLocation == null) return false;
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
            Location companionLocation = safeLocation(origin, configManager.getCompanionOffsetX(), configManager.getCompanionOffsetY(), configManager.getCompanionOffsetZ());
            if (companionLocation != null) {
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
        }

        if (configManager.isDebugSpawn()) {
            org.bukkit.Bukkit.getLogger().info("VelioraTrader debug: NPC " + configManager.getNpcType() + " at " + format(traderEntity.getLocation()) + " uuid=" + traderEntity.getUniqueId());
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

    public void removeNear(Location origin) {
        remove();
        if (origin == null || origin.getWorld() == null) return;
        for (Entity entity : origin.getWorld().getNearbyEntities(origin, 24.0D, 12.0D, 24.0D)) {
            if (entity.getScoreboardTags().contains("velioratrader_npc") || entity.getScoreboardTags().contains("velioratrader_companion")) {
                entity.remove();
            }
        }
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

    private Location safeLocation(Location origin, double offsetX, double offsetY, double offsetZ) {
        Location preferred = origin.clone().add(offsetX, offsetY, offsetZ);
        if (isEntitySpaceSafe(preferred)) return preferred;
        int[][] offsets = {{1,0},{-1,0},{0,1},{0,-1},{2,0},{-2,0},{0,2},{0,-2},{2,1},{-2,1},{1,2},{-1,2}};
        for (int[] offset : offsets) {
            Location candidate = preferred.clone().add(offset[0], 0.0D, offset[1]);
            Location fixed = fixY(candidate);
            if (fixed != null && isEntitySpaceSafe(fixed)) return fixed;
        }
        return null;
    }

    private Location fixY(Location location) {
        if (location == null || location.getWorld() == null) return null;
        Block highest = location.getWorld().getHighestBlockAt(location.getBlockX(), location.getBlockZ());
        return highest.getLocation().add(0.5D, 1.0D, 0.5D);
    }

    private boolean isEntitySpaceSafe(Location location) {
        if (location == null || location.getWorld() == null) return false;
        Block feet = location.getBlock();
        Block head = feet.getRelative(0, 1, 0);
        Block ground = feet.getRelative(0, -1, 0);
        Material groundType = ground.getType();
        if (!groundType.isSolid() || ground.isLiquid()) return false;
        return feet.isPassable() && head.isPassable();
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
