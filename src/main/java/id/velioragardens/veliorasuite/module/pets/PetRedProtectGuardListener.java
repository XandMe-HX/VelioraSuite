package id.velioragardens.veliorasuite.module.pets;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.module.pets.compat.RedProtectCompat;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

public final class PetRedProtectGuardListener implements Listener {
    private static final String PET_TAG = "veliorapets_pet";
    private final PetManager manager;
    private final PetConfigManager config;
    private final RedProtectCompat redProtect;
    private final NamespacedKey ownerKey;
    private final NamespacedKey petIdKey;

    public PetRedProtectGuardListener(VelioraSuite plugin, PetManager manager) {
        this.manager = manager;
        this.config = manager.config();
        this.redProtect = manager.redProtectCompat();
        this.ownerKey = new NamespacedKey(plugin, "veliorapets_owner_uuid");
        this.petIdKey = new NamespacedKey(plugin, "veliorapets_pet_id");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        handleSpawn(event, event.getEntity());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onEntitySpawn(EntitySpawnEvent event) {
        handleSpawn(event, event.getEntity());
    }

    private void handleSpawn(Cancellable event, Entity entity) {
        if (!isPet(entity)) return;
        Player owner = owner(entity);
        String petId = petId(entity);
        if (owner == null) return;
        if (redProtect.canSpawnPet(owner, entity.getLocation())) {
            if (event.isCancelled()) {
                event.setCancelled(false);
                Bukkit.getLogger().info("VelioraPets DEBUG: RedProtect spawn uncancelled for pet " + entity.getType() + " " + entity.getUniqueId());
            }
            return;
        }
        event.setCancelled(true);
        owner.sendMessage(config.color(config.message("redprotect-region-denied", "%prefix% &cPet tidak bisa dipanggil di region RedProtect orang lain.")));
        manager.rememberSpawnFailure(owner.getUniqueId(), "RedProtect denied spawn for " + petId + " in region " + redProtect.regionName(entity.getLocation()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onTeleport(EntityTeleportEvent event) {
        Entity entity = event.getEntity();
        if (!isPet(entity)) return;
        Player owner = owner(entity);
        if (owner == null) return;
        if (redProtect.canMovePet(owner, event.getFrom(), event.getTo())) {
            if (event.isCancelled()) event.setCancelled(false);
        } else {
            event.setCancelled(true);
        }
    }

    private boolean isPet(Entity entity) {
        return entity != null && (entity.getScoreboardTags().contains(PET_TAG) || petId(entity) != null);
    }

    private String petId(Entity entity) {
        return entity == null ? null : entity.getPersistentDataContainer().get(petIdKey, PersistentDataType.STRING);
    }

    private Player owner(Entity entity) {
        if (entity == null) return null;
        String raw = entity.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        if (raw == null) return null;
        try { return Bukkit.getPlayer(UUID.fromString(raw)); } catch (IllegalArgumentException ignored) { return null; }
    }
}
