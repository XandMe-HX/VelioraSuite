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
        data.set(path + ".active-pet", player.activePet());
        data.set(path + ".last-pet", player.lastPet());
        data.set(path + ".cooldown-until", player.cooldownUntil());
        data.set(path + ".owned", null);
        for (OwnedPet pet : player.owned().values()) {
            String petPath = path + ".owned." + pet.id().toLowerCase(Locale.ROOT);
            data.set(petPath + ".level", pet.level());
            data.set(petPath + ".exp", pet.exp());
            data.set(petPath + ".name", pet.name());
        }
        saveFile();
    }

    public void saveAll() {
        for (UUID uuid : new ArrayList<>(cache.keySet())) save(uuid);
    }

    public List<ItemStack> loadStorage(UUID uuid, String petId) {
        List<?> raw = data.getList("players." + uuid + ".owned." + petId.toLowerCase(Locale.ROOT) + ".storage", new ArrayList<>());
        List<ItemStack> items = new ArrayList<>();
        for (Object object : raw) if (object instanceof ItemStack item) items.add(item);
        return items;
    }

    public void saveStorage(UUID uuid, String petId, ItemStack[] contents) {
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack item : contents) items.add(item);
        data.set("players." + uuid + ".owned." + petId.toLowerCase(Locale.ROOT) + ".storage", items);
        saveFile();
    }

    private PlayerPetData loadPlayer(UUID uuid) {
        PlayerPetData player = new PlayerPetData();
        String path = "players." + uuid;
        player.activePet(data.getString(path + ".active-pet", null));
        player.lastPet(data.getString(path + ".last-pet", null));
        player.cooldownUntil(data.getLong(path + ".cooldown-until", 0L));
        ConfigurationSection owned = data.getConfigurationSection(path + ".owned");
        if (owned != null) {
            for (String id : owned.getKeys(false)) {
                String petPath = path + ".owned." + id;
                player.add(new OwnedPet(id.toLowerCase(Locale.ROOT), data.getInt(petPath + ".level", 1), data.getInt(petPath + ".exp", 0), data.getString(petPath + ".name", id)));
            }
        }
        return player;
    }

    private void saveFile() { try { data.save(file); } catch (IOException exception) { plugin.getLogger().warning("Gagal menyimpan pets.yml"); } }
}
