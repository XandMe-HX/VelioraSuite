package id.velioragardens.veliorasuite.module.security;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.module.security.model.SecurityDecision;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SecurityManager {

    private final VelioraSuite plugin;
    private final SecurityConfigManager configManager;
    private final SecurityRiskManager riskManager;
    private final SecurityAlertManager alertManager;
    private final SecurityJoinProtectionManager joinProtectionManager;
    private final SecurityCommandProtectionManager commandProtectionManager;
    private final SecurityTabProtectionManager tabProtectionManager;

    public SecurityManager(VelioraSuite plugin) {
        this.plugin = plugin;
        this.configManager = new SecurityConfigManager(plugin);
        this.riskManager = new SecurityRiskManager(plugin, configManager);
        this.alertManager = new SecurityAlertManager(plugin, configManager);
        this.joinProtectionManager = new SecurityJoinProtectionManager(configManager, riskManager);
        this.commandProtectionManager = new SecurityCommandProtectionManager(configManager, riskManager);
        this.tabProtectionManager = new SecurityTabProtectionManager(configManager, commandProtectionManager);
    }

    public void load() {
        configManager.load();
        plugin.getLogger().info("VelioraSecurity loaded.");
    }

    public void reload() {
        configManager.load();
        alertManager.clearCooldowns();
        joinProtectionManager.clear();
    }

    public SecurityConfigManager getConfigManager() { return configManager; }
    public SecurityTabProtectionManager getTabProtectionManager() { return tabProtectionManager; }

    public SecurityDecision checkJoin(Player player) {
        SecurityDecision decision = joinProtectionManager.check(player);
        alertIfNeeded(decision);
        return decision;
    }

    public SecurityDecision checkCommand(Player player, String commandLine) {
        SecurityDecision decision = commandProtectionManager.check(player, commandLine);
        alertIfNeeded(decision);
        return decision;
    }

    public void sendHelp(CommandSender sender) {
        sendLines(sender, configManager.messageList("help", List.of(
                "&8&m--------------------------------",
                "&c&lVelioraSecurity",
                "&f/vsecurity status &7- Cek status security.",
                "&f/vsecurity alerts &7- Lihat alert terbaru.",
                "&f/vsecurity reload &7- Reload config.",
                "&8&m--------------------------------"
        )), Map.of());
    }

    public void sendStatus(CommandSender sender) {
        sendLines(sender, configManager.messageList("status", List.of(
                "&8&m--------------------------------",
                "&c&lVelioraSecurity Status",
                "&7Enabled: &f%enabled%",
                "&7Join Protection: &f%join_protection%",
                "&7Name Protection: &f%name_protection%",
                "&7Command Protection: &f%command_protection%",
                "&7Tab Protection: &f%tab_protection%",
                "&7Blocked Commands: &f%blocked_commands%",
                "&7Recent Alerts: &f%recent_alerts%",
                "&8&m--------------------------------"
        )), statusPlaceholders());
    }

    public void sendAlerts(CommandSender sender) {
        alertManager.sendRecent(sender);
    }

    public void sendReloadSuccess(CommandSender sender) {
        send(sender, "reload-success", "%prefix% &aVelioraSecurity berhasil direload.", Map.of());
    }

    public void sendNoPermission(CommandSender sender) {
        send(sender, "no-permission", "%prefix% &cKamu tidak punya izin.", Map.of());
    }

    public String denyMessage(SecurityDecision decision) {
        return configManager.color(configManager.message(decision.messageKey(), decision.fallbackMessage()));
    }

    private void alertIfNeeded(SecurityDecision decision) {
        if (decision == null || !decision.alert()) return;
        alertManager.alert(decision.type(), decision.player(), decision.risk(), decision.reason(), decision.action());
    }

    private Map<String, String> statusPlaceholders() {
        Map<String, String> map = new HashMap<>();
        map.put("%enabled%", String.valueOf(configManager.isEnabled()));
        map.put("%join_protection%", String.valueOf(configManager.isJoinProtectionEnabled()));
        map.put("%name_protection%", String.valueOf(configManager.isNameProtectionEnabled()));
        map.put("%command_protection%", String.valueOf(configManager.isCommandProtectionEnabled()));
        map.put("%tab_protection%", String.valueOf(configManager.isTabProtectionEnabled()));
        map.put("%blocked_commands%", String.valueOf(commandProtectionManager.blockedCommands().size()));
        map.put("%recent_alerts%", String.valueOf(alertManager.recentCount()));
        return map;
    }

    private void send(CommandSender sender, String path, String fallback, Map<String, String> placeholders) {
        sender.sendMessage(configManager.color(apply(configManager.message(path, fallback), placeholders)));
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
