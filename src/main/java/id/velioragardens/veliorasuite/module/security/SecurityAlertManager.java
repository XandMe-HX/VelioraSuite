package id.velioragardens.veliorasuite.module.security;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.module.security.model.SecurityAlert;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SecurityAlertManager {

    private final VelioraSuite plugin;
    private final SecurityConfigManager configManager;
    private final Deque<SecurityAlert> recentAlerts = new ArrayDeque<>();
    private final Map<String, Long> cooldowns = new HashMap<>();

    public SecurityAlertManager(VelioraSuite plugin, SecurityConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    public void alert(String type, String player, int risk, String reason, String action) {
        if (!configManager.isAlertsEnabled()) return;
        SecurityAlert alert = new SecurityAlert(type, player, risk, reason, action, System.currentTimeMillis());
        addRecent(alert);

        String cooldownKey = type + ":" + player + ":" + action;
        long now = System.currentTimeMillis();
        long until = cooldowns.getOrDefault(cooldownKey, 0L);
        if (until > now) return;
        cooldowns.put(cooldownKey, now + (configManager.getAlertCooldownSeconds() * 1000L));

        Map<String, String> placeholders = placeholders(alert);
        List<String> lines = configManager.messageList("risk-alert", configManager.messageList("alert", List.of(
                "&8&m--------------------------------",
                "&c&lSecurity Risk Alert",
                "&7Type: &f%type%",
                "&7Player: &f%player%",
                "&7Risk: &f%risk%",
                "&7Reason: &f%reason%",
                "&7Action: &f%action%",
                "&8&m--------------------------------"
        )));

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!configManager.hasAlerts(online)) continue;
            sendLines(online, lines, placeholders);
        }
        plugin.getLogger().info("Security alert: " + type + " player=" + player + " risk=" + risk + " action=" + action);
    }

    public void sendRecent(CommandSender sender) {
        if (recentAlerts.isEmpty()) {
            sender.sendMessage(configManager.color(configManager.getPrefix() + "&eBelum ada alert terbaru."));
            return;
        }
        int index = 1;
        for (SecurityAlert alert : recentAlerts) {
            sender.sendMessage(configManager.color("&c#" + index + " &7" + alert.type() + " &f" + alert.player() + " &7risk=&f" + alert.risk() + " &7action=&f" + alert.action()));
            index++;
        }
    }

    public int recentCount() {
        return recentAlerts.size();
    }

    public void clearCooldowns() {
        cooldowns.clear();
    }

    private void addRecent(SecurityAlert alert) {
        recentAlerts.addFirst(alert);
        while (recentAlerts.size() > configManager.getMaxRecentAlerts()) recentAlerts.removeLast();
    }

    private Map<String, String> placeholders(SecurityAlert alert) {
        Map<String, String> map = new HashMap<>();
        map.put("%type%", alert.type());
        map.put("%player%", alert.player());
        map.put("%risk%", String.valueOf(alert.risk()));
        map.put("%reason%", alert.reason());
        map.put("%action%", alert.action());
        return map;
    }

    private void sendLines(CommandSender sender, List<String> lines, Map<String, String> placeholders) {
        for (String line : lines) {
            String result = line;
            for (Map.Entry<String, String> entry : placeholders.entrySet()) result = result.replace(entry.getKey(), entry.getValue());
            sender.sendMessage(configManager.color(result));
        }
    }
}
