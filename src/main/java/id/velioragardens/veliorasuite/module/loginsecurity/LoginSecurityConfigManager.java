package id.velioragardens.veliorasuite.module.loginsecurity;

import id.velioragardens.veliorasuite.VelioraSuite;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class LoginSecurityConfigManager {

    private final VelioraSuite plugin;
    private FileConfiguration config;

    public LoginSecurityConfigManager(VelioraSuite plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveResourceIfNotExists("modules/loginsecurity.yml");
        File file = new File(plugin.getDataFolder(), "modules/loginsecurity.yml");
        this.config = YamlConfiguration.loadConfiguration(file);
    }

    public boolean isEnabled() { return getBoolean("settings.enabled", true); }
    public String getPrefix() { return getString("settings.prefix", "&8[&bVelioraLogin&8] "); }
    public boolean isRequireLogin() { return getBoolean("settings.require-login", true); }
    public int getMinPasswordLength() { return Math.max(1, getInt("settings.min-password-length", 5)); }
    public int getMaxPasswordLength() { return Math.max(getMinPasswordLength(), getInt("settings.max-password-length", 32)); }

    public String getHashAlgorithm() { return getString("settings.hashing.algorithm", "PBKDF2WithHmacSHA256"); }
    public int getHashIterations() { return Math.max(120000, getInt("settings.hashing.iterations", 120000)); }
    public int getHashKeyLength() { return Math.max(128, getInt("settings.hashing.key-length", 256)); }
    public int getSaltLength() { return Math.max(16, getInt("settings.hashing.salt-length", 16)); }

    public int getMaxLoginAttempts() { return Math.max(1, getInt("settings.max-login-attempts", 5)); }
    public int getLockSeconds() { return Math.max(1, getInt("settings.lock-seconds", 60)); }
    public int getAuthTimeoutSeconds() { return Math.max(0, getInt("settings.auth-timeout-seconds", 120)); }
    public boolean isBlockMovementBeforeLogin() { return getBoolean("settings.block-movement-before-login", true); }
    public double getAllowedMoveDistanceBeforeLogin() { return Math.max(0D, getDouble("settings.allowed-move-distance-before-login", 0.2D)); }
    public boolean isBlockChatBeforeLogin() { return getBoolean("settings.block-chat-before-login", true); }
    public boolean isBlockActionsBeforeLogin() { return getBoolean("settings.block-actions-before-login", true); }

    public List<String> getAllowedCommandsBeforeLogin() {
        List<String> commands = config == null ? List.of() : config.getStringList("settings.allowed-commands-before-login");
        return commands.isEmpty() ? List.of("/login", "/l", "/register", "/reg") : commands;
    }

    public List<String> getOwnerUuids() {
        return config == null ? List.of("isi-uuid-owner-di-sini") : config.getStringList("settings.owner-uuids");
    }

    public String getUsePermission() { return getString("permissions.use", "veliorasuite.loginsecurity.use"); }
    public String getAdminPermission() { return getString("permissions.admin", "veliorasuite.loginsecurity.admin"); }
    public String getOwnerPermission() { return getString("permissions.owner", "veliorasuite.loginsecurity.owner"); }
    public String getBypassPermission() { return getString("permissions.bypass", "veliorasuite.loginsecurity.bypass"); }

    public boolean isAllowedBeforeLogin(String commandLine) {
        if (commandLine == null || commandLine.isBlank()) return false;
        String command = commandLine.trim().split("\\s+")[0].toLowerCase(Locale.ROOT);
        if (!command.startsWith("/")) command = "/" + command;

        for (String allowed : getAllowedCommandsBeforeLogin()) {
            if (allowed == null || allowed.isBlank()) continue;
            String normalized = allowed.trim().toLowerCase(Locale.ROOT);
            if (!normalized.startsWith("/")) normalized = "/" + normalized;
            if (normalized.equals(command)) return true;
        }
        return false;
    }

    public boolean hasUsePermission(CommandSender sender) {
        return sender.hasPermission(getUsePermission()) || hasAdminPermission(sender);
    }

    public boolean hasAdminPermission(CommandSender sender) {
        return sender.hasPermission(getAdminPermission()) || sender.isOp();
    }

    public boolean hasBypassPermission(Player player) {
        return player.hasPermission(getBypassPermission()) || hasAdminPermission(player);
    }

    public boolean hasOwnerPermission(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            return true;
        }
        if (player.isOp() || player.hasPermission(getOwnerPermission()) || hasAdminPermission(player)) {
            return true;
        }
        UUID uuid = player.getUniqueId();
        for (String rawUuid : getOwnerUuids()) {
            if (rawUuid == null || rawUuid.isBlank() || rawUuid.equalsIgnoreCase("isi-uuid-owner-di-sini")) continue;
            try {
                if (UUID.fromString(rawUuid).equals(uuid)) return true;
            } catch (IllegalArgumentException ignored) {
                // Invalid UUID in config should not break commands.
            }
        }
        return false;
    }

    public String getMessage(String path, String fallback) {
        return getString("messages." + path, fallback).replace("%prefix%", getPrefix());
    }

    public List<String> getMessageList(String path, List<String> fallback) {
        List<String> list = config == null ? List.of() : config.getStringList("messages." + path);
        return list.isEmpty() ? fallback : list;
    }

    public String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }

    private String getString(String path, String fallback) {
        if (config == null || !config.contains(path)) return fallback;
        return config.getString(path, fallback);
    }

    private boolean getBoolean(String path, boolean fallback) {
        if (config == null || !config.contains(path)) return fallback;
        return config.getBoolean(path, fallback);
    }

    private int getInt(String path, int fallback) {
        if (config == null || !config.contains(path)) return fallback;
        return config.getInt(path, fallback);
    }

    private double getDouble(String path, double fallback) {
        if (config == null || !config.contains(path)) return fallback;
        return config.getDouble(path, fallback);
    }
}
