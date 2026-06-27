package id.velioragardens.veliorasuite.module.pets;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;

public final class PetQuietTask implements Runnable {
    private final PetConfigManager config;

    public PetQuietTask(PetConfigManager config) {
        this.config = config;
    }

    @Override
    public void run() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (!entity.getScoreboardTags().contains("veliorapets_pet")) continue;
                if (!(entity instanceof LivingEntity pet)) continue;
                pet.setSilent(config.silentPets());
                pet.setFireTicks(0);
                pet.setCanPickupItems(false);
                pet.setRemoveWhenFarAway(false);
                pet.setPersistent(false);
                if (isAquaticPet(pet.getType())) {
                    pet.setRemainingAir(pet.getMaximumAir());
                    pet.setGravity(false);
                }
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
