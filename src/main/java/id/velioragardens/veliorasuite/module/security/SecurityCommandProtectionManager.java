package id.velioragardens.veliorasuite.module.security;

import id.velioragardens.veliorasuite.module.security.model.SecurityDecision;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class SecurityCommandProtectionManager {

    private final SecurityConfigManager configManager;
    private final SecurityRiskManager riskManager;

    public SecurityCommandProtectionManager(SecurityConfigManager configManager, SecurityRiskManager riskManager) {
        this.configManager = configManager;
        this.riskManager = riskManager;
    }

    public SecurityDecision check(Player player, String commandLine) {
        if (!configManager.isEnabled() || !configManager.isCommandProtectionEnabled() || player == null || configManager.hasBypass(player)) {
            return SecurityDecision.allow();
        }

        String command = commandToken(commandLine);
        if (command.isBlank() || isIgnored(command)) return SecurityDecision.allow();

        if (commandLine != null && commandLine.length() > configManager.getMaxCommandLength()) {
            int risk = riskManager.scoreCommandRisk(player, true);
            return blocked(player, risk, "COMMAND_EXPLOIT", "Command terlalu panjang", "command-too-long", "%prefix% &cCommand terlalu panjang.");
        }

        if (configManager.isBlockControlCharacters() && hasSuspiciousControl(commandLine)) {
            int risk = riskManager.scoreCommandRisk(player, true);
            return blocked(player, risk, "COMMAND_EXPLOIT", "Command mengandung karakter tidak valid", "command-invalid", "%prefix% &cCommand tidak valid.");
        }

        if (blockedCommands().contains(command)) {
            int risk = riskManager.scoreCommandRisk(player, false);
            return blocked(player, risk, "COMMAND_BLOCKED", "Command diblokir config", "command-blocked", "%prefix% &cCommand ini tidak diperbolehkan.");
        }

        return SecurityDecision.allow();
    }

    public Set<String> blockedCommands() {
        Set<String> commands = new HashSet<>();
        for (String raw : configManager.getBlockedCommands()) {
            String normalized = normalize(raw);
            if (!normalized.isBlank()) commands.add(normalized);
        }
        return commands;
    }

    public boolean isIgnored(String command) {
        String normalized = normalize(command);
        for (String raw : configManager.getIgnoredCommands()) {
            if (normalized.equals(normalize(raw))) return true;
        }
        return false;
    }

    private SecurityDecision blocked(Player player, int risk, String type, String reason, String key, String fallback) {
        String action = riskManager.actionForRisk(risk);
        return new SecurityDecision(true, risk >= configManager.getRiskAlertThreshold(), type, player.getName(), risk, reason, action, key, fallback);
    }

    private boolean hasSuspiciousControl(String input) {
        if (input == null) return false;
        for (int offset = 0; offset < input.length();) {
            int codePoint = input.codePointAt(offset);
            int type = Character.getType(codePoint);
            if ((Character.isISOControl(codePoint) && !Character.isWhitespace(codePoint)) || type == Character.FORMAT || type == Character.PRIVATE_USE || type == Character.SURROGATE) {
                return true;
            }
            offset += Character.charCount(codePoint);
        }
        return false;
    }

    private String commandToken(String commandLine) {
        if (commandLine == null || commandLine.isBlank()) return "";
        return normalize(commandLine.trim().split("\\s+")[0]);
    }

    private String normalize(String command) {
        if (command == null || command.isBlank()) return "";
        String normalized = command.trim().toLowerCase(Locale.ROOT);
        if (!normalized.startsWith("/")) normalized = "/" + normalized;
        return normalized;
    }
}
