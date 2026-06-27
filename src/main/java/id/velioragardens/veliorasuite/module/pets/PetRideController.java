package id.velioragardens.veliorasuite.module.pets;

import id.velioragardens.veliorasuite.module.pets.model.OwnedPet;
import id.velioragardens.veliorasuite.module.pets.model.PetDefinition;
import id.velioragardens.veliorasuite.module.pets.model.VelioraPet;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;

public final class PetRideController implements Listener {
    private final PetManager manager;
    private final PetConfigManager config;

    public PetRideController(PetManager manager) {
        this.manager = manager;
        this.config = manager.config();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String[] args = event.getMessage().substring(1).split(" ");
        if (args.length < 2) return;
        String root = args[0].toLowerCase();
        if (!root.equals("pet") && !root.equals("pets") && !root.equals("vpet") && !root.equals("vpets")) return;
        if (!args[1].equalsIgnoreCase("ride")) return;
        event.setCancelled(true);
        startRide(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractAtEntityEvent event) {
        Entity clicked = event.getRightClicked();
        if (!clicked.getScoreboardTags().contains("veliorapets_pet")) return;
        VelioraPet active = manager.activePet(event.getPlayer().getUniqueId());
        if (active == null || !active.entity().getUniqueId().equals(clicked.getUniqueId())) return;
        event.setCancelled(true);
        startRide(event.getPlayer());
    }

    public void startRide(Player player) {
        if (!config.ridingEnabled()) return;
        VelioraPet active = manager.activePet(player.getUniqueId());
        if (active == null || active.entity().isDead()) {
            player.sendMessage(config.color(config.message("pet-ride-not-active", "%prefix% &cTidak ada pet aktif untuk ditunggangi.")));
            return;
        }
        PetDefinition definition = config.pets().get(active.petId());
        OwnedPet owned = manager.playerData(player.getUniqueId()).get(active.petId());
        if (definition == null || owned == null) return;
        if (!definition.rideable() || !config.rideableRarity(definition.rarity())) {
            player.sendMessage(config.color(config.message("pet-ride-not-rideable", "%prefix% &cPet ini tidak bisa ditunggangi.")));
            return;
        }
        if (config.ridingRequireAdult() && owned.level() < definition.adultLevel()) {
            player.sendMessage(config.color(config.message("pet-ride-not-adult", "%prefix% &cPet ini belum dewasa. Minimal level: &f%level%").replace("%level%", String.valueOf(definition.adultLevel()))));
            return;
        }
        LivingEntity entity = active.entity();
        if (!entity.getPassengers().contains(player)) entity.addPassenger(player);
        player.sendMessage(config.color(config.message("pet-ride-start", "%prefix% &aKamu menaiki &f%pet%&a.").replace("%pet%", owned.name())));
    }
}
