package id.velioragardens.veliorasuite.module.pets;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.module.pets.model.OwnedPet;
import id.velioragardens.veliorasuite.module.pets.model.PetDefinition;
import id.velioragardens.veliorasuite.module.pets.model.PetRarity;
import id.velioragardens.veliorasuite.module.pets.model.PlayerPetData;
import id.velioragardens.veliorasuite.module.pets.model.VelioraPet;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
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
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public final class PetManager implements Listener {
    private final VelioraSuite plugin;
    private final PetConfigManager config;
    private final PetDataManager data;
    private final PetEconomyManager economy;
    private final PetScaleHelper scaleHelper;
    private final Map<UUID, VelioraPet> activePets = new HashMap<>();
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
        this.ownerKey = new NamespacedKey(plugin, "veliorapets_owner_uuid");
        this.petIdKey = new NamespacedKey(plugin, "veliorapets_pet_id");
        this.rarityKey = new NamespacedKey(plugin, "veliorapets_rarity");
        this.levelKey = new NamespacedKey(plugin, "veliorapets_level");
    }

    public void load() {
        config.load();
        data.load();
    }

    public void start(PetGuiManager guiManager) {
        this.guiManager = guiManager;
        stopTasks();
        followTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickFollowCombat, 20L, 20L);
        cosmeticTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickCosmetic, 20L * config.auraIntervalSeconds(), 20L * config.auraIntervalSeconds());
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
        followTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickFollowCombat, 20L, 20L);
        cosmeticTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickCosmetic, 20L * config.auraIntervalSeconds(), 20L * config.auraIntervalSeconds());
    }

    public PetConfigManager config() { return config; }
    public PetDataManager data() { return data; }
    public PetGuiManager gui() { return guiManager; }
    public PlayerPetData playerData(UUID uuid) { return data.get(uuid); }
    public VelioraPet activePet(UUID uuid) { return activePets.get(uuid); }
    public boolean hasActivePet(UUID uuid) { return activePets.containsKey(uuid); }

    public void openMain(Player player) { guiManager.openMain(player); }
    public void openShop(Player player) { guiManager.openShop(player); }
    public void openGacha(Player player) { guiManager.openGacha(player); }
    public void openList(Player player) { guiManager.openList(player); }

    public void openStorage(Player player) {
        VelioraPet active = activePets.get(player.getUniqueId());
        PlayerPetData pdata = data.get(player.getUniqueId());
        String petId = active != null ? active.petId() : pdata.lastPet();
        if (petId == null || (!config.allowStorageWithoutActive() && active == null)) {
            player.sendMessage(config.color(config.message("pet-storage-open", "%prefix% &cTidak ada pet aktif untuk membuka storage.")));
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
        givePet(player, definition.id(), false);
        player.sendMessage(config.color(config.message("pet-bought", "%prefix% &aKamu membeli pet &f%pet%&a.").replace("%pet%", config.color(definition.displayName()))));
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
            if (owned != null) owned.addExp(config.duplicateExp(), config.maxLevel());
            data.save(player.getUniqueId());
            player.sendMessage(config.color(config.message("already-owned", "%prefix% &eKamu sudah punya pet ini.")));
        } else {
            givePet(player, result.id(), false);
            player.sendMessage(config.color(config.message("gacha-result", "%prefix% &aKamu mendapatkan pet &f%pet% &7(%rarity%&7)&a!")
                    .replace("%pet%", config.color(result.displayName()))
                    .replace("%rarity%", result.rarity().name())));
            if (config.autoSummonNewPet()) summon(player, result.id());
        }
    }

    public boolean givePet(Player player, String petId, boolean silent) {
        PetDefinition definition = config.pets().get(petId.toLowerCase(Locale.ROOT));
        if (definition == null) return false;
        PlayerPetData pdata = data.get(player.getUniqueId());
        if (!pdata.owns(definition.id())) pdata.add(new OwnedPet(definition.id(), 1, 0, config.plain(definition.displayName())));
        data.save(player.getUniqueId());
        if (!silent) player.sendMessage(config.color(config.message("gacha-result", "%prefix% &aKamu mendapatkan pet &f%pet% &7(%rarity%&7)&a!").replace("%pet%", config.color(definition.displayName())).replace("%rarity%", definition.rarity().name())));
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
        PetDefinition definition = config.pets().get(petId.toLowerCase(Locale.ROOT));
        PlayerPetData pdata = data.get(player.getUniqueId());
        if (definition == null || !pdata.owns(definition.id())) return false;
        long now = System.currentTimeMillis();
        if (pdata.cooldownUntil() > now) {
            player.sendMessage(config.color(config.message("pet-cooldown", "%prefix% &cPet masih cooldown: &f%time%").replace("%time%", timeLeft(pdata.cooldownUntil()))));
            return false;
        }
        dismiss(player, false);
        LivingEntity entity = spawnEntity(player, definition, pdata.get(definition.id()));
        VelioraPet runtime = new VelioraPet(player.getUniqueId(), definition.id(), entity);
        activePets.put(player.getUniqueId(), runtime);
        pdata.activePet(definition.id());
        pdata.lastPet(definition.id());
        data.save(player.getUniqueId());
        player.sendMessage(config.color(config.message("pet-summoned", "%prefix% &aPet &f%pet% &adipanggil.").replace("%pet%", config.color(pdata.get(definition.id()).name()))));
        return true;
    }

    public void dismiss(Player player, boolean message) {
        UUID uuid = player != null ? player.getUniqueId() : null;
        if (uuid == null) return;
        VelioraPet active = activePets.remove(uuid);
        if (active != null && !active.entity().isDead()) active.entity().remove();
        PlayerPetData pdata = data.get(uuid);
        pdata.activePet(null);
        data.save(uuid);
        if (message) player.sendMessage(config.color(config.message("pet-dismissed", "%prefix% &ePet kamu disimpan.")));
    }

    public void rename(Player player, String name) {
        VelioraPet active = activePets.get(player.getUniqueId());
        if (active == null) return;
        OwnedPet owned = data.get(player.getUniqueId()).get(active.petId());
        if (owned == null) return;
        owned.name(name);
        active.entity().setCustomName(config.color(name));
        data.save(player.getUniqueId());
        player.sendMessage(config.color(config.message("pet-renamed", "%prefix% &aNama pet diubah menjadi &f%name%&a.").replace("%name%", name)));
    }

    public void saveStorage(Player player, String petId, Inventory inventory) {
        data.saveStorage(player.getUniqueId(), petId, inventory.getContents());
    }

    public void setTarget(Player player, LivingEntity target) {
        if (!isAllowedTarget(target)) return;
        VelioraPet active = activePets.get(player.getUniqueId());
        if (active != null) active.targetUuid(target.getUniqueId());
    }

    private LivingEntity spawnEntity(Player player, PetDefinition definition, OwnedPet owned) {
        Location location = player.getLocation().clone().add(1.0D, 0.0D, 1.0D);
        Entity raw = player.getWorld().spawnEntity(location, definition.entityType());
        LivingEntity entity = (LivingEntity) raw;
        entity.addScoreboardTag("veliorapets_pet");
        entity.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, player.getUniqueId().toString());
        entity.getPersistentDataContainer().set(petIdKey, PersistentDataType.STRING, definition.id());
        entity.getPersistentDataContainer().set(rarityKey, PersistentDataType.STRING, definition.rarity().name());
        entity.getPersistentDataContainer().set(levelKey, PersistentDataType.INTEGER, owned.level());
        entity.setCustomName(config.color(definition.rarity().color() + owned.name()));
        entity.setCustomNameVisible(true);
        entity.setRemoveWhenFarAway(false);
        entity.setPersistent(false);
        entity.setAI(false);
        entity.setCollidable(false);
        entity.setCanPickupItems(false);
        entity.setGlowing(config.glowEnabled());
        tryBaby(entity);
        if (entity instanceof Creeper creeper) creeper.setPowered(false);
        scaleHelper.apply(entity, definition.scale());
        return entity;
    }

    private void tickFollowCombat() {
        long now = System.currentTimeMillis();
        for (UUID uuid : new ArrayList<>(activePets.keySet())) {
            Player owner = Bukkit.getPlayer(uuid);
            VelioraPet pet = activePets.get(uuid);
            if (owner == null || !owner.isOnline()) { if (pet != null && !pet.entity().isDead()) pet.entity().remove(); activePets.remove(uuid); continue; }
            if (pet == null || pet.entity().isDead()) { activePets.remove(uuid); continue; }
            follow(owner, pet.entity());
            attackIfPossible(owner, pet, now);
        }
    }

    private void tickCosmetic() {
        if (!config.auraEnabled()) return;
        for (VelioraPet pet : activePets.values()) {
            if (pet.entity().isDead()) continue;
            int amount = config.lowLagParticles() ? 3 : 8;
            pet.entity().getWorld().spawnParticle(config.auraParticle(), pet.entity().getLocation().add(0, 0.7D, 0), amount, 0.25D, 0.25D, 0.25D, 0.01D);
        }
    }

    private void follow(Player owner, LivingEntity pet) {
        Location ownerLocation = owner.getLocation();
        if (!pet.getWorld().equals(owner.getWorld()) || pet.getLocation().distanceSquared(ownerLocation) > 144.0D) {
            pet.teleport(ownerLocation.clone().add(1.0D, 0.0D, 1.0D));
            return;
        }
        double distance = pet.getLocation().distanceSquared(ownerLocation);
        if (distance > 9.0D) {
            Vector velocity = ownerLocation.toVector().subtract(pet.getLocation().toVector()).normalize().multiply(0.35D);
            pet.setVelocity(velocity);
        }
    }

    private void attackIfPossible(Player owner, VelioraPet pet, long now) {
        if (!config.combatEnabled() || pet.targetUuid() == null) return;
        Entity target = Bukkit.getEntity(pet.targetUuid());
        if (!(target instanceof LivingEntity living) || living.isDead() || !isAllowedTarget(living)) { pet.targetUuid(null); return; }
        if (!living.getWorld().equals(pet.entity().getWorld())) { pet.targetUuid(null); return; }
        if (pet.entity().getLocation().distanceSquared(living.getLocation()) > 64.0D) { follow(owner, pet.entity()); return; }
        if (pet.entity().getLocation().distanceSquared(living.getLocation()) > config.attackRange() * config.attackRange()) {
            Vector velocity = living.getLocation().toVector().subtract(pet.entity().getLocation().toVector()).normalize().multiply(0.45D);
            pet.entity().setVelocity(velocity);
            return;
        }
        if (now - pet.lastAttackMillis() < config.attackCooldownSeconds() * 1000L) return;
        PetDefinition definition = config.pets().get(pet.petId());
        double damage = definition == null ? 1.0D : definition.damage() * config.petDamageMultiplier();
        living.damage(damage, owner);
        pet.entity().getWorld().playSound(pet.entity().getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.5F, 1.4F);
        pet.lastAttackMillis(now);
        OwnedPet owned = data.get(owner.getUniqueId()).get(pet.petId());
        if (owned != null) { owned.addExp(2, config.maxLevel()); data.save(owner.getUniqueId()); }
    }

    private boolean isAllowedTarget(LivingEntity target) {
        if (target == null || target instanceof Player || target.getScoreboardTags().contains("veliorapets_pet")) return false;
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

    @EventHandler
    public void onQuit(PlayerQuitEvent event) { dismiss(event.getPlayer(), false); }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!config.autoSummonLastPet()) return;
        String last = data.get(event.getPlayer().getUniqueId()).lastPet();
        if (last != null) plugin.getServer().getScheduler().runTaskLater(plugin, () -> summon(event.getPlayer(), last), 20L);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager().getScoreboardTags().contains("veliorapets_pet")) {
            if (event.getEntity() instanceof Player || event.getEntity().getScoreboardTags().contains("veliorapets_pet")) event.setCancelled(true);
            return;
        }
        if (event.getEntity().getScoreboardTags().contains("veliorapets_pet")) return;
        if (event.getDamager() instanceof Player player && event.getEntity() instanceof LivingEntity target) setTarget(player, target);
        if (event.getEntity() instanceof Player player && event.getDamager() instanceof LivingEntity attacker) setTarget(player, attacker);
    }

    @EventHandler
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (event.getEntity().getScoreboardTags().contains("veliorapets_pet")) event.setCancelled(true);
        if (event.getTarget() != null && event.getTarget().getScoreboardTags().contains("veliorapets_pet")) event.setCancelled(true);
    }

    @EventHandler
    public void onPetDeath(EntityDeathEvent event) {
        if (!event.getEntity().getScoreboardTags().contains("veliorapets_pet")) return;
        event.getDrops().clear();
        event.setDroppedExp(0);
        String ownerRaw = event.getEntity().getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        if (ownerRaw == null) return;
        UUID uuid = UUID.fromString(ownerRaw);
        activePets.remove(uuid);
        PlayerPetData pdata = data.get(uuid);
        pdata.activePet(null);
        pdata.cooldownUntil(System.currentTimeMillis() + config.deathCooldownMinutes() * 60_000L);
        data.save(uuid);
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) player.sendMessage(config.color(config.message("pet-dead", "%prefix% &cPet kamu mati. Bisa dipanggil lagi dalam &f%time%&c.").replace("%time%", timeLeft(pdata.cooldownUntil()))));
    }

    @EventHandler
    public void onExplode(EntityExplodeEvent event) { if (event.getEntity() != null && event.getEntity().getScoreboardTags().contains("veliorapets_pet")) { event.blockList().clear(); event.setCancelled(true); } }
    @EventHandler
    public void onPrime(ExplosionPrimeEvent event) { if (event.getEntity().getScoreboardTags().contains("veliorapets_pet")) event.setCancelled(true); }

    private void cleanupAllEntities() {
        for (org.bukkit.World world : Bukkit.getWorlds()) for (Entity entity : world.getEntities()) if (entity.getScoreboardTags().contains("veliorapets_pet")) entity.remove();
    }

    private void stopTasks() { if (followTask != null) followTask.cancel(); if (cosmeticTask != null) cosmeticTask.cancel(); followTask = null; cosmeticTask = null; }
    private void tryBaby(LivingEntity entity) { try { Method method = entity.getClass().getMethod("setBaby", boolean.class); method.invoke(entity, true); } catch (Exception ignored) { try { Method method = entity.getClass().getMethod("setBaby"); method.invoke(entity); } catch (Exception ignored2) { } } }
    private String timeLeft(long target) { long seconds = Math.max(0L, (target - System.currentTimeMillis()) / 1000L); return (seconds / 60L) + "m " + (seconds % 60L) + "s"; }
}
