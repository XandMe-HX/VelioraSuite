package id.velioragardens.veliorasuite.module.security;

import id.velioragardens.veliorasuite.VelioraSuite;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Hard inventory guard for high-risk economy items. Excess items are quarantined,
 * not destroyed, so an owner can recover legitimate items from the data file.
 */
public final class AntiDupeManager {

    private final VelioraSuite plugin;
    private final SecurityConfigManager config;
    private final File quarantineFile;
    private final Set<UUID> scheduled = new HashSet<>();
    private final Map<UUID, Long> lastScanAt = new HashMap<>();

    public AntiDupeManager(VelioraSuite plugin, SecurityConfigManager config) {
        this.plugin = plugin;
        this.config = config;
        this.quarantineFile = new File(plugin.getDataFolder(), "data/antidupe-quarantine.yml");
    }

    public void scheduleScan(Player player, long delayTicks) {
        if (!config.isAntiDupeEnabled() || player == null || config.hasBypass(player)) return;
        UUID uuid = player.getUniqueId();
        long cooldownMillis = config.getAntiDupeScanCooldownTicks() * 50L;
        if (System.currentTimeMillis() - lastScanAt.getOrDefault(uuid, 0L) < cooldownMillis) return;
        if (!scheduled.add(uuid)) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            scheduled.remove(uuid);
            if (!player.isOnline() || config.hasBypass(player)) return;
            lastScanAt.put(uuid, System.currentTimeMillis());
            scan(player);
        }, Math.max(1L, delayTicks));
    }

    /**
     * Moves only the requested suspicious amounts from the player's inventory into
     * the recoverable quarantine file. Used by OreWatch after a staged warning.
     */
    public String quarantineSuspiciousItems(Player player, Map<Material, Integer> requested, String reason) {
        if (player == null || requested == null || requested.isEmpty()) return "";
        ItemStack[] contents = player.getInventory().getStorageContents();
        List<ItemStack> quarantined = new ArrayList<>();
        Map<Material, Integer> removedCounts = new HashMap<>();
        for (Map.Entry<Material, Integer> entry : requested.entrySet()) {
            int amount = Math.max(0, entry.getValue());
            if (amount <= 0) continue;
            int removed = removeExcess(contents, entry.getKey(), amount, quarantined);
            if (removed > 0) removedCounts.put(entry.getKey(), removed);
        }
        if (quarantined.isEmpty()) return "";
        player.getInventory().setStorageContents(contents);
        player.updateInventory();
        String quarantineId = saveQuarantine(player, quarantined, removedCounts, reason);
        plugin.getLogger().warning("VelioraOreWatch quarantined " + summarize(removedCounts)
                + " from " + player.getName() + " id=" + quarantineId + " reason=" + reason);
        return quarantineId;
    }

    private void scan(Player player) {
        Map<Material, Integer> limits = config.getAntiDupeInventoryLimits();
        if (limits.isEmpty()) return;
        ItemStack[] contents = player.getInventory().getStorageContents();
        List<ItemStack> quarantined = new ArrayList<>();
        Map<Material, Integer> removedCounts = new HashMap<>();

        for (Map.Entry<Material, Integer> entry : limits.entrySet()) {
            Material material = entry.getKey();
            int total = count(contents, material);
            int excess = total - entry.getValue();
            if (excess <= 0) continue;
            int removed = removeExcess(contents, material, excess, quarantined);
            if (removed > 0) removedCounts.put(material, removed);
        }

        if (quarantined.isEmpty()) return;
        player.getInventory().setStorageContents(contents);
        player.updateInventory();
        String quarantineId = saveQuarantine(player, quarantined, removedCounts, "ANTI_DUPE_LIMIT");
        String summary = summarize(removedCounts);
        plugin.getLogger().warning("VelioraAntiDupe quarantined " + summary + " from " + player.getName() + " id=" + quarantineId);
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!config.hasAlerts(online) && !config.hasAdmin(online)) continue;
            online.sendMessage(config.color("&8[&cVelioraAntiDupe&8] &f" + player.getName()
                    + " &cmemiliki item melewati batas: &f" + summary + "&c. Karantina: &f" + quarantineId));
        }
        player.sendMessage(config.color("&8[&cVelioraAntiDupe&8] &cItem berlebih diamankan untuk pengecekan owner. &7ID: &f" + quarantineId));
        if (config.isAntiDupeKickEnabled()) {
            player.kickPlayer(config.color("&cInventory terdeteksi melewati batas anti-dupe.\n&7Item berlebih diamankan, bukan dihapus.\n&7Hubungi owner dengan ID: &f" + quarantineId));
        }
    }

    private int count(ItemStack[] contents, Material material) {
        int total = 0;
        for (ItemStack item : contents) if (item != null && item.getType() == material) total += item.getAmount();
        return total;
    }

    private int removeExcess(ItemStack[] contents, Material material, int excess, List<ItemStack> quarantined) {
        int removed = 0;
        for (int slot = contents.length - 1; slot >= 0 && excess > 0; slot--) {
            ItemStack item = contents[slot];
            if (item == null || item.getType() != material) continue;
            int amount = Math.min(excess, item.getAmount());
            ItemStack saved = item.clone();
            saved.setAmount(amount);
            quarantined.add(saved);
            removed += amount;
            excess -= amount;
            if (amount == item.getAmount()) contents[slot] = null;
            else item.setAmount(item.getAmount() - amount);
        }
        return removed;
    }

    private String saveQuarantine(Player player, List<ItemStack> items, Map<Material, Integer> counts, String reason) {
        File parent = quarantineFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            plugin.getLogger().warning("VelioraAntiDupe gagal membuat folder data karantina.");
        }
        YamlConfiguration data = quarantineFile.exists()
                ? YamlConfiguration.loadConfiguration(quarantineFile) : new YamlConfiguration();
        long now = System.currentTimeMillis();
        String id = now + "-" + player.getUniqueId().toString().substring(0, 8);
        String path = "records." + id;
        data.set(path + ".player", player.getName());
        data.set(path + ".uuid", player.getUniqueId().toString());
        data.set(path + ".timestamp", now);
        data.set(path + ".reason", reason);
        data.set(path + ".counts", counts.entrySet().stream().collect(
                java.util.stream.Collectors.toMap(entry -> entry.getKey().name(), Map.Entry::getValue)));
        data.set(path + ".items", items);
        try {
            data.save(quarantineFile);
        } catch (IOException exception) {
            plugin.getLogger().severe("VelioraAntiDupe gagal menyimpan karantina " + id + ": " + exception.getMessage());
        }
        return id;
    }

    private String summarize(Map<Material, Integer> counts) {
        List<String> parts = new ArrayList<>();
        for (Map.Entry<Material, Integer> entry : counts.entrySet()) parts.add(entry.getKey().name() + " x" + entry.getValue());
        return String.join(", ", parts);
    }
}
