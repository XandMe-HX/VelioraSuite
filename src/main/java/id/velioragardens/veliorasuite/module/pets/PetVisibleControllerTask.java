package id.velioragardens.veliorasuite.module.pets;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.module.pets.model.PetDefinition;
import id.velioragardens.veliorasuite.module.pets.model.VelioraPet;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.lang.reflect.Method;
import java.util.UUID;

public final class PetVisibleControllerTask implements Runnable {
    private static final String PET_TAG = "veliorapets_pet";
    private static final String ANCHOR_TAG = "veliorapets_aquatic_anchor";

    private final PetManager manager;
    private final PetConfigManager config;
    private final NamespacedKey ownerKey;
    private final NamespacedKey petIdKey;

    public PetVisibleControllerTask(VelioraSuite plugin, PetManager manager) {
        this.manager = manager;
        this.config = manager.config();
        this.ownerKey = new NamespacedKey(plugin, "veliorapets_owner_uuid");
        this.petIdKey = new NamespacedKey(plugin, "veliorapets_pet_id");
    }

    @Override
    public void run() {
        cleanupAnchorsOnly();
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (!entity.getScoreboardTags().contains(PET_TAG)) continue;
                if (!(entity instanceof LivingEntity pet)) continue;
                String petId = pet.getPersistentDataContainer().get(petIdKey, PersistentDataType.STRING);
                String ownerRaw = pet.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
                PetDefinition definition = petId == null ? null : config.pets().get(petId);
                Player owner = ownerOf(ownerRaw);
                stabilizePet(pet, definition, owner);
                if (owner != null && isCombatBlockedForOwner(owner)) {
                    VelioraPet active = manager.activePet(owner.getUniqueId());
                    if (active != null && active.entity().getUniqueId().equals(pet.getUniqueId())) active.targetUuid(null);
                }
            }
        }
    }

    private void stabilizePet(LivingEntity pet, PetDefinition definition, Player owner) {
        setInvisible(pet, false);
        pet.setCustomNameVisible(true);
        pet.setRemoveWhenFarAway(false);
        pet.setPersistent(false);
        pet.setCanPickupItems(false);
        pet.setCollidable(false);
        pet.setFireTicks(0);
        pet.setFallDistance(0.0F);
        pet.setSilent(config.silentPets() || (definition != null && definition.aquaticPet()));
        if (pet.isInsideVehicle()) pet.leaveVehicle();
        boolean controlled = definition != null && (definition.aquaticPet() || definition.flyingPet() || isHostileOrSpecialPet(definition.entityType()));
        if (controlled) {
            pet.setAI(false);
            if (definition.aquaticPet()) {
                pet.setGravity(false);
                pet.setInvulnerable(true);
                pet.setRemainingAir(pet.getMaximumAir());
            }
        }
        if (pet instanceof Mob mob) mob.setTarget(null);
    }

    private Player ownerOf(String raw) {
        if (raw == null) return null;
        try { return Bukkit.getPlayer(UUID.fromString(raw)); } catch (IllegalArgumentException ignored) { return null; }
    }

    private boolean isCombatBlockedForOwner(Player owner) {
        return owner.getGameMode() == GameMode.CREATIVE || owner.getGameMode() == GameMode.SPECTATOR;
    }

    private boolean isHostileOrSpecialPet(EntityType type) {
        if (type == null) return false;
        return switch (type) {
            case WARDEN, CREEPER, RAVAGER, EVOKER, VINDICATOR, PILLAGER, WITCH,
                 WITHER_SKELETON, ZOMBIFIED_PIGLIN, PIGLIN, PIGLIN_BRUTE, HOGLIN, ZOGLIN,
                 ENDERMAN, BREEZE, GUARDIAN, ELDER_GUARDIAN, SHULKER, GIANT, ILLUSIONER,
                 SKELETON, STRAY, BOGGED, DROWNED, HUSK, ZOMBIE, ZOMBIE_VILLAGER,
                 SLIME, MAGMA_CUBE, SPIDER, CAVE_SPIDER, SILVERFISH, ENDERMITE -> true;
            default -> type.name().equals("CREAKING");
        };
    }

    private void cleanupAnchorsOnly() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity.getScoreboardTags().contains(ANCHOR_TAG)) entity.remove();
            }
        }
    }

    private void setInvisible(LivingEntity pet, boolean invisible) {
        try {
            Method method = pet.getClass().getMethod("setInvisible", boolean.class);
            method.invoke(pet, invisible);
        } catch (Exception ignored) {
        }
    }
}
