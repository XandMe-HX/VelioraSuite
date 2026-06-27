package id.velioragardens.veliorasuite.module.pets;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
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
            }
        }
    }
}
