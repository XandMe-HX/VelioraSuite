package id.velioragardens.veliorasuite.module.actionhouse;

import id.velioragardens.veliorasuite.VelioraSuite;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;

/** Persistent marketplace state. All calls are made on the server thread. */
final class ActionHouseStore {
    record Listing(String id, UUID owner, String ownerName, ItemStack item, double price, long createdAt, long expiresAt, long promotedUntil) {}
    private final VelioraSuite plugin;
    private final int defaultSlots;
    private final File file;
    private final Map<String, Listing> listings = new LinkedHashMap<>();
    private final Map<UUID, Double> pending = new HashMap<>();
    private final Map<UUID, Integer> slots = new HashMap<>();
    private final Set<UUID> shopOwners = new HashSet<>();
    private final Map<UUID, List<ItemStack>> expired = new HashMap<>();

    ActionHouseStore(VelioraSuite plugin, int defaultSlots) {
        this.plugin = plugin;
        this.defaultSlots = Math.max(1, defaultSlots);
        this.file = new File(plugin.getDataFolder(), "modules/actionhouse-data.yml");
    }

    void load() {
        listings.clear(); pending.clear(); slots.clear(); expired.clear(); shopOwners.clear();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection source = yaml.getConfigurationSection("listings");
        if (source != null) for (String id : source.getKeys(false)) {
            String p = "listings." + id + ".";
            try {
                UUID owner = UUID.fromString(yaml.getString(p + "owner", ""));
                ItemStack item = yaml.getItemStack(p + "item");
                if (item == null || item.getType().isAir()) continue;
                listings.put(id, new Listing(id, owner, yaml.getString(p + "owner-name", "Unknown"), item,
                    Math.max(1D, yaml.getDouble(p + "price")), yaml.getLong(p + "created-at"), yaml.getLong(p + "expires-at"), yaml.getLong(p + "promoted-until")));
            } catch (IllegalArgumentException ignored) { plugin.getLogger().warning("ActionHouse: listing rusak dilewati: " + id); }
        }
        ConfigurationSection profiles = yaml.getConfigurationSection("profiles");
        if (profiles != null) for (String raw : profiles.getKeys(false)) try {
            UUID uuid = UUID.fromString(raw); String p = "profiles." + raw + ".";
            pending.put(uuid, Math.max(0D, yaml.getDouble(p + "pending")));
            slots.put(uuid, Math.max(1, yaml.getInt(p + "slots", defaultSlots)));
            if (yaml.getBoolean(p + "shop-owned", false)) shopOwners.add(uuid);
            List<ItemStack> items = new ArrayList<>();
            for (Object value : yaml.getList(p + "expired", List.of())) if (value instanceof ItemStack item && !item.getType().isAir()) items.add(item);
            if (!items.isEmpty()) expired.put(uuid, items);
        } catch (IllegalArgumentException ignored) { }
    }

    void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Listing l : listings.values()) { String p = "listings." + l.id + "."; yaml.set(p + "owner", l.owner.toString()); yaml.set(p + "owner-name", l.ownerName); yaml.set(p + "item", l.item); yaml.set(p + "price", l.price); yaml.set(p + "created-at", l.createdAt); yaml.set(p + "expires-at", l.expiresAt); yaml.set(p + "promoted-until", l.promotedUntil); }
        Set<UUID> owners = new HashSet<>(); owners.addAll(pending.keySet()); owners.addAll(slots.keySet()); owners.addAll(expired.keySet()); owners.addAll(shopOwners);
        for (UUID owner : owners) { String p = "profiles." + owner + "."; yaml.set(p + "pending", pending.getOrDefault(owner, 0D)); yaml.set(p + "slots", slots.getOrDefault(owner, defaultSlots)); yaml.set(p + "shop-owned", shopOwners.contains(owner)); yaml.set(p + "expired", expired.getOrDefault(owner, List.of())); }
        try { File parent = file.getParentFile(); if (parent != null) parent.mkdirs(); yaml.save(file); } catch (IOException e) { plugin.getLogger().warning("ActionHouse gagal disimpan: " + e.getMessage()); }
    }

    List<Listing> browse() { return listings.values().stream().sorted(Comparator.comparingLong(Listing::promotedUntil).reversed().thenComparing(Comparator.comparingLong(Listing::createdAt).reversed())).toList(); }
    List<Listing> owned(UUID owner) { return listings.values().stream().filter(l -> l.owner.equals(owner)).sorted(Comparator.comparingLong(Listing::createdAt).reversed()).toList(); }
    Listing get(String id) { return listings.get(id); }
    void add(UUID owner, String ownerName, ItemStack item, double price, long lifeMillis) { long now = System.currentTimeMillis(); String id = UUID.randomUUID().toString().replace("-", ""); listings.put(id, new Listing(id, owner, ownerName, item.clone(), price, now, now + lifeMillis, 0L)); save(); }
    boolean remove(String id) { boolean result = listings.remove(id) != null; if (result) save(); return result; }
    int capacity(UUID owner) { return slots.getOrDefault(owner, defaultSlots); }
    boolean hasShop(UUID owner) { return shopOwners.contains(owner); }
    void buyShop(UUID owner) { shopOwners.add(owner); slots.putIfAbsent(owner, defaultSlots); save(); }
    void addSlot(UUID owner) { slots.put(owner, capacity(owner) + 1); save(); }
    double pending(UUID owner) { return pending.getOrDefault(owner, 0D); }
    void addPending(UUID owner, double amount) { pending.merge(owner, amount, Double::sum); save(); }
    void reducePending(UUID owner, double amount) { pending.put(owner, Math.max(0D, pending(owner) - amount)); save(); }
    List<ItemStack> expired(UUID owner) { return new ArrayList<>(expired.getOrDefault(owner, List.of())); }
    ItemStack takeExpired(UUID owner, int index) { List<ItemStack> items = expired.get(owner); if (items == null || index < 0 || index >= items.size()) return null; ItemStack item = items.remove(index); if (items.isEmpty()) expired.remove(owner); save(); return item; }
    void promote(UUID owner, long until) { for (Listing l : new ArrayList<>(listings.values())) if (l.owner.equals(owner)) listings.put(l.id, new Listing(l.id,l.owner,l.ownerName,l.item,l.price,l.createdAt,l.expiresAt,Math.max(l.promotedUntil,until))); save(); }
    void expire() { long now = System.currentTimeMillis(); boolean changed = false; for (Listing l : new ArrayList<>(listings.values())) if (l.expiresAt <= now) { listings.remove(l.id); expired.computeIfAbsent(l.owner, x -> new ArrayList<>()).add(l.item); changed = true; } if (changed) save(); }
}
