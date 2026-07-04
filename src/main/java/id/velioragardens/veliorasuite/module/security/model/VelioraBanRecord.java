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
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Manager untuk ban yang berasal dari VelioraSuite
 * Hanya mencatat ban internal, bukan ban dari plugin lain
 */
public final class VelioraBanManager {
    private static final Pattern PLAYER_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]{3,16}$");
    private static final int MAX_REASON_LENGTH = 180;
    
    private final VelioraSuite plugin;
    private final File file;
    private final Map<String, VelioraBanRecord> banRecords = new HashMap<>();
    private final Map<String, VelioraBanRecord> ipBanRecords = new HashMap<>();
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
        ipBanRecords.clear();
        banHistory.clear();
        
        if (!file.exists()) {
            save();
            return;
        }
        
        try {
            FileConfiguration data = YamlConfiguration.loadConfiguration(file);
            
            // Load ban records
            if (data.isConfigurationSection("bans")) {
                for (String playerName : data.getConfigurationSection("bans").getKeys(false)) {
                    String key = "bans." + playerName;
                    String reason = data.getString(key + ".reason", "");
                    String source = data.getString(key + ".source", "MANUAL");
                    long timestamp = data.getLong(key + ".timestamp", 0L);
                    boolean permanent = data.getBoolean(key + ".permanent", true);
                    long expiresAt = data.getLong(key + ".expires-at", 0L);

                    try {
                        BanSource banSource = parseStoredSource(source);
                        VelioraBanRecord record = new VelioraBanRecord(
                            playerName, reason, banSource, timestamp, permanent, expiresAt
                        );
                        if (record.isValid()) {
                            banRecords.put(playerName.toLowerCase(Locale.ROOT), record);
                        }
                    } catch (IllegalArgumentException ignored) {
                        // Missing or invalid legacy source is treated as manual and is never imported as AutoBan.
                    }
                }
            }

            if (data.isConfigurationSection("ip-bans")) {
                for (String encodedIp : data.getConfigurationSection("ip-bans").getKeys(false)) {
                    String key = "ip-bans." + encodedIp;
                    String ipAddress = data.getString(key + ".ip", "");
                    String reason = data.getString(key + ".reason", "");
                    BanSource banSource = parseStoredSource(data.getString(key + ".source", "MANUAL"));
                    long timestamp = data.getLong(key + ".timestamp", 0L);
                    boolean permanent = data.getBoolean(key + ".permanent", true);
                    long expiresAt = data.getLong(key + ".expires-at", 0L);
                    VelioraBanRecord record = new VelioraBanRecord(ipAddress, reason, banSource, timestamp, permanent, expiresAt);
                    if (record.isValid() && banSource == BanSource.AUTO_IP) {
                        ipBanRecords.put(encodedIp, record);
                    }
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

            for (Map.Entry<String, VelioraBanRecord> entry : ipBanRecords.entrySet()) {
                VelioraBanRecord record = entry.getValue();
                String path = "ip-bans." + entry.getKey();
                data.set(path + ".ip", record.playerName());
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
        if (source != BanSource.AUTO_ALT) {
            // Jangan track ban non-internal
            return;
        }
        String safePlayerName = normalizePlayerName(playerName);
        if (safePlayerName == null) {
            plugin.getLogger().warning("VelioraBan membatalkan ban: nama player tidak valid.");
            return;
        }
        String safeReason = sanitizeReason(reason);
        
        long now = System.currentTimeMillis();
        String key = safePlayerName.toLowerCase(Locale.ROOT);
        VelioraBanRecord record = new VelioraBanRecord(
            safePlayerName, safeReason, source, now, true, 0L
        );
        
        banRecords.put(key, record);
        
        // Jalankan ban command
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), 
            "ban " + safePlayerName + " " + safeReason);
        
        // Catat di history
        String historyEntry = "[" + formatTime(now) + "] " + source.name() + ": " + 
                            safePlayerName + " - " + safeReason;
        banHistory.add(historyEntry);
        while (banHistory.size() > 100) banHistory.remove(0);
        
        save();
        plugin.getLogger().info("VelioraBan recorded: " + safePlayerName + " (" + source.name() + ")");
    }
    
    /**
     * Ban IP dengan metadata internal
     */
    public void banIp(String ipAddress, String reason, BanSource source) {
        if (source != BanSource.AUTO_IP) {
            // Jangan track ban non-internal
            return;
        }
        String safeIpAddress = normalizeIp(ipAddress);
        if (safeIpAddress == null) {
            plugin.getLogger().warning("VelioraBan membatalkan ban-ip: alamat IP tidak valid.");
            return;
        }
        String safeReason = sanitizeReason(reason);
        
        long now = System.currentTimeMillis();
        VelioraBanRecord record = new VelioraBanRecord(
            safeIpAddress, safeReason, source, now, true, 0L
        );
        ipBanRecords.put(ipKey(safeIpAddress), record);
        
        // Jalankan ban-ip command
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), 
            "ban-ip " + safeIpAddress + " " + safeReason);
        
        // Catat di history
        String historyEntry = "[" + formatTime(now) + "] " + source.name() + 
                            " IP: " + safeIpAddress + " - " + safeReason;
        banHistory.add(historyEntry);
        while (banHistory.size() > 100) banHistory.remove(0);
        
        save();
        plugin.getLogger().info("VelioraBan IP recorded: " + safeIpAddress + " (" + source.name() + ")");
    }
    
    /**
     * Unban player (admin request)
     */
    public void unbanPlayer(String playerName) {
        String safePlayerName = normalizePlayerName(playerName);
        if (safePlayerName == null) {
            plugin.getLogger().warning("VelioraBan membatalkan unban: nama player tidak valid.");
            return;
        }
        String key = safePlayerName.toLowerCase(Locale.ROOT);
        banRecords.remove(key);
        
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), 
            "pardon " + safePlayerName);
        
        long now = System.currentTimeMillis();
        String historyEntry = "[" + formatTime(now) + "] MANUAL_UNBAN: " + safePlayerName;
        banHistory.add(historyEntry);
        
        save();
        plugin.getLogger().info("VelioraBan unban recorded: " + safePlayerName);
    }

    public void unbanPlayer(String playerName, String ipAddress) {
        unbanPlayer(playerName);
        unbanIp(ipAddress);
    }

    public void unbanIp(String ipAddress) {
        String safeIpAddress = normalizeIp(ipAddress);
        if (safeIpAddress == null) return;
        ipBanRecords.remove(ipKey(safeIpAddress));
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "pardon-ip " + safeIpAddress);
        save();
    }

    public void purgePlayer(String playerName, String ipAddress) {
        String safePlayerName = normalizePlayerName(playerName);
        if (safePlayerName != null) banRecords.remove(safePlayerName.toLowerCase(Locale.ROOT));
        String safeIpAddress = normalizeIp(ipAddress);
        if (safeIpAddress != null) ipBanRecords.remove(ipKey(safeIpAddress));
        save();
    }
    
    /**
     * Cek apakah player pernah di-ban oleh VelioraSuite
     */
    public boolean isBannedByVeliora(String playerName) {
        if (playerName == null) return false;
        VelioraBanRecord record = banRecords.get(playerName.toLowerCase(Locale.ROOT));
        return record != null && record.isValid();
    }
    
    /**
     * Dapatkan ban record jika ada
     */
    public VelioraBanRecord getBanRecord(String playerName) {
        if (playerName == null) return null;
        return banRecords.get(playerName.toLowerCase(Locale.ROOT));
    }

    public VelioraBanRecord getIpBanRecord(String ipAddress) {
        String safeIpAddress = normalizeIp(ipAddress);
        return safeIpAddress == null ? null : ipBanRecords.get(ipKey(safeIpAddress));
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

    private String normalizePlayerName(String playerName) {
        if (playerName == null) return null;
        String trimmed = playerName.trim();
        return PLAYER_NAME_PATTERN.matcher(trimmed).matches() ? trimmed : null;
    }

    private String normalizeIp(String ipAddress) {
        if (ipAddress == null) return null;
        String trimmed = ipAddress.trim();
        if (trimmed.isBlank() || trimmed.length() > 64 || trimmed.chars().anyMatch(Character::isWhitespace)) return null;
        try {
            InetAddress.getByName(trimmed);
            return trimmed;
        } catch (Exception ignored) {
            return null;
        }
    }

    private BanSource parseStoredSource(String source) {
        if (source == null || source.isBlank()) return BanSource.MANUAL;
        try {
            return BanSource.valueOf(source.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return BanSource.UNKNOWN;
        }
    }

    private String ipKey(String ipAddress) {
        return ipAddress.replace('.', '_').replace(':', '_');
    }

    private String sanitizeReason(String reason) {
        String sanitized = reason == null || reason.isBlank() ? "VelioraSuite security action" : reason;
        sanitized = sanitized.replace('\r', ' ').replace('\n', ' ').replace('\t', ' ').trim();
        if (sanitized.length() > MAX_REASON_LENGTH) sanitized = sanitized.substring(0, MAX_REASON_LENGTH);
        return sanitized.isBlank() ? "VelioraSuite security action" : sanitized;
    }
}
