package id.velioragardens.veliorasuite.module.chat;

import id.velioragardens.veliorasuite.VelioraSuite;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

public final class ChatManager {

    private final VelioraSuite plugin;
    private final ChatConfigManager configManager;
    private final ChatCooldownManager cooldownManager;
    private final ChatFilterManager filterManager;
    private final ChatPlaceholderManager placeholderManager;
    private final ChatFormatManager formatManager;

    public ChatManager(VelioraSuite plugin) {
        this.plugin = plugin;
        this.configManager = new ChatConfigManager(plugin);
        this.cooldownManager = new ChatCooldownManager();
        this.filterManager = new ChatFilterManager(configManager);
        this.placeholderManager = new ChatPlaceholderManager(plugin, configManager);
        this.formatManager = new ChatFormatManager(configManager, placeholderManager);
    }

    public void load() {
        configManager.load();
        plugin.getLogger().info("VelioraChat loaded.");
    }

    public void reload() {
        configManager.load();
        cooldownManager.clear();
        filterManager.clear();
    }

    public void shutdown() {
        cooldownManager.clear();
        filterManager.clear();
    }

    public ChatConfigManager getConfigManager() { return configManager; }
    public ChatPlaceholderManager getPlaceholderManager() { return placeholderManager; }

    public boolean hasUsePermission(CommandSender sender) {
        return sender.hasPermission(configManager.getUsePermission()) || hasAdminPermission(sender);
    }

    public boolean hasAdminPermission(CommandSender sender) {
        return sender.hasPermission(configManager.getAdminPermission()) || sender.isOp();
    }

    public boolean hasReloadPermission(CommandSender sender) {
        return sender.hasPermission(configManager.getReloadPermission()) || hasAdminPermission(sender);
    }

    public ChatProcessResult processChat(Player player, String message) {
        if (!configManager.isEnabled()) {
            return ChatProcessResult.pass(message);
        }

        String finalMessage = message;

        if (configManager.isProtectionEnabled()) {
            if (configManager.isCooldownEnabled() && !player.hasPermission(configManager.getBypassCooldownPermission()) && !hasAdminPermission(player)) {
                long remaining = cooldownManager.getRemainingSeconds(player.getUniqueId());
                if (remaining > 0) {
                    send(player, "cooldown", "%prefix% &cTunggu &f%time%s &csebelum chat lagi.", Map.of("%time%", String.valueOf(remaining)));
                    return ChatProcessResult.cancel();
                }
            }

            if (!player.hasPermission(configManager.getBypassFilterPermission()) && !hasAdminPermission(player)) {
                ChatFilterManager.FilterResult filterResult = filterManager.filter(player, finalMessage);
                if (filterResult.cancelled()) {
                    sendFilterMessage(player, filterResult.messageKey());
                    return ChatProcessResult.cancel();
                }
                finalMessage = filterResult.message();
                if (filterResult.changed() && !filterResult.messageKey().isBlank()) {
                    sendFilterMessage(player, filterResult.messageKey());
                }
            }
        }

        if (configManager.isCooldownEnabled() && configManager.isProtectionEnabled() && !player.hasPermission(configManager.getBypassCooldownPermission()) && !hasAdminPermission(player)) {
            cooldownManager.setCooldown(player.getUniqueId(), configManager.getCooldownSeconds());
        }

        if (!configManager.isFormatterEnabled() || configManager.isEssentialsMode()) {
            return ChatProcessResult.pass(finalMessage);
        }

        return ChatProcessResult.formatted(formatManager.formatPublicChat(player, finalMessage));
    }

    public void broadcastFormatted(String formattedMessage) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                onlinePlayer.sendMessage(formattedMessage);
            }
            Bukkit.getConsoleSender().sendMessage(formattedMessage);
        });
    }

    public void sendHelp(CommandSender sender) {
        sendLines(sender, configManager.getMessageList("help", List.of(
                "&8&m--------------------------------",
                "&b&lVelioraChat",
                "&f/vchat status &7- Cek status chat.",
                "&f/vchat reload &7- Reload config.",
                "&8&m--------------------------------"
        )), Map.of());
    }

    public void sendStatus(CommandSender sender) {
        sendLines(sender, configManager.getMessageList("status", List.of(
                "&8&m--------------------------------",
                "&b&lVelioraChat Status",
                "&7Formatter: &f%formatter%",
                "&7Essentials Mode: &f%essentials_mode%",
                "&7PlaceholderAPI: &f%placeholderapi%",
                "&7Protection: &f%protection%",
                "&8&m--------------------------------"
        )), Map.of(
                "%formatter%", String.valueOf(configManager.isFormatterEnabled()),
                "%essentials_mode%", String.valueOf(configManager.isEssentialsMode()),
                "%placeholderapi%", String.valueOf(Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null),
                "%protection%", String.valueOf(configManager.isProtectionEnabled())
        ));
    }

    public void sendReloadSuccess(CommandSender sender) {
        send(sender, "reload-success", "%prefix% &aVelioraChat berhasil direload.", Map.of());
    }

    public void sendNoPermission(CommandSender sender) {
        send(sender, "no-permission", "%prefix% &cKamu tidak punya izin.", Map.of());
    }

    private void sendFilterMessage(Player player, String messageKey) {
        switch (messageKey) {
            case "repeat-blocked" -> send(player, "repeat-blocked", "%prefix% &cJangan mengirim pesan yang sama berulang kali.", Map.of());
            case "caps-fixed" -> send(player, "caps-fixed", "%prefix% &7Pesan caps kamu dirapikan otomatis.", Map.of());
            case "caps-blocked" -> send(player, "caps-blocked", "%prefix% &cPesan caps tidak diperbolehkan.", Map.of());
            case "word-blocked" -> send(player, "word-blocked", "%prefix% &cPesan mengandung kata yang tidak diperbolehkan.", Map.of());
            default -> { }
        }
    }

    private void send(CommandSender sender, String path, String fallback, Map<String, String> placeholders) {
        sender.sendMessage(configManager.color(apply(configManager.getMessage(path, fallback), placeholders)));
    }

    private void sendLines(CommandSender sender, List<String> lines, Map<String, String> placeholders) {
        for (String line : lines) {
            sender.sendMessage(configManager.color(apply(line, placeholders)));
        }
    }

    private String apply(String text, Map<String, String> placeholders) {
        String result = text == null ? "" : text;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
    }

    public record ChatProcessResult(boolean cancelled, boolean formatted, String message) {
        public static ChatProcessResult cancel() { return new ChatProcessResult(true, false, ""); }
        public static ChatProcessResult pass(String message) { return new ChatProcessResult(false, false, message); }
        public static ChatProcessResult formatted(String message) { return new ChatProcessResult(false, true, message); }
    }
}
