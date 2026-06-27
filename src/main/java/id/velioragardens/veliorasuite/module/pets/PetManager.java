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
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;
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

    public void load() { config.load(); data.load(); }

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
    public PlayerPetData playerData(UUID uuid) { return data.get(uuid); }
    public VelioraPet activePet(UUID uuid) { return activePets.get(uuid); }

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
            if (config.autoSummonNewPet()) summon(player, result.id());
        }
    }

    public boolean givePet(Player player, String petId, boolean silent) {
        PetDefinition definition = config.pets().get(petId.toLowerCase(Locale.ROOT));
        if (definition == null) return false;
        PlayerPetData pdata = data.get(player.getUniqueId());
        if (!pdata.owns(definition.id())) pdata.add(new OwnedPet(definition.id(), 1, 0, config.plain(definition.displayName())));
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

    public void rename(Player player, String target, String name) {
        OwnedPet owned = resolveOwned(player, target);
        if (owned == null) {
            player.sendMessage(config.color(config.message("pet-not-owned", "%prefix% &cKamu belum punya pet itu.")));
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
            player.sendMessage(config.color(config.message("pet-not-owned", "%prefix% &cKamu belum punya pet itu.")));
            return;
        }
        PetDefinition definition = config.pets().get(owned.id());
        if (definition == null) return;
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType() != definition.foodMaterial()) {
            player.sendMessage(config.color(config.message("pet-feed-wrong-food", "%prefix% &cPet ini butuh makanan: &f%food%")
                    .replace("%food%", definition.foodMaterial().name())));
            return;
        }
        hand.setAmount(hand.getAmount() - 1);
        boolean leveled = owned.addExp(definition.feedExp(), config.maxLevel());
        data.save(player.getUniqueId());
        if (leveled) updateActiveScale(player, owned.id());
        player.sendMessage(config.color(config.message("pet-fed", "%prefix% &aPet &f%pet% &adiberi makan. EXP +&f%exp%&a.")
                .replace("%pet%", owned.name())
                .replace("%exp%", String.valueOf(definition.feedExp()))));
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
        entity.setAI(!definition.flyingPet());
        entity.setCollidable(false);
        entity.setCanPickupItems(false);
        entity.setGlowing(config.glowEnabled());
        if (entity instanceof Mob mob) mob.setTarget(null);
        tryBaby(entity);
        if (entity instanceof Creeper creeper) creeper.setPowered(false);
        scaleHelper.apply(entity, scaleFor(definition, owned.level()));
        return entity;
    }

    private void tickFollowCombat() {
        long now = System.currentTimeMillis();
        for (UUID uuid : new ArrayList<>(activePets.keySet())) {
            Player owner = Bukkit.getPlayer(uuid);
            VelioraPet pet = activePets.get(uuid);
            if (owner == null || !owner.isOnline()) { if (pet != null && !pet.entity().isDead()) pet.entity().remove(); activePets.remove(uuid); continue; }
            if (pet == null || pet.entity().isDead()) { activePets.remove(uuid); continue; }
            PetDefinition definition = config.pets().get(pet.petId());
            follow(owner, pet, definition);
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

    private void follow(Player owner, VelioraPet active, PetDefinition definition) {
        LivingEntity pet = active.entity();
        Location ownerLocation = owner.getLocation();
        if (pet instanceof Mob mob) mob.setTarget(null);
        if (!pet.getWorld().equals(owner.getWorld()) || pet.getLocation().distanceSquared(ownerLocation) > 144.0D) {
            pet.teleport(ownerLocation.clone().add(1.0D, 0.0D, 1.0D));
            return;
        }
        double distance = pet.getLocation().distanceSquared(ownerLocation);
        if (distance > 9.0D) {
            Location destination = ownerLocation.clone().add(-1.2D, 0.0D, -1.2D);
            boolean pathing = definition != null && !definition.flyingPet() && config.usePathfinderFollow() && moveWithPathfinder(pet, destination);
            if (!pathing) moveWithVelocity(pet, destination, definition != null && definition.flyingPet() ? 0.28D : 0.35D);
        }
    }

    private void attackIfPossible(Player owner, VelioraPet pet, long now) {
        if (!config.combatEnabled() || pet.targetUuid() == null) return;
        Entity target = Bukkit.getEntity(pet.targetUuid());
        if (!(target instanceof LivingEntity living) || living.isDead() || !isAllowedTarget(living)) { pet.targetUuid(null); return; }
        if (!living.getWorld().equals(pet.entity().getWorld())) { pet.targetUuid(null); return; }
        if (pet.entity().getLocation().distanceSquared(living.getLocation()) > 64.0D) { follow(owner, pet, config.pets().get(pet.petId())); return; }
        if (pet.entity().getLocation().distanceSquared(living.getLocation()) > config.attackRange() * config.attackRange()) {
            PetDefinition definition = config.pets().get(pet.petId());
            boolean pathing = definition != null && !definition.flyingPet() && config.usePathfinderFollow() && moveWithPathfinder(pet.entity(), living.getLocation());
            if (!pathing) moveWithVelocity(pet.entity(), living.getLocation(), definition != null && definition.flyingPet() ? 0.32D : 0.45D);
            return;
        }
        if (now - pet.lastAttackMillis() < config.attackCooldownSeconds() * 1000L) return;
        PetDefinition definition = config.pets().get(pet.petId());
        double damage = definition == null ? 1.0D : definition.damage() * config.petDamageMultiplier();
        living.damage(damage, owner);
        pet.entity().getWorld().playSound(pet.entity().getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.5F, 1.4F);
        pet.lastAttackMillis(now);
        OwnedPet owned = data.get(owner.getUniqueId()).get(pet.petId());
        if (owned != null) {
            boolean leveled = owned.addExp(2, config.maxLevel());
            data.save(owner.getUniqueId());
            if (leveled) updateActiveScale(owner, owned.id());
        }
    }

    private boolean moveWithPathfinder(LivingEntity pet, Location destination) {
        try {
            Method getPathfinder = pet.getClass().getMethod("getPathfinder");
            Object pathfinder = getPathfinder.invoke(pet);
            try {
                Method moveTo = pathfinder.getClass().getMethod("moveTo", Location.class, double.class);
                moveTo.invoke(pathfinder, destination, 1.15D);
                return true;
            } catch (NoSuchMethodException ignored) {
                Method moveTo = pathfinder.getClass().getMethod("moveTo", Location.class);
                moveTo.invoke(pathfinder, destination);
                return true;
            }
        } catch (Exception ignored) {
            return false;
        }
    }

    private void moveWithVelocity(LivingEntity pet, Location destination, double speed) {
        Vector velocity = destination.toVector().subtract(pet.getLocation().toVector());
        if (velocity.lengthSquared() <= 0.01D) return;
        pet.setVelocity(velocity.normalize().multiply(speed));
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
        if (event.getEntity().getScoreboardTags().contains("veliorapets_pet")) {
            if (event.getDamager() instanceof Player) event.setCancelled(true);
            return;
        }
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
    public void onPotion(EntityPotionEffectEvent event) {
        if (event.getEntity().getScoreboardTags().contains("veliorapets_pet")) return;
        if (event.getNewEffect() == null) return;
        PotionEffectType type = event.getNewEffect().getType();
        if (!"DARKNESS".equals(type.getName()) && !"BLINDNESS".equals(type.getName())) return;
        for (VelioraPet pet : activePets.values()) {
            if (!pet.entity().getWorld().equals(event.getEntity().getWorld())) continue;
            if (pet.entity().getLocation().distanceSquared(event.getEntity().getLocation()) <= 144.0D && pet.entity().getType().name().contains("WARDEN")) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onProjectile(ProjectileLaunchEvent event) {
        if (event.getEntity().getShooter() instanceof Entity shooter && shooter.getScoreboardTags().contains("veliorapets_pet")) event.setCancelled(true);
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
