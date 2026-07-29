package id.velioragardens.veliorasuite.module.pets;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.module.pets.model.OwnedPet;
import id.velioragardens.veliorasuite.module.pets.model.PlayerPetData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class PetDataManager {
    public static final int SHARED_STORAGE_SIZE = 27;

    private final VelioraSuite plugin;
    private File file;
    private FileConfiguration data;
    private final Map<UUID, PlayerPetData> cache = new HashMap<>();

    public PetDataManager(VelioraSuite plugin) { this.plugin = plugin; }

    public void load() {
        plugin.createFolder("data");
        file = new File(plugin.getDataFolder(), "data/pets.yml");
        if (!file.exists()) try { file.createNewFile(); } catch (IOException exception) { plugin.getLogger().warning("Gagal membuat pets.yml"); }
        data = YamlConfiguration.loadConfiguration(file);
        cache.clear();
    }

    public PlayerPetData get(UUID uuid) {
        return cache.computeIfAbsent(uuid, this::loadPlayer);
    }

    public void save(UUID uuid) {
        PlayerPetData player = get(uuid);
        String path = "players." + uuid;
        migrateLegacyStorage(uuid);
        data.set(path + ".active-pet", player.activePet());
        data.set(path + ".last-pet", player.lastPet());
        data.set(path + ".cooldown-until", null);
        data.set(path + ".owned", null);
        for (OwnedPet pet : player.owned().values()) {
            String petPath = path + ".owned." + pet.id().toLowerCase(Locale.ROOT);
            data.set(petPath + ".level", pet.level());
            data.set(petPath + ".exp", pet.exp());
            data.set(petPath + ".name", pet.name());
            data.set(petPath + ".cooldown-until", pet.cooldownUntil());
            data.set(petPath + ".last-fed", pet.lastFed());
        }
        saveFile();
    }

    public void saveAll() {
        for (UUID uuid : new ArrayList<>(cache.keySet())) save(uuid);
    }

    public List<ItemStack> loadStorage(UUID uuid) {
        migrateLegacyStorage(uuid);
        return readItems("players." + uuid + ".storage", SHARED_STORAGE_SIZE);
    }

    public void saveStorage(UUID uuid, ItemStack[] contents) {
        migrateLegacyStorage(uuid);
        data.set("players." + uuid + ".storage", normalizeContents(contents));
        saveFile();
    }

    public List<ItemStack> loadStorageOverflow(UUID uuid) {
        migrateLegacyStorage(uuid);
        return readItems("players." + uuid + ".storage-overflow", Integer.MAX_VALUE);
    }

    public void saveStorageOverflow(UUID uuid, List<ItemStack> items) {
        String path = "players." + uuid + ".storage-overflow";
        List<ItemStack> clean = new ArrayList<>();
        for (ItemStack item : items) {
            if (item != null && !item.getType().isAir() && item.getAmount() > 0) clean.add(item.clone());
        }
        data.set(path, clean.isEmpty() ? null : clean);
        saveFile();
    }

    private PlayerPetData loadPlayer(UUID uuid) {
        PlayerPetData player = new PlayerPetData();
        String path = "players." + uuid;
        player.activePet(data.getString(path + ".active-pet", null));
        player.lastPet(data.getString(path + ".last-pet", null));
        long oldGlobalCooldown = data.getLong(path + ".cooldown-until", 0L);
        ConfigurationSection owned = data.getConfigurationSection(path + ".owned");
        if (owned != null) {
            for (String id : owned.getKeys(false)) {
                String petPath = path + ".owned." + id;
                long cooldown = data.contains(petPath + ".cooldown-until") ? data.getLong(petPath + ".cooldown-until", 0L) : oldGlobalCooldown;
                long lastFed = data.getLong(petPath + ".last-fed", System.currentTimeMillis());
                player.add(new OwnedPet(
                        id.toLowerCase(Locale.ROOT),
                        data.getInt(petPath + ".level", 1),
                        data.getInt(petPath + ".exp", 0),
                        data.getString(petPath + ".name", id),
                        cooldown,
                        lastFed
                ));
            }
        }
        return player;
    }

    private void migrateLegacyStorage(UUID uuid) {
        String playerPath = "players." + uuid;
        String migratedPath = playerPath + ".storage-migrated";
        if (data.getBoolean(migratedPath, false)) return;

        ItemStack[] shared = new ItemStack[SHARED_STORAGE_SIZE];
        List<ItemStack> existingShared = readItems(playerPath + ".storage", SHARED_STORAGE_SIZE);
        for (int i = 0; i < existingShared.size() && i < shared.length; i++) {
            ItemStack item = existingShared.get(i);
            shared[i] = item == null ? null : item.clone();
        }

        List<ItemStack> overflow = readItems(playerPath + ".storage-overflow", Integer.MAX_VALUE);
        ConfigurationSection owned = data.getConfigurationSection(playerPath + ".owned");
        if (owned != null) {
            for (String petId : owned.getKeys(false)) {
                String legacyPath = playerPath + ".owned." + petId + ".storage";
                for (ItemStack item : readItems(legacyPath, Integer.MAX_VALUE)) {
                    ItemStack remaining = addToShared(shared, item);
                    if (remaining != null && !remaining.getType().isAir() && remaining.getAmount() > 0) {
                        overflow.add(remaining);
                    }
                }
                data.set(legacyPath, null);
            }
        }

        data.set(playerPath + ".storage", normalizeContents(shared));
        data.set(playerPath + ".storage-overflow", overflow.isEmpty() ? null : overflow);
        data.set(migratedPath, true);
        saveFile();
    }

    private List<ItemStack> readItems(String path, int limit) {
        List<?> raw = data.getList(path, new ArrayList<>());
        List<ItemStack> items = new ArrayList<>();
        int count = 0;
        for (Object object : raw) {
            if (count >= limit) break;
            items.add(object instanceof ItemStack item ? item.clone() : null);
            count++;
        }
        return items;
    }

    private List<ItemStack> normalizeContents(ItemStack[] contents) {
        List<ItemStack> items = new ArrayList<>(SHARED_STORAGE_SIZE);
        for (int i = 0; i < SHARED_STORAGE_SIZE; i++) {
            ItemStack item = i < contents.length ? contents[i] : null;
            items.add(item == null || item.getType().isAir() || item.getAmount() <= 0 ? null : item.clone());
        }
        return items;
    }

    private ItemStack addToShared(ItemStack[] shared, ItemStack source) {
        if (source == null || source.getType().isAir() || source.getAmount() <= 0) return null;
        ItemStack remaining = source.clone();

        for (int i = 0; i < shared.length && remaining.getAmount() > 0; i++) {
            ItemStack current = shared[i];
            if (current == null || !current.isSimilar(remaining)) continue;
            int capacity = current.getMaxStackSize() - current.getAmount();
            if (capacity <= 0) continue;
            int moved = Math.min(capacity, remaining.getAmount());
            current.setAmount(current.getAmount() + moved);
            remaining.setAmount(remaining.getAmount() - moved);
        }

        for (int i = 0; i < shared.length && remaining.getAmount() > 0; i++) {
            if (shared[i] != null && !shared[i].getType().isAir()) continue;
            int moved = Math.min(remaining.getMaxStackSize(), remaining.getAmount());
            ItemStack placed = remaining.clone();
            placed.setAmount(moved);
            shared[i] = placed;
            remaining.setAmount(remaining.getAmount() - moved);
        }

        return remaining.getAmount() <= 0 ? null : remaining;
    }

    private void saveFile() { try { data.save(file); } catch (IOException exception) { plugin.getLogger().warning("Gagal menyimpan pets.yml"); } }
}
