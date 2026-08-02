package id.velioragardens.veliorasuite.module.security;

import id.velioragardens.veliorasuite.VelioraSuite;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Tracks the owner of every player-placed spawner so the limit works regardless of its item source. */
public final class SpawnerGuardManager {

    private final VelioraSuite plugin;
    private final SecurityConfigManager config;
    private final File file;
    private final Map<String, UUID> ownerByLocation = new LinkedHashMap<>();
    private final Map<UUID, Integer> countByOwner = new HashMap<>();
    private final Map<UUID, Long> alertCooldowns = new HashMap<>();

    public SpawnerGuardManager(VelioraSuite plugin, SecurityConfigManager config) {
        this.plugin = plugin;
        this.config = config;
        this.file = new File(plugin.getDataFolder(), "data/spawner-guard.yml");
    }

    public void load() {
        ownerByLocation.clear();
        countByOwner.clear();
        if (!file.exists()) return;

        ConfigurationSection records = YamlConfiguration.loadConfiguration(file).getConfigurationSection("records");
        if (records == null) return;
        for (String key : records.getKeys(false)) {
            String raw = records.getString(key, "");
            try {
                UUID owner = UUID.fromString(raw);
                ownerByLocation.put(key, owner);
                countByOwner.merge(owner, 1, Integer::sum);
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Mengabaikan data SpawnerGuard rusak: " + key);
            }
        }
    }

    public boolean handlePlace(Player player, Block block, ItemStack item) {
        if (!config.isSpawnerGuardEnabled() || player == null || block == null || block.getType() != Material.SPAWNER) return true;
        UUID owner = player.getUniqueId();
        int owned = countByOwner.getOrDefault(owner, 0);
        int limit = config.getSpawnerLimitPerPlayer();
        if (owned < limit) {
            ownerByLocation.put(locationKey(block), owner);
            countByOwner.put(owner, owned + 1);
            save();
            return true;
        }

        // The event is cancelled by the listener after this method returns. The location consequently stays air.
        consumeBlockedSpawner(player, item);
        player.sendMessage(color(config.message("spawner-limit-reached", "%prefix% &cKamu hanya boleh memiliki &f%limit% spawner &csaja. Spawner tambahan dihapus.")
                .replace("%limit%", String.valueOf(limit))));
        alertAdmins(player, block, owned + 1);
        return false;
    }

    public void handleBreak(Block block) {
        if (block == null || block.getType() != Material.SPAWNER) return;
        UUID owner = ownerByLocation.remove(locationKey(block));
        if (owner == null) return;
        countByOwner.computeIfPresent(owner, (ignored, count) -> count <= 1 ? null : count - 1);
        save();
    }

    public void rollbackPlace(Player player, Block block) {
        if (player == null || block == null) return;
        String key = locationKey(block);
        if (!player.getUniqueId().equals(ownerByLocation.get(key))) return;
        ownerByLocation.remove(key);
        countByOwner.computeIfPresent(player.getUniqueId(), (ignored, count) -> count <= 1 ? null : count - 1);
        save();
    }

    private void consumeBlockedSpawner(Player player, ItemStack eventItem) {
        if (!config.isSpawnerGuardConsumeBlockedItem() || eventItem == null || eventItem.getType() != Material.SPAWNER) return;
        ItemStack oneSpawner = eventItem.clone();
        oneSpawner.setAmount(1);
        player.getInventory().removeItem(oneSpawner);
        Bukkit.getScheduler().runTask(plugin, player::updateInventory);
    }

    private void alertAdmins(Player player, Block block, int attempt) {
        long now = System.currentTimeMillis();
        long cooldown = config.getSpawnerGuardAlertCooldownSeconds() * 1000L;
        if (now - alertCooldowns.getOrDefault(player.getUniqueId(), 0L) < cooldown) return;
        alertCooldowns.put(player.getUniqueId(), now);

        String message = color(config.message("spawner-limit-alert", "&8[&cVelioraSpawnerGuard&8] &f%player% &cmencoba memasang spawner ke-%attempt% &7(%world% %x% %y% %z%&7). &eDiblokir dan dihapus.")
                .replace("%player%", player.getName())
                .replace("%attempt%", String.valueOf(attempt))
                .replace("%world%", block.getWorld().getName())
                .replace("%x%", String.valueOf(block.getX()))
                .replace("%y%", String.valueOf(block.getY()))
                .replace("%z%", String.valueOf(block.getZ())));
        plugin.getLogger().warning(org.bukkit.ChatColor.stripColor(message));
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (config.hasAlerts(online) || config.hasAdmin(online)) online.sendMessage(message);
        }
    }

    private void save() {
        YamlConfiguration data = new YamlConfiguration();
        for (Map.Entry<String, UUID> entry : ownerByLocation.entrySet()) data.set("records." + entry.getKey(), entry.getValue().toString());
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                plugin.getLogger().warning("Tidak bisa membuat folder data SpawnerGuard.");
                return;
            }
            data.save(file);
        } catch (IOException exception) {
            plugin.getLogger().warning("Gagal menyimpan data SpawnerGuard: " + exception.getMessage());
        }
    }

    private String locationKey(Block block) {
        return block.getWorld().getUID() + ";" + block.getX() + ";" + block.getY() + ";" + block.getZ();
    }

    private String color(String text) { return config.color(text); }
}
