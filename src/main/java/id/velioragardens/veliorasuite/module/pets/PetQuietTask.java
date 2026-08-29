package id.velioragardens.veliorasuite.module.pets;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;

public final class PetQuietTask implements Runnable {
    private final PetManager manager;

    public PetQuietTask(PetManager manager) {
        this.manager = manager;
    }

    @Override
    public void run() {
        // Only touch registered active pets. Scanning every entity in every
        // world is needlessly expensive on a survival server.
        for (LivingEntity pet : manager.activePetEntities()) {
            if (pet.isDead() || !pet.isValid()) continue;
            PetConfigManager config = manager.config();
            pet.setSilent(config.silentPets());
            pet.setFireTicks(0);
            pet.setCanPickupItems(false);
            pet.setRemoveWhenFarAway(false);
            pet.setPersistent(true);
            if (isAquaticPet(pet.getType())) {
                pet.setRemainingAir(pet.getMaximumAir());
                pet.setGravity(false);
            }
        }
    }

    private boolean isAquaticPet(EntityType type) {
        return switch (type) {
            case AXOLOTL, COD, DOLPHIN, ELDER_GUARDIAN, GLOW_SQUID, GUARDIAN, PUFFERFISH, SALMON, SQUID, TADPOLE, TROPICAL_FISH -> true;
            default -> false;
        };
    }
}
