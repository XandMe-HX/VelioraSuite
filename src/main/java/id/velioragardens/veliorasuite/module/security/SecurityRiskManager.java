package id.velioragardens.veliorasuite.module.security;

import id.velioragardens.veliorasuite.VelioraSuite;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Locale;

public final class SecurityRiskManager {

    private final VelioraSuite plugin;
    private final SecurityConfigManager configManager;

    public SecurityRiskManager(VelioraSuite plugin, SecurityConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    public int scoreJoinRisk(Player player, int uniqueNames, int rejoinCount, int joinCount, boolean invalidName) {
        if (!configManager.isRiskScoreEnabled() || player == null || configManager.hasBypass(player)) return 0;
        int risk = 0;
        if (uniqueNames >= configManager.getDifferentNamesAlertThreshold()) risk += 40;
        if (invalidName) risk += 30;
        if (joinCount > configManager.getMaxJoinsPerIp()) risk += 20;
        if (rejoinCount >= configManager.getRejoinAlertThreshold()) risk += 5;
        if (hasKnownLoginData(player.getName())) risk -= 30;
        return Math.max(0, risk);
    }

    public int scoreCommandRisk(Player player, boolean exploit) {
        if (!configManager.isRiskScoreEnabled() || player == null || configManager.hasBypass(player)) return 0;
        int risk = exploit ? 30 : 0;
        if (hasKnownLoginData(player.getName())) risk -= 30;
        return Math.max(0, risk);
    }

    public String actionForRisk(int risk) {
        if (risk >= configManager.getRiskTemporaryBlockThreshold()) return "TEMPORARY_DENY";
        if (risk >= configManager.getRiskKickThreshold()) return "KICK_TEMPORARY";
        if (risk >= configManager.getRiskAlertThreshold()) return "ALERT_ONLY";
        return "ALLOW";
    }

    public String hashIp(String ip) {
        if (ip == null || ip.isBlank()) return "unknown";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Base64.getEncoder().encodeToString(digest.digest(ip.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ignored) {
            return "unknown";
        }
    }

    private boolean hasKnownLoginData(String playerName) {
        if (playerName == null || playerName.isBlank()) return false;
        File file = new File(plugin.getDataFolder(), "data/loginsecurity.yml");
        if (!file.exists()) return false;
        FileConfiguration data = YamlConfiguration.loadConfiguration(file);
        return data.isConfigurationSection("players." + playerName.toLowerCase(Locale.ROOT));
    }
}
