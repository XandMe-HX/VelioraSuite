package id.velioragardens.veliorasuite.module.pets;

import id.velioragardens.veliorasuite.module.pets.model.OwnedPet;
import id.velioragardens.veliorasuite.module.pets.model.PetDefinition;
import id.velioragardens.veliorasuite.module.pets.model.PlayerPetData;
import id.velioragardens.veliorasuite.module.pets.model.VelioraPet;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import id.velioragardens.veliorasuite.core.effects.VelioraEffects.Priority;

import java.util.Locale;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.List;

public final class PetRideController implements Listener {
    private final PetManager manager;
    private final PetConfigManager config;
    private final Map<UUID, Long> lastRideAttempt = new HashMap<>();

    public PetRideController(PetManager manager) {
        this.manager = manager;
        this.config = manager.config();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String[] args = event.getMessage().substring(1).split(" ");
        if (args.length < 2) return;
        String root = args[0].toLowerCase(Locale.ROOT);
        if (!root.equals("pet") && !root.equals("pets") && !root.equals("vpet") && !root.equals("vpets")) return;
        if (args[1].equalsIgnoreCase("ride") && args.length == 2) {
            event.setCancelled(true);
            startRide(event.getPlayer());
            return;
        }
        if (args[1].equalsIgnoreCase("ride") && args.length >= 3) {
            event.setCancelled(true);
            manageAccess(event.getPlayer(), args);
            return;
        }
        if (args[1].equalsIgnoreCase("info")) {
            event.setCancelled(true);
            sendInfo(event.getPlayer(), args.length >= 3 ? args[2].toLowerCase(Locale.ROOT) : "active");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractAtEntityEvent event) {
        Entity clicked = event.getRightClicked();
        if (!clicked.getScoreboardTags().contains("veliorapets_pet")) return;
        VelioraPet active = manager.activePetByEntity(clicked.getUniqueId());
        if (active == null) return;
        event.setCancelled(true);
        if (clicked.getPassengers().contains(event.getPlayer())) return;
        startRide(event.getPlayer(), active);
    }

    public void startRide(Player player) {
        startRide(player, manager.activePet(player.getUniqueId()));
    }

    private void startRide(Player player, VelioraPet active) {
        if (!config.ridingEnabled()) return;
        long now = System.currentTimeMillis();
        long last = lastRideAttempt.getOrDefault(player.getUniqueId(), 0L);
        if (now - last < 1200L) return;
        lastRideAttempt.put(player.getUniqueId(), now);
        if (active == null || active.entity().isDead()) {
            player.sendMessage(config.color(config.message("pet-ride-not-active", "%prefix% &cTidak ada pet aktif untuk ditunggangi.")));
            return;
        }
        PetDefinition definition = config.pets().get(active.petId());
        OwnedPet owned = manager.playerData(active.ownerUuid()).get(active.petId());
        if (definition == null || owned == null) return;
        boolean owner = player.getUniqueId().equals(active.ownerUuid());
        if (!owner && !owned.publicRide() && !owned.trustedRiders().contains(player.getUniqueId())) {
            player.sendMessage(config.color("%prefix% &cPet ini hanya dapat ditunggangi pemilik atau rider tepercaya.".replace("%prefix%", config.prefix())));
            return;
        }
        if (!definition.rideable() || !config.rideableRarity(definition.rarity())) {
            player.sendMessage(config.color(config.message("pet-ride-not-rideable", "%prefix% &cPet ini tidak bisa ditunggangi.")));
            return;
        }
        if (definition.babyPet() && !config.allowRideBabyPets()) {
            player.sendMessage(config.color(config.message("pet-ride-baby", "%prefix% &cPet bayi tidak bisa ditunggangi. Tunggu atau pilih pet dewasa.")));
            return;
        }
        if (config.ridingRequireAdult() && owned.level() < definition.adultLevel()) {
            player.sendMessage(config.color(config.message("pet-ride-not-adult", "%prefix% &cPet ini belum dewasa. Minimal level: &f%level%").replace("%level%", String.valueOf(definition.adultLevel()))));
            return;
        }
        if (definition.flyingPet() && owned.level() < config.flyingMinimumLevel()) {
            player.sendMessage(config.color("%prefix% &cPet terbang membutuhkan level &f" + config.flyingMinimumLevel() + "&c.".replace("%prefix%", config.prefix())));
            return;
        }
        LivingEntity entity = active.entity();
        if (!entity.getPassengers().contains(player)) entity.addPassenger(player);
        manager.plugin().getEffects().particle(entity.getLocation().add(0, 0.7D, 0), org.bukkit.Particle.CLOUD, 12, 0.3D, 0.3D, 0.3D, 0.02D, Priority.IMPORTANT);
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_HORSE_SADDLE, 0.7F, definition.flyingPet() ? 1.35F : 1.0F);
        player.sendMessage(config.color(config.message("pet-ride-start", "%prefix% &aKamu menaiki &f%pet%&a.").replace("%pet%", owned.name())));
        player.sendActionBar(config.color("&eGunakan arah gerak untuk mengendalikan pet. &7Tekan Shift untuk turun."));
    }

    private void manageAccess(Player owner, String[] args) {
        String action = args[2].toLowerCase(Locale.ROOT);
        String petId = args.length >= 4 ? args[3].toLowerCase(Locale.ROOT) : "active";
        VelioraPet active = manager.activePet(owner.getUniqueId());
        if (petId.equals("active") && active != null) petId = active.petId();
        OwnedPet pet = manager.playerData(owner.getUniqueId()).get(petId);
        if (pet == null) {
            owner.sendMessage(config.color(config.prefix() + "&cPet tidak ditemukan. Gunakan ID pet atau panggil pet terlebih dahulu."));
            return;
        }
        if (action.equals("public")) {
            pet.publicRide(true);
            manager.data().save(owner.getUniqueId());
            owner.sendMessage(config.color(config.prefix() + "&aTunggangan &f" + pet.name() + " &aterbuka untuk semua pemain."));
            return;
        }
        if (action.equals("private")) {
            pet.publicRide(false);
            manager.data().save(owner.getUniqueId());
            owner.sendMessage(config.color(config.prefix() + "&eTunggangan &f" + pet.name() + " &ehanya untuk pemilik dan rider tepercaya."));
            return;
        }
        if ((action.equals("trust") || action.equals("untrust")) && args.length >= 5) {
            Player target = owner.getServer().getPlayerExact(args[4]);
            if (target == null) { owner.sendMessage(config.color(config.prefix() + "&cPemain harus sedang online.")); return; }
            if (target.getUniqueId().equals(owner.getUniqueId())) { owner.sendMessage(config.color(config.prefix() + "&eKamu sudah selalu bisa menunggangi pet sendiri.")); return; }
            boolean changed = action.equals("trust") ? pet.trustRider(target.getUniqueId()) : pet.untrustRider(target.getUniqueId());
            if (!changed) {
                owner.sendMessage(config.color(config.prefix() + (action.equals("trust") ? "&eRider sudah terdaftar atau batasnya 20 orang." : "&ePemain itu bukan rider tepercaya.")));
                return;
            }
            manager.data().save(owner.getUniqueId());
            owner.sendMessage(config.color(config.prefix() + (action.equals("trust") ? "&a" : "&e") + target.getName() + " &7" + (action.equals("trust") ? "ditambahkan sebagai rider tepercaya." : "dihapus dari rider tepercaya.")));
            return;
        }
        if (action.equals("list")) {
            List<String> riders = pet.trustedRiders().stream().map(uuid -> {
                Player online = owner.getServer().getPlayer(uuid);
                return online == null ? uuid.toString().substring(0, 8) : online.getName();
            }).toList();
            owner.sendMessage(config.color(config.prefix() + "&7Akses tunggangan &f" + pet.name() + "&7: " + (pet.publicRide() ? "&aPublik" : "&ePrivat") + " &8| &f" + (riders.isEmpty() ? "Tidak ada rider tepercaya" : String.join(", ", riders))));
            return;
        }
        owner.sendMessage(config.color(config.prefix() + "&e/pet ride &8- &7naik pet aktif"));
        owner.sendMessage(config.color(config.prefix() + "&e/pet ride <public|private|list> [pet]"));
        owner.sendMessage(config.color(config.prefix() + "&e/pet ride <trust|untrust> <pet|active> <pemain-online>"));
    }

    private void sendInfo(Player player, String target) {
        PlayerPetData data = manager.playerData(player.getUniqueId());
        OwnedPet owned;
        if (target == null || target.equalsIgnoreCase("active")) {
            VelioraPet active = manager.activePet(player.getUniqueId());
            owned = active == null ? null : data.get(active.petId());
        } else {
            owned = data.get(target.toLowerCase(Locale.ROOT));
        }
        if (owned == null) {
            player.sendMessage(config.color(config.message("pet-not-owned", "%prefix% &cKamu belum punya pet &f%pet%&c.").replace("%pet%", target)));
            return;
        }
        PetDefinition definition = config.pets().get(owned.id());
        if (definition == null) return;
        boolean active = manager.activePet(player.getUniqueId()) != null && manager.activePet(player.getUniqueId()).petId().equalsIgnoreCase(owned.id());
        boolean adult = owned.level() >= definition.adultLevel();
        player.sendMessage(config.color(config.message("pet-info-header", "%prefix% &dInfo Pet: &f%pet%").replace("%pet%", owned.name())));
        player.sendMessage(config.color("&7ID: &f" + owned.id()));
        player.sendMessage(config.color("&7Rarity: &f" + definition.rarity().name()));
        player.sendMessage(config.color("&7Level/EXP: &f" + owned.level() + " / " + owned.exp()));
        player.sendMessage(config.color("&7Food: &f" + definition.foodMaterial().name() + " (+" + definition.feedExp() + " EXP)"));
        player.sendMessage(config.color("&7Rideable: &f" + (definition.rideable() ? "Yes" : "No")));
        if (definition.rideable()) player.sendMessage(config.color("&7Akses ride: " + (owned.publicRide() ? "&aPublik" : "&ePrivat") + " &8| &f" + owned.trustedRiders().size() + " &7rider tepercaya"));
        player.sendMessage(config.color("&7Adult Level: &f" + definition.adultLevel()));
        player.sendMessage(config.color("&7Status Dewasa: &f" + (adult ? "Dewasa" : "Belum dewasa")));
        player.sendMessage(config.color("&7Active: &f" + (active ? "yes" : "no")));
    }
}
