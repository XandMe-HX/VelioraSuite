package id.velioragardens.veliorasuite.module.pets;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.module.pets.compat.RedProtectCompat;
import id.velioragardens.veliorasuite.module.pets.model.OwnedPet;
import id.velioragardens.veliorasuite.module.pets.model.PetDefinition;
import id.velioragardens.veliorasuite.module.pets.model.PetRarity;
import id.velioragardens.veliorasuite.module.pets.model.PlayerPetData;
import id.velioragardens.veliorasuite.module.pets.model.VelioraPet;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public final class PetManager implements Listener {
    private static final String PET_TAG = "veliorapets_pet";
    private static final String AQUATIC_ANCHOR_TAG = "veliorapets_aquatic_anchor";

    private final VelioraSuite plugin;
    private final PetConfigManager config;
    private final PetDataManager data;
    private final PetEconomyManager economy;
    private final PetScaleHelper scaleHelper;
    private final RedProtectCompat redProtect;
    private final Map<UUID, VelioraPet> activePets = new HashMap<>();
    private final Map<UUID, String> lastSpawnFailure = new HashMap<>();
    private final Map<UUID, Integer> lastSpawnAttempts = new HashMap<>();
    private final Random random = new Random();
    private final NamespacedKey ownerKey;
    private final NamespacedKey petIdKey;
    private final NamespacedKey rarityKey;
    private final NamespacedKey levelKey;
    private BukkitTask followTask;
    private BukkitTask cosmeticTask;
    private PetGuiManager guiManager;

    public PetManager(VelioraSuite plugin) {
        this.plugin = plugin;
        this.config = new PetConfigManager(plugin);
        this.data = new PetDataManager(plugin);
        this.economy = new PetEconomyManager(plugin, config);
        this.scaleHelper = new PetScaleHelper(plugin);
        this.redProtect = new RedProtectCompat(plugin, config);
        this.ownerKey = new NamespacedKey(plugin, "veliorapets_owner_uuid");
        this.petIdKey = new NamespacedKey(plugin, "veliorapets_pet_id");
        this.rarityKey = new NamespacedKey(plugin, "veliorapets_rarity");
        this.levelKey = new NamespacedKey(plugin, "veliorapets_level");
    }

    public void load() { config.load(); data.load(); }

    public void start(PetGuiManager guiManager) {
        this.guiManager = guiManager;
        stopTasks();
        if (!config.stableSafeMode()) {
            followTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickFollowCombatLegacy, 20L, 20L);
            cosmeticTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickCosmetic, 20L * config.auraIntervalSeconds(), 20L * config.auraIntervalSeconds());
        }
    }

    public void shutdown() {
        stopTasks();
        for (UUID uuid : new ArrayList<>(activePets.keySet())) dismiss(Bukkit.getPlayer(uuid), false);
        cleanupAllEntities();
        data.saveAll();
    }

    public void reload() {
        config.load();
        data.load();
        stopTasks();
        if (!config.stableSafeMode()) {
            followTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickFollowCombatLegacy, 20L, 20L);
            cosmeticTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickCosmetic, 20L * config.auraIntervalSeconds(), 20L * config.auraIntervalSeconds());
        }
    }

    public PetConfigManager config() { return config; }
    public PetDataManager data() { return data; }
    public RedProtectCompat redProtectCompat() { return redProtect; }
    public PlayerPetData playerData(UUID uuid) { return data.get(uuid); }
    public VelioraPet activePet(UUID uuid) { return activePets.get(uuid); }
    public String lastSpawnFailure(UUID uuid) { return lastSpawnFailure.getOrDefault(uuid, "none"); }
    public int lastSpawnAttempts(UUID uuid) { return lastSpawnAttempts.getOrDefault(uuid, 0); }
    public void rememberSpawnFailure(UUID uuid, String reason) { lastSpawnFailure.put(uuid, reason == null ? "unknown" : reason); }
    public void openMain(Player player) { guiManager.openMain(player); }
    public void openShop(Player player) { guiManager.openShop(player); }
    public void openGacha(Player player) { guiManager.openGacha(player); }
    public void openList(Player player) { guiManager.openList(player); }

    public void openStorage(Player player) {
        VelioraPet active = activePets.get(player.getUniqueId());
        PlayerPetData pdata = data.get(player.getUniqueId());
        String petId = active != null ? active.petId() : pdata.lastPet();
        if (petId == null || (!config.allowStorageWithoutActive() && active == null)) {
            player.sendMessage(config.color(config.message("no-active-pet", "%prefix% &cTidak ada pet aktif.")));
            return;
        }
        PetDefinition definition = config.pets().get(petId.toLowerCase(Locale.ROOT));
        OwnedPet owned = pdata.get(petId);
        if (definition == null || owned == null) return;
        guiManager.openStorage(player, definition, owned);
    }

    public boolean buy(Player player, String petId) {
        PetDefinition definition = config.pets().get(petId.toLowerCase(Locale.ROOT));
        if (definition == null) return false;
        PlayerPetData pdata = data.get(player.getUniqueId());
        if (pdata.owns(definition.id())) {
            player.sendMessage(config.color(config.message("already-owned", "%prefix% &eKamu sudah punya pet ini.")));
            return false;
        }
        if (!economy.isReady()) { player.sendMessage(config.color(config.message("vault-missing", "%prefix% &cEconomy tidak aktif."))); return false; }
        if (!economy.take(player, definition.price())) { player.sendMessage(config.color(config.message("not-enough-money", "%prefix% &cUang kamu tidak cukup."))); return false; }
        givePet(player, definition.id(), true);
        player.sendMessage(config.color(config.message("pet-bought", "%prefix% &aKamu membeli pet &f%pet% &adengan harga &e%money%&a.")
                .replace("%pet%", config.color(definition.displayName()))
                .replace("%money%", config.formatMoney(definition.price()))));
        sendFoodHint(player, definition);
        if (config.autoSummonNewPet()) summon(player, definition.id());
        return true;
    }

    public void startGacha(Player player) {
        if (!economy.isReady()) { player.sendMessage(config.color(config.message("vault-missing", "%prefix% &cEconomy tidak aktif."))); return; }
        if (!economy.take(player, config.gachaPrice())) { player.sendMessage(config.color(config.message("not-enough-money", "%prefix% &cUang kamu tidak cukup."))); return; }
        player.sendMessage(config.color(config.message("gacha-start", "%prefix% &eGacha pet dimulai...")));
        guiManager.animateGacha(player, () -> finishGacha(player));
    }

    private void finishGacha(Player player) {
        PetDefinition result = randomPet();
        if (result == null) return;
        PlayerPetData pdata = data.get(player.getUniqueId());
        if (pdata.owns(result.id())) {
            OwnedPet owned = pdata.get(result.id());
            if (owned != null) {
                boolean leveled = owned.addExp(config.duplicateExp(), config.maxLevel());
                if (leveled) updateActiveScale(player, owned.id());
            }
            data.save(player.getUniqueId());
            player.sendMessage(config.color(config.message("already-owned", "%prefix% &eKamu sudah punya pet ini. EXP pet ditambah.")));
        } else {
            givePet(player, result.id(), false);
            sendFoodHint(player, result);
            if (config.autoSummonNewPet()) summon(player, result.id());
        }
    }

    public boolean givePet(Player player, String petId, boolean silent) {
        PetDefinition definition = config.pets().get(petId.toLowerCase(Locale.ROOT));
        if (definition == null) return false;
        PlayerPetData pdata = data.get(player.getUniqueId());
        if (!pdata.owns(definition.id())) pdata.add(new OwnedPet(definition.id(), 1, 0, config.plain(definition.displayName()), 0L, System.currentTimeMillis()));
        data.save(player.getUniqueId());
        if (!silent) player.sendMessage(config.color(config.message("gacha-result", "%prefix% &aKamu mendapatkan pet &f%pet% &7(%rarity%&7)&a!")
                .replace("%pet%", config.color(definition.displayName()))
                .replace("%rarity%", definition.rarity().name())));
        return true;
    }

    public boolean removePet(Player player, String petId) {
        PlayerPetData pdata = data.get(player.getUniqueId());
        if (!pdata.owns(petId)) return false;
        if (petId.equalsIgnoreCase(pdata.activePet())) dismiss(player, false);
        pdata.remove(petId);
        data.save(player.getUniqueId());
        return true;
    }

    public boolean summon(Player player, String petId) {
        String id = petId == null ? "" : petId.toLowerCase(Locale.ROOT);
        if (config.isSafeModeDisabledPet(id)) {
            player.sendMessage(config.color(config.message("pet-disabled-safe-mode", "%prefix% &cPet &f%pet% &csedang dinonaktifkan sementara di stable safe mode.").replace("%pet%", id)));
            return false;
        }
        PetDefinition definition = config.pets().get(id);
        if (definition == null) {
            player.sendMessage(config.color(config.message("pet-not-found", "%prefix% &cPet &f%pet% &ctidak ditemukan.").replace("%pet%", id)));
            return false;
        }
        PlayerPetData pdata = data.get(player.getUniqueId());
        OwnedPet owned = pdata.get(definition.id());
        if (owned == null) {
            player.sendMessage(config.color(config.message("pet-not-owned", "%prefix% &cKamu belum punya pet &f%pet%&c.").replace("%pet%", definition.id())));
            return false;
        }
        if (owned.cooldownUntil() > System.currentTimeMillis()) {
            player.sendMessage(config.color(config.message("pet-cooldown", "%prefix% &cPet &f%pet% &cmasih cooldown: &f%time%")
                    .replace("%pet%", owned.name())
                    .replace("%time%", timeLeft(owned.cooldownUntil()))));
            return false;
        }
        Location spawnLocation = safeSpawnLocation(player);
        if (spawnLocation == null) {
            rememberSpawnFailure(player.getUniqueId(), "RedProtect/world denied all safe spawn locations near owner");
            player.sendMessage(config.color(config.message("redprotect-compat-blocked", "%prefix% &cPet diblokir oleh RedProtect. Cek flag region atau izin land.")));
            return false;
        }
        dismiss(player, false);
        LivingEntity entity;
        try {
            entity = spawnEntity(player, definition, owned, spawnLocation);
        } catch (Exception exception) {
            rememberSpawnFailure(player.getUniqueId(), exception.getMessage());
            player.sendMessage(config.color(config.prefix() + "&cPet gagal spawn: &f" + exception.getMessage()));
            plugin.getLogger().warning("VelioraPets DEBUG: spawn failed for " + definition.id() + " / " + definition.entityType() + " - " + exception.getMessage());
            return false;
        }
        activePets.put(player.getUniqueId(), new VelioraPet(player.getUniqueId(), definition.id(), entity));
        pdata.activePet(definition.id());
        pdata.lastPet(definition.id());
        data.save(player.getUniqueId());
        lastSpawnAttempts.put(player.getUniqueId(), 0);
        scheduleSpawnChecks(player, definition, owned, entity);
        player.sendMessage(config.color(config.message("pet-summoned", "%prefix% &aPet &f%pet% &adipanggil.").replace("%pet%", config.color(owned.name()))));
        sendFoodHint(player, definition);
        return true;
    }

    private void scheduleSpawnChecks(Player player, PetDefinition definition, OwnedPet owned, LivingEntity entity) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> verifySpawn(player, definition, owned, entity), 1L);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> verifySpawn(player, definition, owned, entity), 5L);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> verifySpawn(player, definition, owned, entity), 20L);
    }

    private void verifySpawn(Player player, PetDefinition definition, OwnedPet owned, LivingEntity entity) {
        UUID uuid = player.getUniqueId();
        VelioraPet active = activePets.get(uuid);
        if (active == null || active.entity().getUniqueId() != entity.getUniqueId()) return;
        if (entity != null && !entity.isDead() && entity.isValid()) return;
        int attempts = lastSpawnAttempts.getOrDefault(uuid, 0);
        activePets.remove(uuid);
        if (attempts < 2) {
            lastSpawnAttempts.put(uuid, attempts + 1);
            Location retryLocation = safeSpawnLocation(player);
            if (retryLocation != null) {
                try {
                    LivingEntity retry = spawnEntity(player, definition, owned, retryLocation);
                    activePets.put(uuid, new VelioraPet(uuid, definition.id(), retry));
                    data.get(uuid).activePet(definition.id());
                    data.save(uuid);
                    scheduleSpawnChecks(player, definition, owned, retry);
                    rememberSpawnFailure(uuid, "respawn attempt " + (attempts + 1) + " after vanished entity");
                    return;
                } catch (Exception exception) {
                    rememberSpawnFailure(uuid, "retry failed: " + exception.getMessage());
                }
            }
        }
        data.get(uuid).activePet(null);
        data.save(uuid);
        String region = redProtect.regionName(player.getLocation());
        rememberSpawnFailure(uuid, "pet vanished after spawn. pet=" + definition.id() + ", type=" + definition.entityType() + ", region=" + region + ", attempts=" + attempts);
        player.sendMessage(config.color(config.prefix() + "&cPet gagal muncul karena diblokir RedProtect/world/plugin lain."));
        plugin.getLogger().warning("VelioraPets DEBUG: pet vanished after spawn: " + definition.id() + " " + definition.entityType() + " owner=" + player.getName() + " region=" + region + " attempts=" + attempts);
    }

    private Location safeSpawnLocation(Player owner) {
        Location base = owner.getLocation();
        for (int radius = 0; radius <= 3; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    Location candidate = base.clone().add(dx + 0.5D, 0.0D, dz + 0.5D);
                    if (candidate.getY() < 1.0D || !isSafeSpawnBlock(candidate)) candidate = owner.getWorld().getHighestBlockAt(candidate).getLocation().add(0.5D, 1.0D, 0.5D);
                    if (candidate.getY() < 1.0D) candidate.setY(1.0D);
                    if (isSafeSpawnBlock(candidate) && redProtect.canSpawnPet(owner, candidate)) return candidate;
                }
            }
        }
        return null;
    }

    private boolean isSafeSpawnBlock(Location location) {
        Block feet = location.getBlock();
        Block head = location.clone().add(0.0D, 1.0D, 0.0D).getBlock();
        Block below = location.clone().add(0.0D, -1.0D, 0.0D).getBlock();
        return !feet.getType().isSolid() && !head.getType().isSolid() && below.getType().isSolid();
    }

    public void dismiss(Player player, boolean message) {
        UUID uuid = player != null ? player.getUniqueId() : null;
        if (uuid == null) return;
        VelioraPet active = activePets.remove(uuid);
        if (active != null) {
            removeAquaticAnchor(uuid.toString(), active.petId());
            if (!active.entity().isDead()) {
                active.entity().leaveVehicle();
                active.entity().remove();
            }
        }
        PlayerPetData pdata = data.get(uuid);
        pdata.activePet(null);
        data.save(uuid);
        if (message) player.sendMessage(config.color(config.message("pet-dismissed", "%prefix% &ePet kamu disimpan.")));
    }

    public void rename(Player player, String target, String name) {
        OwnedPet owned = resolveOwned(player, target);
        if (owned == null) {
            player.sendMessage(config.color(config.message("pet-not-owned", "%prefix% &cKamu belum punya pet &f%pet%&c.").replace("%pet%", target)));
            return;
        }
        owned.name(name);
        VelioraPet active = activePets.get(player.getUniqueId());
        if (active != null && active.petId().equalsIgnoreCase(owned.id())) active.entity().setCustomName(config.color(name));
        data.save(player.getUniqueId());
        player.sendMessage(config.color(config.message("pet-renamed", "%prefix% &aNama pet diubah menjadi &f%name%&a.").replace("%name%", name)));
    }

    public void feed(Player player, String target) {
        OwnedPet owned = resolveOwned(player, target);
        if (owned == null) {
            player.sendMessage(config.color(config.message("pet-not-owned", "%prefix% &cKamu belum punya pet &f%pet%&c.").replace("%pet%", target)));
            return;
        }
        PetDefinition definition = config.pets().get(owned.id());
        if (definition == null) return;
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType() != definition.foodMaterial()) {
            player.sendMessage(config.color(config.message("pet-feed-wrong-food", "%prefix% &cPet ini butuh makanan: &f%food%").replace("%food%", definition.foodMaterial().name())));
            return;
        }
        hand.setAmount(Math.max(0, hand.getAmount() - 1));
        owned.lastFed(System.currentTimeMillis());
        boolean leveled = owned.addExp(definition.feedExp(), config.maxLevel());
        data.save(player.getUniqueId());
        if (leveled) updateActiveScale(player, owned.id());
        player.sendMessage(config.color(config.message("pet-fed", "%prefix% &aPet &f%pet% &adiberi makan. EXP +&f%exp%&a.")
                .replace("%pet%", owned.name())
                .replace("%exp%", String.valueOf(definition.feedExp()))));
    }

    public void sendInfo(Player player, String target) {
        OwnedPet owned = resolveOwned(player, target);
        if (owned == null) {
            player.sendMessage(config.color(config.message("pet-not-owned", "%prefix% &cKamu belum punya pet &f%pet%&c.").replace("%pet%", target)));
            return;
        }
        PetDefinition definition = config.pets().get(owned.id());
        if (definition == null) return;
        boolean active = activePets.containsKey(player.getUniqueId()) && activePets.get(player.getUniqueId()).petId().equalsIgnoreCase(owned.id());
        player.sendMessage(config.color(config.message("pet-info-header", "%prefix% &dInfo Pet: &f%pet%").replace("%pet%", owned.name())));
        player.sendMessage(config.color("&7ID: &f" + owned.id()));
        player.sendMessage(config.color("&7Rarity: &f" + definition.rarity().name()));
        player.sendMessage(config.color("&7Level/EXP: &f" + owned.level() + " / " + owned.exp()));
        player.sendMessage(config.color("&7Food: &f" + definition.foodMaterial().name() + " (+" + definition.feedExp() + " EXP)"));
        player.sendMessage(config.color("&7Active: &f" + (active ? "yes" : "no")));
        player.sendMessage(config.color("&7Last fed: &f" + timeSince(owned.lastFed()) + " ago"));
        if (owned.cooldownUntil() > System.currentTimeMillis()) player.sendMessage(config.color("&7Cooldown: &c" + timeLeft(owned.cooldownUntil())));
    }

    public void saveStorage(Player player, String petId, Inventory inventory) { data.saveStorage(player.getUniqueId(), petId, inventory.getContents()); }

    public void setTarget(Player player, LivingEntity target) {
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;
        if (!isAllowedTarget(target)) return;
        VelioraPet active = activePets.get(player.getUniqueId());
        if (active == null) return;
        PetDefinition definition = config.pets().get(active.petId());
        if (definition != null && (definition.aquaticPet() || definition.flyingPet())) return;
        active.targetUuid(target.getUniqueId());
    }

    private LivingEntity spawnEntity(Player player, PetDefinition definition, OwnedPet owned, Location location) {
        Class<? extends Entity> rawClass = definition.entityType().getEntityClass();
        if (rawClass == null || !LivingEntity.class.isAssignableFrom(rawClass)) {
            plugin.getLogger().warning("VelioraPets DEBUG: no entity class mapping for " + definition.entityType());
            throw new IllegalStateException("no entity class mapping for " + definition.entityType());
        }
        Class<? extends LivingEntity> entityClass = rawClass.asSubclass(LivingEntity.class);
        return player.getWorld().spawn(location, entityClass, entity -> configureSpawnedEntity(entity, player, definition, owned));
    }

    private void configureSpawnedEntity(LivingEntity entity, Player player, PetDefinition definition, OwnedPet owned) {
        entity.addScoreboardTag(PET_TAG);
        entity.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, player.getUniqueId().toString());
        entity.getPersistentDataContainer().set(petIdKey, PersistentDataType.STRING, definition.id());
        entity.getPersistentDataContainer().set(rarityKey, PersistentDataType.STRING, definition.rarity().name());
        entity.getPersistentDataContainer().set(levelKey, PersistentDataType.INTEGER, owned.level());
        entity.setCustomName(config.color(definition.rarity().color() + owned.name()));
        entity.setCustomNameVisible(true);
        entity.setRemoveWhenFarAway(false);
        entity.setPersistent(true);
        entity.setCanPickupItems(false);
        entity.setCollidable(false);
        entity.setSilent(config.silentPets());
        entity.setGlowing(config.glowEnabled());
        entity.setFireTicks(0);
        entity.setFallDistance(0.0F);
        entity.removePotionEffect(PotionEffectType.INVISIBILITY);
        setInvisible(entity, false);
        entity.setAI(false);
        entity.setGravity(true);
        if (entity instanceof Mob mob) mob.setTarget(null);
        if (entity instanceof Creeper creeper) creeper.setPowered(false);
        tryBaby(entity);
        scaleHelper.apply(entity, scaleFor(definition, owned.level()));
    }

    private void tickFollowCombatLegacy() { }

    private void tickCosmetic() {
        if (!config.auraEnabled()) return;
        for (VelioraPet pet : activePets.values()) {
            if (pet.entity().isDead()) continue;
            int amount = config.lowLagParticles() ? 3 : 8;
            pet.entity().getWorld().spawnParticle(config.auraParticle(), pet.entity().getLocation().add(0, 0.7D, 0), amount, 0.25D, 0.25D, 0.25D, 0.01D);
        }
    }

    private boolean isAllowedTarget(LivingEntity target) {
        if (target == null || target instanceof Player || target.getScoreboardTags().contains(PET_TAG)) return false;
        if (!config.allowAttackPassive() && !(target instanceof Monster)) return false;
        return true;
    }

    private PetDefinition randomPet() {
        PetRarity rarity = rollRarity();
        List<PetDefinition> list = new ArrayList<>();
        for (PetDefinition definition : config.pets().values()) if (definition.rarity() == rarity) list.add(definition);
        if (list.isEmpty()) list.addAll(config.pets().values());
        return list.isEmpty() ? null : list.get(random.nextInt(list.size()));
    }

    private PetRarity rollRarity() {
        double total = 0.0D;
        for (double value : config.chances().values()) total += value;
        double roll = random.nextDouble() * Math.max(1.0D, total);
        double current = 0.0D;
        for (Map.Entry<PetRarity, Double> entry : config.chances().entrySet()) {
            current += entry.getValue();
            if (roll <= current) return entry.getKey();
        }
        return PetRarity.COMMON;
    }

    private OwnedPet resolveOwned(Player player, String target) {
        PlayerPetData pdata = data.get(player.getUniqueId());
        if (target == null || target.equalsIgnoreCase("active")) {
            VelioraPet active = activePets.get(player.getUniqueId());
            return active == null ? null : pdata.get(active.petId());
        }
        return pdata.get(target.toLowerCase(Locale.ROOT));
    }

    private void updateActiveScale(Player player, String petId) {
        VelioraPet active = activePets.get(player.getUniqueId());
        if (active == null || !active.petId().equalsIgnoreCase(petId)) return;
        OwnedPet owned = data.get(player.getUniqueId()).get(petId);
        PetDefinition definition = config.pets().get(petId);
        if (owned == null || definition == null) return;
        active.entity().getPersistentDataContainer().set(levelKey, PersistentDataType.INTEGER, owned.level());
        scaleHelper.apply(active.entity(), scaleFor(definition, owned.level()));
    }

    private double scaleFor(PetDefinition definition, int level) {
        double bonus = Math.min(config.maxScaleBonus(), Math.max(0, level - 1) * config.scalePerLevel());
        return definition.scale() + bonus;
    }

    private void sendFoodHint(Player player, PetDefinition definition) {
        player.sendMessage(config.color(config.message("pet-food-info", "%prefix% &7Makanan pet ini: &f%food%").replace("%food%", definition.foodMaterial().name())));
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) { dismiss(event.getPlayer(), false); }
    @EventHandler public void onJoin(PlayerJoinEvent event) { if (config.autoSummonLastPet()) { String last = data.get(event.getPlayer().getUniqueId()).lastPet(); if (last != null) plugin.getServer().getScheduler().runTaskLater(plugin, () -> summon(event.getPlayer(), last), 20L); } }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager().getScoreboardTags().contains(PET_TAG)) {
            if (event.getEntity() instanceof Player || event.getEntity().getScoreboardTags().contains(PET_TAG)) event.setCancelled(true);
            return;
        }
        if (event.getEntity().getScoreboardTags().contains(PET_TAG)) {
            if (event.getDamager() instanceof Player) event.setCancelled(true);
            return;
        }
        if (event.getDamager() instanceof Player player && event.getEntity() instanceof LivingEntity target) {
            if (player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE) setTarget(player, target);
        }
        if (event.getEntity() instanceof Player player && event.getDamager() instanceof LivingEntity attacker) {
            if (player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE) setTarget(player, attacker);
        }
    }

    @EventHandler public void onTarget(EntityTargetLivingEntityEvent event) { if (event.getEntity().getScoreboardTags().contains(PET_TAG)) event.setCancelled(true); if (event.getTarget() != null && event.getTarget().getScoreboardTags().contains(PET_TAG)) event.setCancelled(true); }

    @EventHandler
    public void onPetEntityRemoved(EntityDeathEvent event) {
        if (!event.getEntity().getScoreboardTags().contains(PET_TAG)) return;
        event.getDrops().clear();
        event.setDroppedExp(0);
        String ownerRaw = event.getEntity().getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        String petId = event.getEntity().getPersistentDataContainer().get(petIdKey, PersistentDataType.STRING);
        if (ownerRaw == null || petId == null) return;
        removeAquaticAnchor(ownerRaw, petId);
        UUID uuid = UUID.fromString(ownerRaw);
        activePets.remove(uuid);
        PlayerPetData pdata = data.get(uuid);
        pdata.activePet(null);
        OwnedPet owned = pdata.get(petId);
        long until = System.currentTimeMillis() + config.deathCooldownMinutes() * 60_000L;
        if (owned != null) owned.cooldownUntil(until);
        pdata.cooldownUntil(0L);
        data.save(uuid);
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) player.sendMessage(config.color(config.message("pet-dead", "%prefix% &cPet kamu mati. Bisa dipanggil lagi dalam &f%time%&c.").replace("%time%", timeLeft(until))));
    }

    @EventHandler public void onExplode(EntityExplodeEvent event) { if (event.getEntity() != null && event.getEntity().getScoreboardTags().contains(PET_TAG)) { event.blockList().clear(); event.setCancelled(true); } }
    @EventHandler public void onPrime(ExplosionPrimeEvent event) { if (event.getEntity().getScoreboardTags().contains(PET_TAG)) event.setCancelled(true); }

    private void removeAquaticAnchor(String ownerRaw, String petId) {
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (!entity.getScoreboardTags().contains(AQUATIC_ANCHOR_TAG)) continue;
                String anchorOwner = entity.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
                String anchorPet = entity.getPersistentDataContainer().get(petIdKey, PersistentDataType.STRING);
                if (ownerRaw.equals(anchorOwner) && petId.equals(anchorPet)) entity.remove();
            }
        }
    }

    private void cleanupAllEntities() {
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity.getScoreboardTags().contains(PET_TAG) || entity.getScoreboardTags().contains(AQUATIC_ANCHOR_TAG)) entity.remove();
            }
        }
    }

    private void stopTasks() { if (followTask != null) followTask.cancel(); if (cosmeticTask != null) cosmeticTask.cancel(); followTask = null; cosmeticTask = null; }
    private void tryBaby(LivingEntity entity) { try { Method method = entity.getClass().getMethod("setBaby", boolean.class); method.invoke(entity, true); } catch (Exception ignored) { try { Method method = entity.getClass().getMethod("setBaby"); method.invoke(entity); } catch (Exception ignored2) { } } }
    private void setInvisible(LivingEntity entity, boolean invisible) { try { Method method = entity.getClass().getMethod("setInvisible", boolean.class); method.invoke(entity, invisible); } catch (Exception ignored) { } }
    private String timeLeft(long target) { long seconds = Math.max(0L, (target - System.currentTimeMillis()) / 1000L); return (seconds / 60L) + "m " + (seconds % 60L) + "s"; }
    private String timeSince(long past) { long seconds = Math.max(0L, (System.currentTimeMillis() - past) / 1000L); return (seconds / 60L) + "m " + (seconds % 60L) + "s"; }
}
