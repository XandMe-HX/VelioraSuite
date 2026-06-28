package id.velioragardens.veliorasuite.module.pets;

import id.velioragardens.veliorasuite.module.pets.model.VelioraPet;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.lang.reflect.Method;
import java.util.Locale;

public final class PetSafeModeGuardListener implements Listener {
    private static final String PET_TAG = "veliorapets_pet";
    private final PetManager manager;
    private final PetConfigManager config;

    public PetSafeModeGuardListener(PetManager manager) {
        this.manager = manager;
        this.config = manager.config();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPetCommand(PlayerCommandPreprocessEvent event) {
        String[] args = event.getMessage().substring(1).split(" ");
        if (args.length == 0) return;
        String root = args[0].toLowerCase(Locale.ROOT);
        if (!root.equals("pet") && !root.equals("pets") && !root.equals("vpet") && !root.equals("vpets")) return;
        if (args.length >= 2 && args[1].equalsIgnoreCase("debug")) {
            event.setCancelled(true);
            if (!event.getPlayer().hasPermission("veliorasuite.pets.admin") && !event.getPlayer().isOp()) {
                event.getPlayer().sendMessage(config.color(config.message("no-permission", "%prefix% &cKamu tidak punya izin.")));
                return;
            }
            sendDebug(event.getPlayer());
            return;
        }
        if (args.length >= 3 && args[1].equalsIgnoreCase("summon") && config.isSafeModeDisabledPet(args[2])) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(config.color(config.message("pet-disabled-safe-mode", "%prefix% &cPet &f%pet% &csedang dinonaktifkan sementara di stable safe mode.").replace("%pet%", args[2].toLowerCase(Locale.ROOT))));
        }
    }

    private void sendDebug(Player player) {
        VelioraPet active = manager.activePet(player.getUniqueId());
        LivingEntity entity = active == null ? null : active.entity();
        Location playerLoc = player.getLocation();
        player.sendMessage(config.color("&8[&dVelioraPets Debug&8]"));
        player.sendMessage(config.color("&7module enabled: &ftrue"));
        player.sendMessage(config.color("&7stable-safe-mode: &f" + config.stableSafeMode()));
        player.sendMessage(config.color("&7walking-pets-only: &f" + config.walkingPetsOnly()));
        player.sendMessage(config.color("&7active pet id: &f" + (active == null ? "none" : active.petId())));
        player.sendMessage(config.color("&7owner gamemode: &f" + player.getGameMode().name()));
        player.sendMessage(config.color("&7redprotect installed: &f" + manager.redProtectCompat().isInstalled()));
        player.sendMessage(config.color("&7redprotect region: &f" + manager.redProtectCompat().regionName(playerLoc)));
        player.sendMessage(config.color("&7redprotect can spawn here: &f" + manager.redProtectCompat().canSpawnPet(player, playerLoc)));
        player.sendMessage(config.color("&7redprotect can move here: &f" + manager.redProtectCompat().canMovePet(player, playerLoc, playerLoc)));
        player.sendMessage(config.color("&7last spawn failure: &f" + manager.lastSpawnFailure(player.getUniqueId())));
        player.sendMessage(config.color("&7last spawn attempts: &f" + manager.lastSpawnAttempts(player.getUniqueId())));
        player.sendMessage(config.color("&7last movement reason: &f" + PetMovementDebug.reason(player.getUniqueId())));
        player.sendMessage(config.color("&7last target location: &f" + PetMovementDebug.target(player.getUniqueId())));
        player.sendMessage(config.color("&7redprotect target allowed: &f" + PetMovementDebug.redProtectAllowed(player.getUniqueId())));
        player.sendMessage(config.color("&7pathfinder used: &f" + PetMovementDebug.pathfinderUsed(player.getUniqueId())));
        player.sendMessage(config.color("&7last teleport reason: &f" + PetMovementDebug.teleportReason(player.getUniqueId())));
        if (entity == null) {
            player.sendMessage(config.color("&7entity: &cnone"));
            player.sendMessage(config.color("&7nearby veliorapets_pet count: &f" + nearbyPetCount(player)));
            return;
        }
        Location loc = entity.getLocation();
        player.sendMessage(config.color("&7entity type: &f" + entity.getType().name()));
        player.sendMessage(config.color("&7entity uuid: &f" + entity.getUniqueId()));
        player.sendMessage(config.color("&7world: &f" + loc.getWorld().getName()));
        player.sendMessage(config.color("&7location: &f" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ()));
        player.sendMessage(config.color("&7dead: &f" + entity.isDead()));
        player.sendMessage(config.color("&7valid: &f" + entity.isValid()));
        player.sendMessage(config.color("&7invisible: &f" + isInvisible(entity)));
        player.sendMessage(config.color("&7ai: &f" + hasAi(entity)));
        player.sendMessage(config.color("&7gravity: &f" + entity.hasGravity()));
        player.sendMessage(config.color("&7silent: &f" + entity.isSilent()));
        player.sendMessage(config.color("&7inside vehicle: &f" + entity.isInsideVehicle()));
        player.sendMessage(config.color("&7tags: &f" + entity.getScoreboardTags()));
        player.sendMessage(config.color("&7nearby veliorapets_pet count: &f" + nearbyPetCount(player)));
    }

    private int nearbyPetCount(Player player) {
        int count = 0;
        for (Entity entity : player.getNearbyEntities(20, 20, 20)) if (entity.getScoreboardTags().contains(PET_TAG)) count++;
        return count;
    }

    private boolean isInvisible(LivingEntity entity) {
        try { Method method = entity.getClass().getMethod("isInvisible"); Object value = method.invoke(entity); return value instanceof Boolean b && b; } catch (Exception ignored) { return false; }
    }

    private boolean hasAi(LivingEntity entity) {
        try { Method method = entity.getClass().getMethod("hasAI"); Object value = method.invoke(entity); return value instanceof Boolean b && b; } catch (Exception ignored) { return false; }
    }
}
