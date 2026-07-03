package id.velioragardens.veliorasuite.module.security;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.module.security.model.BanSource;
import id.velioragardens.veliorasuite.module.security.model.VelioraBanRecord;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Manager untuk ban yang berasal dari VelioraSuite
 * Hanya mencatat ban internal, bukan ban dari plugin lain
 */
public final class VelioraBanManager {
    
    private final VelioraSuite plugin;
    private final File file;
    private final Map<String, VelioraBanRecord> banRecords = new HashMap<>();
    private final List<String> banHistory = new ArrayList<>();
    
    public VelioraBanManager(VelioraSuite plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data/veliora-bans.yml");
    }
    
    /**
     * Load ban records dari storage
     */
    public void load() {
        banRecords.clear();
        banHistory.clear();
        
        if (!file.exists()) {
            save();
            return;
        }
        
        try {
            FileConfiguration data = YamlConfiguration.loadConfiguration(file);
            
            // Load ban records
            for (String key : data.getKeys(false)) {
                if (!key.startsWith("bans.")) continue;
                String playerName = key.substring(5);
                String reason = data.getString(key + ".reason", "");
                String source = data.getString(key + ".source", "EXTERNAL");
                long timestamp = data.getLong(key + ".timestamp", 0L);
                boolean permanent = data.getBoolean(key + ".permanent", true);
                long expiresAt = data.getLong(key + ".expires-at", 0L);
                
                try {
                    BanSource banSource = BanSource.valueOf(source.toUpperCase(Locale.ROOT));
                    VelioraBanRecord record = new VelioraBanRecord(
                        playerName, reason, banSource, timestamp, permanent, expiresAt
                    );
                    if (record.isValid()) {
                        banRecords.put(playerName.toLowerCase(Locale.ROOT), record);
                    }
                } catch (IllegalArgumentException ignored) {
                    // Skip invalid source
                }
            }
            
            // Load history
            List<String> history = data.getStringList("history");
            banHistory.addAll(history);
            while (banHistory.size() > 100) banHistory.remove(0);
            
        } catch (Exception e) {
            plugin.getLogger().warning("VelioraBanManager gagal load: " + e.getMessage());
        }
    }
    
    /**
     * Save ban records ke storage
     */
    public void save() {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            
            FileConfiguration data = new YamlConfiguration();
            
            for (Map.Entry<String, VelioraBanRecord> entry : banRecords.entrySet()) {
                VelioraBanRecord record = entry.getValue();
                String path = "bans." + entry.getKey();
                data.set(path + ".reason", record.reason());
                data.set(path + ".source", record.source().name());
                data.set(path + ".timestamp", record.timestamp());
                data.set(path + ".permanent", record.isPermanent());
                data.set(path + ".expires-at", record.expiresAt());
            }
            
            data.set("history", banHistory);
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("VelioraBanManager gagal save: " + e.getMessage());
        }
    }
    
    /**
     * Ban player dengan metadata internal
     */
    public void banPlayer(String playerName, String reason, BanSource source) {
        if (source == BanSource.EXTERNAL || source == BanSource.MANUAL_OWNER) {
            // Jangan track ban non-internal
            return;
        }
        
        long now = System.currentTimeMillis();
        String key = playerName.toLowerCase(Locale.ROOT);
        VelioraBanRecord record = new VelioraBanRecord(
            playerName, reason, source, now, true, 0L
        );
        
        banRecords.put(key, record);
        
        // Jalankan ban command
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), 
            "ban " + playerName + " " + reason);
        
        // Catat di history
        String historyEntry = "[" + formatTime(now) + "] " + source.name() + ": " + 
                            playerName + " - " + reason;
        banHistory.add(historyEntry);
        while (banHistory.size() > 100) banHistory.remove(0);
        
        save();
        plugin.getLogger().info("VelioraBan recorded: " + playerName + " (" + source.name() + ")");
    }
    
    /**
     * Ban IP dengan metadata internal
     */
    public void banIp(String ipAddress, String reason, BanSource source) {
        if (source == BanSource.EXTERNAL || source == BanSource.MANUAL_OWNER) {
            // Jangan track ban non-internal
            return;
        }
        
        long now = System.currentTimeMillis();
        
        // Jalankan ban-ip command
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), 
            "ban-ip " + ipAddress + " " + reason);
        
        // Catat di history
        String historyEntry = "[" + formatTime(now) + "] " + source.name() + 
                            " IP: " + ipAddress + " - " + reason;
        banHistory.add(historyEntry);
        while (banHistory.size() > 100) banHistory.remove(0);
        
        save();
        plugin.getLogger().info("VelioraBan IP recorded: " + ipAddress + " (" + source.name() + ")");
    }
    
    /**
     * Unban player (admin request)
     */
    public void unbanPlayer(String playerName) {
        String key = playerName.toLowerCase(Locale.ROOT);
        banRecords.remove(key);
        
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), 
            "pardon " + playerName);
        
        long now = System.currentTimeMillis();
        String historyEntry = "[" + formatTime(now) + "] MANUAL_UNBAN: " + playerName;
        banHistory.add(historyEntry);
        
        save();
        plugin.getLogger().info("VelioraBan unban recorded: " + playerName);
    }
    
    /**
     * Cek apakah player pernah di-ban oleh VelioraSuite
     */
    public boolean isBannedByVeliora(String playerName) {
        VelioraBanRecord record = banRecords.get(playerName.toLowerCase(Locale.ROOT));
        return record != null && record.isValid();
    }
    
    /**
     * Dapatkan ban record jika ada
     */
    public VelioraBanRecord getBanRecord(String playerName) {
        return banRecords.get(playerName.toLowerCase(Locale.ROOT));
    }
    
    /**
     * Dapatkan history ban untuk analytics
     */
    public List<String> getBanHistory() {
        return new ArrayList<>(banHistory);
    }
    
    private String formatTime(long millis) {
        return new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss")
            .format(new java.util.Date(millis));
    }
}
