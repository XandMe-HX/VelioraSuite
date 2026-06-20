package id.velioragardens.veliorasuite.module.login;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.config.ConfigFile;
import id.velioragardens.veliorasuite.util.ColorUtil;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class LoginManager {

    private final VelioraSuite plugin;
    private final ConfigFile configFile;
    private final Map<UUID, Boolean> authenticated = new HashMap<>();
    private final Map<UUID, Integer> attempts = new HashMap<>();
    private File dataFile;
    private FileConfiguration data;
    private final SecureRandom random = new SecureRandom();

    public LoginManager(VelioraSuite plugin, ConfigFile configFile) {
        this.plugin = plugin;
        this.configFile = configFile;
    }

    public void load() {
        File folder = new File(plugin.getDataFolder(), "data");
        if (!folder.exists()) folder.mkdirs();
        dataFile = new File(folder, "login-users.yml");
        if (!dataFile.exists()) {
            try { dataFile.createNewFile(); } catch (IOException exception) { plugin.getLogger().warning(exception.getMessage()); }
        }
        data = YamlConfiguration.loadConfiguration(dataFile);
        if (!data.isConfigurationSection("users")) data.createSection("users");
        save();
    }

    public void save() {
        try { if (data != null && dataFile != null) data.save(dataFile); } catch (IOException exception) { plugin.getLogger().warning("Gagal menyimpan login-users.yml: " + exception.getMessage()); }
    }

    public void reload() {
        configFile.reload();
        load();
    }

    public boolean isRegistered(String name) {
        return data.isConfigurationSection("users." + key(name));
    }

    public boolean isLoggedIn(Player player) {
        return player.hasPermission("veliorasuite.login.bypass") || authenticated.getOrDefault(player.getUniqueId(), false);
    }

    public void handleJoin(Player player) {
        if (!configFile.get().getBoolean("enabled", true) || player.hasPermission("veliorasuite.login.bypass")) {
            authenticated.put(player.getUniqueId(), true);
            return;
        }
        authenticated.put(player.getUniqueId(), false);
        attempts.put(player.getUniqueId(), 0);
        if (isRegistered(player.getName())) {
            message(player, "need-login");
        } else {
            message(player, "need-register");
        }
    }

    public boolean register(Player player, String password, String confirm) {
        if (isRegistered(player.getName())) { message(player, "already-registered"); return false; }
        if (!password.equals(confirm)) { message(player, "password-not-match"); return false; }
        if (!validPassword(player, password)) return false;
        String salt = createSalt();
        String path = "users." + key(player.getName());
        data.set(path + ".name", player.getName());
        data.set(path + ".uuid", player.getUniqueId().toString());
        data.set(path + ".salt", salt);
        data.set(path + ".password", hash(salt, password));
        data.set(path + ".last-ip", ip(player));
        data.set(path + ".registered-at", System.currentTimeMillis());
        data.set(path + ".last-login", System.currentTimeMillis());
        save();
        authenticated.put(player.getUniqueId(), true);
        message(player, "register-success");
        return true;
    }

    public boolean login(Player player, String password) {
        if (!isRegistered(player.getName())) { message(player, "need-register"); return false; }
        if (isLoggedIn(player)) { message(player, "already-logged-in"); return false; }
        String path = "users." + key(player.getName());
        String salt = data.getString(path + ".salt", "");
        String stored = data.getString(path + ".password", "");
        if (!stored.equals(hash(salt, password))) {
            int current = attempts.getOrDefault(player.getUniqueId(), 0) + 1;
            attempts.put(player.getUniqueId(), current);
            int max = configFile.get().getInt("login.max-failed-attempts", 5);
            player.sendMessage(ColorUtil.color(msg("wrong-password").replace("%attempt%", String.valueOf(current)).replace("%max_attempts%", String.valueOf(max))));
            if (current >= max && configFile.get().getBoolean("login.kick-on-max-failed", true)) {
                player.kickPlayer(ColorUtil.color(msg("too-many-attempts")));
            }
            return false;
        }
        authenticated.put(player.getUniqueId(), true);
        attempts.put(player.getUniqueId(), 0);
        data.set(path + ".last-ip", ip(player));
        data.set(path + ".last-login", System.currentTimeMillis());
        save();
        message(player, "login-success");
        return true;
    }

    public boolean changePassword(Player player, String oldPassword, String newPassword) {
        if (!isRegistered(player.getName())) { message(player, "need-register"); return false; }
        String path = "users." + key(player.getName());
        String salt = data.getString(path + ".salt", "");
        String stored = data.getString(path + ".password", "");
        if (!stored.equals(hash(salt, oldPassword))) { message(player, "wrong-old-password"); return false; }
        if (!validPassword(player, newPassword)) return false;
        String newSalt = createSalt();
        data.set(path + ".salt", newSalt);
        data.set(path + ".password", hash(newSalt, newPassword));
        save();
        message(player, "changepass-success");
        return true;
    }

    public boolean setPassword(String playerName, String newPassword) {
        String path = "users." + key(playerName);
        if (!data.isConfigurationSection(path)) data.createSection(path);
        String salt = createSalt();
        data.set(path + ".name", playerName);
        data.set(path + ".salt", salt);
        data.set(path + ".password", hash(salt, newPassword));
        data.set(path + ".admin-reset-at", System.currentTimeMillis());
        save();
        return true;
    }

    public boolean unregister(String playerName) {
        if (!isRegistered(playerName)) return false;
        data.set("users." + key(playerName), null);
        save();
        return true;
    }

    public void logout(Player player) {
        authenticated.put(player.getUniqueId(), false);
        message(player, "logout-success");
    }

    private boolean validPassword(Player player, String password) {
        int min = configFile.get().getInt("register.min-password-length", 6);
        int max = configFile.get().getInt("register.max-password-length", 32);
        if (password.length() < min || password.length() > max) {
            player.sendMessage(ColorUtil.color(msg("invalid-password-length").replace("%min%", String.valueOf(min)).replace("%max%", String.valueOf(max))));
            return false;
        }
        if (configFile.get().getBoolean("register.block-player-name-password", true) && password.equalsIgnoreCase(player.getName())) {
            message(player, "password-same-name");
            return false;
        }
        return true;
    }

    public boolean isAllowedBeforeLogin(String rawCommand) {
        String command = rawCommand.replaceFirst("^/", "").split(" ")[0].toLowerCase(Locale.ROOT);
        return configFile.get().getStringList("protection.allowed-commands-before-login").contains(command);
    }

    public ConfigFile getConfigFile() { return configFile; }
    public String msg(String key) { return configFile.get().getString("messages." + key, "&cMessage not found: " + key).replace("%prefix%", configFile.get().getString("messages.prefix", "&8【&aVelioraLogin&8】")); }
    public void message(Player player, String key) { player.sendMessage(ColorUtil.color(msg(key))); }

    private String createSalt() {
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    private String hash(String salt, String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((salt + password).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception exception) {
            return "";
        }
    }

    private String key(String name) { return name.toLowerCase(Locale.ROOT); }
    private String ip(Player player) { return player.getAddress() == null ? "unknown" : player.getAddress().getAddress().getHostAddress(); }
}
