package id.velioragardens.veliorasuite.module.loginsecurity;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.module.loginsecurity.model.AuthPlayerData;
import id.velioragardens.veliorasuite.module.loginsecurity.model.AuthState;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.net.InetSocketAddress;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

public final class LoginSecurityManager {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final VelioraSuite plugin;
    private final LoginSecurityConfigManager configManager;
    private final LoginSecurityDataManager dataManager;
    private final LoginSecuritySessionManager sessionManager;
    private final LoginSecurityPasswordManager passwordManager;
    private final LoginSecurityProtectionManager protectionManager;

    public LoginSecurityManager(VelioraSuite plugin) {
        this.plugin = plugin;
        this.configManager = new LoginSecurityConfigManager(plugin);
        this.dataManager = new LoginSecurityDataManager(plugin);
        this.sessionManager = new LoginSecuritySessionManager();
        this.passwordManager = new LoginSecurityPasswordManager(configManager);
        this.protectionManager = new LoginSecurityProtectionManager(configManager);
    }

    public void load() {
        configManager.load();
        dataManager.load();
        plugin.getLogger().info("VelioraLoginSecurity loaded with " + dataManager.countRegistered() + " account(s).");
    }

    public void reload() {
        configManager.load();
    }

    public void shutdown() {
        LoginSecurityBlindnessManager.clearAll();
        sessionManager.clearAll();
        dataManager.shutdown();
    }

    public LoginSecurityConfigManager getConfigManager() { return configManager; }
    public LoginSecuritySessionManager getSessionManager() { return sessionManager; }
    public LoginSecurityDataManager getDataManager() { return dataManager; }

    public boolean isAuthenticated(Player player) {
        if (!configManager.isEnabled() || !configManager.isRequireLogin()) {
            return true;
        }
        return sessionManager.isAuthenticated(player);
    }

    public void handleJoin(Player player) {
        if (!configManager.isEnabled() || !configManager.isRequireLogin()) {
            sessionManager.setState(player, AuthState.AUTHENTICATED);
            finishAuthentication(player);
            return;
        }

        AuthPlayerData data = getPlayerData(player);
        sessionManager.setAuthLocation(player, player.getLocation());
        sessionManager.setState(player, data == null ? AuthState.WAITING_REGISTER : AuthState.WAITING_LOGIN);
        LoginSecurityBlindnessManager.apply(player, configManager);
        send(player, data == null ? "need-register" : "need-login", data == null
                ? "%prefix% &eSilakan daftar dengan &f/register <password> <password confirm>&e."
                : "%prefix% &eSilakan login dengan &f/login <password>&e.", Map.of());
        startTitleReminder(player, data == null);

        int timeout = configManager.getAuthTimeoutSeconds();
        if (timeout > 0) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline() && !isAuthenticated(player)) {
                    player.kickPlayer(configManager.color(configManager.getMessage("auth-timeout-kick", "&cKamu terlalu lama tidak login/register.")));
                }
            }, timeout * 20L);
        }
    }

    private void startTitleReminder(Player player, boolean register) {
        new BukkitRunnable() {
            @Override public void run() {
                if (!player.isOnline() || isAuthenticated(player)) { cancel(); return; }
                String title = register ? "&eDAFTARKAN AKUN" : "&aLOGIN AKUN";
                String subtitle = register ? "&f/register <password> <ulangi password>"
                        : "&f/login <password> &8| &7Bantuan: TikTok Veliora Gardens Official";
                player.sendTitle(configManager.color(title), configManager.color(subtitle), 5, 50, 5);
            }
        }.runTaskTimer(plugin, 1L, 40L);
    }

    public void handleQuit(Player player) {
        LoginSecurityBlindnessManager.remove(player);
        sessionManager.clear(player);
    }

    public void register(Player player, String password, String confirm) {
        if (!checkUse(player)) return;
        if (dataManager.getByUuid(player.getUniqueId()) != null || dataManager.getByName(player.getName()) != null) {
            send(player, "already-registered", "%prefix% &cAkun kamu sudah terdaftar. Gunakan &f/login <password>&c.", Map.of());
            return;
        }
        if (!password.equals(confirm)) {
            send(player, "password-not-match", "%prefix% &cPassword konfirmasi tidak sama.", Map.of());
            return;
        }
        if (!validatePasswordAndSend(player, password)) return;

        try {
            LoginSecurityPasswordManager.PasswordHash passwordHash = passwordManager.createHash(password);
            AuthPlayerData data = new AuthPlayerData(player.getUniqueId(), player.getName(), passwordHash.hash(), passwordHash.salt(), now(), now(), getIpHash(player), 0, 0L);
            dataManager.savePlayer(data);
            sessionManager.setState(player, AuthState.AUTHENTICATED);
            finishAuthentication(player);
            send(player, "register-success", "%prefix% &aRegistrasi berhasil. Selamat bermain!", Map.of());
        } catch (Exception exception) {
            plugin.getLogger().warning("VelioraLoginSecurity: gagal membuat hash password untuk register.");
            send(player, "register-failed", "%prefix% &cRegistrasi gagal. Hubungi staff.", Map.of());
        }
    }

    public void login(Player player, String password) {
        if (!checkUse(player)) return;
        AuthPlayerData data = getPlayerData(player);
        if (data == null) {
            send(player, "not-registered", "%prefix% &cAkun kamu belum terdaftar. Gunakan &f/register <password> <password confirm>&c.", Map.of());
            return;
        }
        if (sessionManager.isAuthenticated(player)) {
            finishAuthentication(player);
            send(player, "already-logged-in", "%prefix% &aKamu sudah login.", Map.of());
            return;
        }
        if (protectionManager.isLocked(data)) {
            send(player, "account-locked", "%prefix% &cAkun sementara terkunci. Coba lagi dalam &f%time%s&c.", Map.of("%time%", String.valueOf(protectionManager.getRemainingLockSeconds(data))));
            return;
        }

        try {
            if (!passwordManager.verify(password, data.getPasswordHash(), data.getSalt())) {
                protectionManager.recordFailedAttempt(data);
                dataManager.savePlayer(data);
                if (protectionManager.isLocked(data)) {
                    send(player, "account-locked", "%prefix% &cAkun sementara terkunci. Coba lagi dalam &f%time%s&c.", Map.of("%time%", String.valueOf(protectionManager.getRemainingLockSeconds(data))));
                } else {
                    send(player, "wrong-password", "%prefix% &cPassword salah.", Map.of());
                }
                return;
            }
        } catch (Exception exception) {
            plugin.getLogger().warning("VelioraLoginSecurity: gagal verifikasi password.");
            send(player, "login-failed", "%prefix% &cLogin gagal. Hubungi staff.", Map.of());
            return;
        }

        protectionManager.resetAttempts(data);
        data.setLastLogin(now());
        data.setLastIpHash(getIpHash(player));
        if (data.getUuid() == null) data.setUuid(player.getUniqueId());
        dataManager.updateName(data, player.getName());
        sessionManager.setState(player, AuthState.AUTHENTICATED);
        finishAuthentication(player);
        send(player, "login-success", "%prefix% &aLogin berhasil. Selamat bermain!", Map.of());
    }

    public void changePassword(Player player, String oldPassword, String newPassword) {
        if (!checkUse(player)) return;
        if (!requireLoggedIn(player)) return;
        AuthPlayerData data = getPlayerData(player);
        if (data == null) {
            send(player, "not-registered", "%prefix% &cAkun kamu belum terdaftar. Gunakan &f/register <password> <password confirm>&c.", Map.of());
            return;
        }
        if (!validatePasswordAndSend(player, newPassword)) return;

        try {
            if (!passwordManager.verify(oldPassword, data.getPasswordHash(), data.getSalt())) {
                send(player, "wrong-password", "%prefix% &cPassword salah.", Map.of());
                return;
            }
            LoginSecurityPasswordManager.PasswordHash passwordHash = passwordManager.createHash(newPassword);
            data.setPasswordHash(passwordHash.hash());
            data.setSalt(passwordHash.salt());
            protectionManager.resetAttempts(data);
            dataManager.savePlayer(data);
            finishAuthentication(player);
            send(player, "changepass-success", "%prefix% &aPassword berhasil diganti.", Map.of());
        } catch (Exception exception) {
            plugin.getLogger().warning("VelioraLoginSecurity: gagal mengganti password player.");
            send(player, "changepass-failed", "%prefix% &cGagal mengganti password. Hubungi staff.", Map.of());
        }
    }

    public void unregister(Player player, String password) {
        if (!checkUse(player)) return;
        if (!requireLoggedIn(player)) return;
        AuthPlayerData data = getPlayerData(player);
        if (data == null) {
            send(player, "not-registered", "%prefix% &cAkun kamu belum terdaftar. Gunakan &f/register <password> <password confirm>&c.", Map.of());
            return;
        }
        try {
            if (!passwordManager.verify(password, data.getPasswordHash(), data.getSalt())) {
                send(player, "wrong-password", "%prefix% &cPassword salah.", Map.of());
                return;
            }
            dataManager.deleteByName(data.getName());
            sessionManager.setState(player, AuthState.WAITING_REGISTER);
            sessionManager.setAuthLocation(player, player.getLocation());
            LoginSecurityBlindnessManager.apply(player, configManager);
            send(player, "unregister-success", "%prefix% &aAkun login kamu berhasil dihapus.", Map.of());
            send(player, "need-register", "%prefix% &eSilakan daftar dengan &f/register <password> <password confirm>&e.", Map.of());
        } catch (Exception exception) {
            plugin.getLogger().warning("VelioraLoginSecurity: gagal unregister player.");
            send(player, "unregister-failed", "%prefix% &cGagal menghapus akun login. Hubungi staff.", Map.of());
        }
    }

    public void logout(Player player) {
        if (!checkUse(player)) return;
        if (!sessionManager.isAuthenticated(player)) {
            send(player, "not-logged-in", "%prefix% &cKamu belum login.", Map.of());
            return;
        }
        AuthPlayerData data = getPlayerData(player);
        sessionManager.setAuthLocation(player, player.getLocation());
        sessionManager.setState(player, data == null ? AuthState.WAITING_REGISTER : AuthState.WAITING_LOGIN);
        LoginSecurityBlindnessManager.apply(player, configManager);
        send(player, "logout-success", "%prefix% &aKamu berhasil logout.", Map.of());
    }

    public void ownerReset(CommandSender sender, String targetName) {
        if (!configManager.hasOwnerPermission(sender)) {
            send(sender, "no-permission", "%prefix% &cKamu tidak punya izin.", Map.of());
            return;
        }
        AuthPlayerData data = dataManager.getByName(targetName);
        if (data == null) {
            send(sender, "target-not-found", "%prefix% &cData player &f%player% &ctidak ditemukan.", Map.of("%player%", targetName));
            return;
        }
        dataManager.deleteByName(data.getName());
        Player online = Bukkit.getPlayerExact(data.getName());
        if (online != null) {
            sessionManager.setAuthLocation(online, online.getLocation());
            sessionManager.setState(online, AuthState.WAITING_REGISTER);
            LoginSecurityBlindnessManager.apply(online, configManager);
            send(online, "need-register", "%prefix% &eSilakan daftar dengan &f/register <password> <password confirm>&e.", Map.of());
        }
        send(sender, "owner-reset-success", "%prefix% &aPassword player &f%player% &aberhasil direset. Player harus register ulang.", Map.of("%player%", data.getName()));
    }

    public void ownerChangePassword(CommandSender sender, String targetName, String newPassword) {
        if (!configManager.hasOwnerPermission(sender)) {
            send(sender, "no-permission", "%prefix% &cKamu tidak punya izin.", Map.of());
            return;
        }
        AuthPlayerData data = dataManager.getByName(targetName);
        if (data == null) {
            send(sender, "target-not-found", "%prefix% &cData player &f%player% &ctidak ditemukan.", Map.of("%player%", targetName));
            return;
        }
        if (!validatePasswordAndSend(sender, newPassword)) return;
        try {
            LoginSecurityPasswordManager.PasswordHash passwordHash = passwordManager.createHash(newPassword);
            data.setPasswordHash(passwordHash.hash());
            data.setSalt(passwordHash.salt());
            protectionManager.resetAttempts(data);
            dataManager.savePlayer(data);
            Player online = Bukkit.getPlayerExact(data.getName());
            if (online != null) {
                sessionManager.setAuthLocation(online, online.getLocation());
                sessionManager.setState(online, AuthState.WAITING_LOGIN);
                LoginSecurityBlindnessManager.apply(online, configManager);
                send(online, "need-login", "%prefix% &eSilakan login dengan &f/login <password>&e.", Map.of());
            }
            send(sender, "owner-changepass-success", "%prefix% &aPassword player &f%player% &aberhasil diubah.", Map.of("%player%", data.getName()));
        } catch (Exception exception) {
            plugin.getLogger().warning("VelioraLoginSecurity: gagal owner change password.");
            send(sender, "owner-changepass-failed", "%prefix% &cGagal mengubah password player.", Map.of());
        }
    }

    public void sendHelp(CommandSender sender) {
        sendLines(sender, configManager.getMessageList("help", List.of(
                "&8&m--------------------------------",
                "&b&lVelioraLoginSecurity",
                "&f/register <password> <confirm> &7- Daftar akun.",
                "&f/login <password> &7- Login akun.",
                "&f/changepass <old> <new> &7- Ganti password.",
                "&f/unregister <password> &7- Hapus akun login.",
                "&f/logout &7- Logout akun.",
                "&8&m--------------------------------"
        )), Map.of());
        if (configManager.hasOwnerPermission(sender)) {
            sendLines(sender, configManager.getMessageList("help-owner", List.of(
                    "&8&m--------------------------------",
                    "&b&lVelioraLoginSecurity Owner",
                    "&f/risetpw <playername> &7- Reset password player.",
                    "&f/cpowner <playername> <new> &7- Ubah password player.",
                    "&8&m--------------------------------"
            )), Map.of());
        }
    }

    public void sendStatus(CommandSender sender) {
        sendLines(sender, configManager.getMessageList("status", List.of(
                "&8&m--------------------------------",
                "&b&lVelioraLoginSecurity Status",
                "&7Enabled: &f%enabled%",
                "&7Require Login: &f%require_login%",
                "&7Registered Accounts: &f%accounts%",
                "&7Active Sessions: &f%sessions%",
                "&7Max Attempts: &f%max_attempts%",
                "&7Lock Seconds: &f%lock_seconds%",
                "&7Hashing: &f%hashing%",
                "&8&m--------------------------------"
        )), Map.of(
                "%enabled%", String.valueOf(configManager.isEnabled()),
                "%require_login%", String.valueOf(configManager.isRequireLogin()),
                "%accounts%", String.valueOf(dataManager.countRegistered()),
                "%sessions%", String.valueOf(sessionManager.countAuthenticated()),
                "%max_attempts%", String.valueOf(configManager.getMaxLoginAttempts()),
                "%lock_seconds%", String.valueOf(configManager.getLockSeconds()),
                "%hashing%", configManager.getHashAlgorithm()
        ));
    }

    public void sendReloadSuccess(CommandSender sender) { send(sender, "reload-success", "%prefix% &aVelioraLoginSecurity berhasil direload.", Map.of()); }
    public void sendNoPermission(CommandSender sender) { send(sender, "no-permission", "%prefix% &cKamu tidak punya izin.", Map.of()); }
    public void sendPlayerOnly(CommandSender sender) { send(sender, "player-only", "%prefix% &cCommand ini hanya bisa digunakan oleh player.", Map.of()); }
    public void sendUsage(CommandSender sender, String messageKey) { send(sender, messageKey, "%prefix% &cGunakan command dengan format yang benar.", Map.of()); }

    private boolean checkUse(Player player) {
        if (!configManager.hasUsePermission(player)) {
            sendNoPermission(player);
            return false;
        }
        return true;
    }

    private boolean requireLoggedIn(Player player) {
        if (!isAuthenticated(player)) {
            send(player, "not-logged-in", "%prefix% &cKamu belum login.", Map.of());
            return false;
        }
        return true;
    }

    private boolean validatePasswordAndSend(CommandSender sender, String password) {
        LoginSecurityProtectionManager.PasswordValidation validation = protectionManager.validatePassword(password);
        if (validation == LoginSecurityProtectionManager.PasswordValidation.TOO_SHORT) {
            send(sender, "password-too-short", "%prefix% &cPassword terlalu pendek. Minimal &f%min% &ckarakter.", Map.of("%min%", String.valueOf(configManager.getMinPasswordLength())));
            return false;
        }
        if (validation == LoginSecurityProtectionManager.PasswordValidation.TOO_LONG) {
            send(sender, "password-too-long", "%prefix% &cPassword terlalu panjang. Maksimal &f%max% &ckarakter.", Map.of("%max%", String.valueOf(configManager.getMaxPasswordLength())));
            return false;
        }
        return true;
    }

    private void finishAuthentication(Player player) {
        LoginSecurityBlindnessManager.removeSafe(player);
    }

    private AuthPlayerData getPlayerData(Player player) {
        AuthPlayerData data = dataManager.getByUuid(player.getUniqueId());
        if (data != null) {
            if (data.getName() == null || !data.getName().equalsIgnoreCase(player.getName())) dataManager.updateName(data, player.getName());
            return data;
        }
        return dataManager.getByName(player.getName());
    }

    private String getIpHash(Player player) {
        InetSocketAddress address = player.getAddress();
        if (address == null || address.getAddress() == null) return "";
        return passwordManager.hashIp(address.getAddress().getHostAddress());
    }

    private String now() { return LocalDateTime.now().format(TIME_FORMATTER); }

    private void send(CommandSender sender, String path, String fallback, Map<String, String> placeholders) {
        sender.sendMessage(configManager.color(apply(configManager.getMessage(path, fallback), placeholders)));
    }

    private void sendLines(CommandSender sender, List<String> lines, Map<String, String> placeholders) {
        for (String line : lines) sender.sendMessage(configManager.color(apply(line, placeholders)));
    }

    private String apply(String text, Map<String, String> placeholders) {
        String result = text == null ? "" : text;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) result = result.replace(entry.getKey(), entry.getValue());
        return result;
    }
}
