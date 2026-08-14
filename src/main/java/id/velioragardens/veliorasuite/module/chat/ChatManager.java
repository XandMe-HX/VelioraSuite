package id.velioragardens.veliorasuite.module.chat;

import id.velioragardens.veliorasuite.VelioraSuite;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ChatManager {

    private final VelioraSuite plugin;
    private final ChatConfigManager configManager;
    private final ChatCooldownManager cooldownManager;
    private final ChatCooldownManager commandCooldownManager;
    private final ChatFilterManager filterManager;
    private final ChatPlaceholderManager placeholderManager;
    private final ChatFormatManager formatManager;
    private final Map<UUID, Long> autoReplyCooldowns = new ConcurrentHashMap<>();
    private Map<String, ChatConfigManager.AutoReplyEntry> autoReplies = Map.of();

    public ChatManager(VelioraSuite plugin) {
        this.plugin = plugin;
        this.configManager = new ChatConfigManager(plugin);
        this.cooldownManager = new ChatCooldownManager();
        this.commandCooldownManager = new ChatCooldownManager();
        this.filterManager = new ChatFilterManager(configManager);
        this.placeholderManager = new ChatPlaceholderManager(plugin, configManager);
        this.formatManager = new ChatFormatManager(configManager, placeholderManager);
    }

    public void load() {
        configManager.load();
        autoReplies = configManager.getAutoReplies();
        plugin.getLogger().info("VelioraChat loaded.");
    }

    public void reload() {
        configManager.load();
        autoReplies = configManager.getAutoReplies();
        cooldownManager.clear();
        commandCooldownManager.clear();
        autoReplyCooldowns.clear();
        filterManager.clear();
    }

    public void shutdown() {
        cooldownManager.clear();
        commandCooldownManager.clear();
        autoReplyCooldowns.clear();
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

        scheduleAutoReply(player, finalMessage);

        if (configManager.isCooldownEnabled() && configManager.isProtectionEnabled() && !player.hasPermission(configManager.getBypassCooldownPermission()) && !hasAdminPermission(player)) {
            cooldownManager.setCooldown(player.getUniqueId(), configManager.getCooldownSeconds());
        }

        if (!configManager.isFormatterEnabled() || configManager.isEssentialsMode()) {
            return ChatProcessResult.pass(finalMessage);
        }

        return ChatProcessResult.formatted(formatManager.formatPublicChat(player, finalMessage));
    }

    public boolean shouldCancelCommand(Player player, String commandLine) {
        if (!configManager.isEnabled() || !configManager.isProtectionEnabled() || !configManager.isCommandSpamEnabled()) {
            return false;
        }
        if (player.hasPermission(configManager.getBypassCommandCooldownPermission()) || hasAdminPermission(player)) {
            return false;
        }

        String command = normalizeCommand(commandLine);
        if (command.isBlank() || isIgnoredCommand(command)) {
            return false;
        }

        int cooldownSeconds = getCommandCooldownSeconds(command);
        if (cooldownSeconds <= 0) {
            return false;
        }

        long remaining = commandCooldownManager.getRemainingSeconds(player.getUniqueId());
        if (remaining > 0) {
            send(player, "command-cooldown", "%prefix% &cTunggu &f%time%s &csebelum memakai command lagi.", Map.of("%time%", String.valueOf(remaining)));
            return true;
        }

        commandCooldownManager.setCooldown(player.getUniqueId(), cooldownSeconds);
        return false;
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
                "&f/vchat status &7- Lihat status formatter dan protection.",
                "&f/vchat reload &7- Reload config.",
                "&7Chat cooldown: &fsettings.cooldown.seconds",
                "&7Command cooldown: &fsettings.command-spam.seconds",
                "&7Word filter: &fsettings.word-filter.blocked-words",
                "&7Auto reply: &fsettings.auto-reply",
                "&8&m--------------------------------"
        )), Map.of());
    }

    public void sendStatus(CommandSender sender) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("%formatter%", String.valueOf(configManager.isFormatterEnabled()));
        placeholders.put("%essentials_mode%", String.valueOf(configManager.isEssentialsMode()));
        placeholders.put("%protection%", String.valueOf(configManager.isProtectionEnabled()));
        placeholders.put("%chat_cooldown%", String.valueOf(configManager.isCooldownEnabled()));
        placeholders.put("%chat_cooldown_seconds%", String.valueOf(configManager.getCooldownSeconds()));
        placeholders.put("%command_cooldown%", String.valueOf(configManager.isCommandSpamEnabled()));
        placeholders.put("%command_cooldown_seconds%", String.valueOf(configManager.getCommandSpamSeconds()));
        placeholders.put("%auto_reply%", String.valueOf(configManager.isAutoReplyEnabled()));
        placeholders.put("%auto_reply_cooldown%", String.valueOf(configManager.getAutoReplyCooldownSeconds()));
        placeholders.put("%anti_repeat%", String.valueOf(configManager.isAntiRepeatEnabled()));
        placeholders.put("%anti_repeat_max%", String.valueOf(configManager.getMaxRepeat()));
        placeholders.put("%anti_caps%", String.valueOf(configManager.isAntiCapsEnabled()));
        placeholders.put("%word_filter%", String.valueOf(configManager.isWordFilterEnabled()));
        placeholders.put("%word_filter_action%", configManager.getWordFilterAction());
        placeholders.put("%placeholderapi%", String.valueOf(Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null));

        sendLines(sender, configManager.getMessageList("status", List.of(
                "&8&m--------------------------------",
                "&b&lVelioraChat Status",
                "&7Formatter: &f%formatter%",
                "&7Essentials Mode: &f%essentials_mode%",
                "&7Protection: &f%protection%",
                "&7Chat Cooldown: &f%chat_cooldown% &7(%chat_cooldown_seconds%s)",
                "&7Command Cooldown: &f%command_cooldown% &7(%command_cooldown_seconds%s)",
                "&7Auto Reply: &f%auto_reply% &7(%auto_reply_cooldown%s)",
                "&7Anti Repeat: &f%anti_repeat% &7(max %anti_repeat_max%)",
                "&7Anti Caps: &f%anti_caps%",
                "&7Word Filter: &f%word_filter% &7(%word_filter_action%)",
                "&7PlaceholderAPI: &f%placeholderapi%",
                "&8&m--------------------------------"
        )), placeholders);
    }

    public void sendReloadSuccess(CommandSender sender) {
        send(sender, "reload-success", "%prefix% &aVelioraChat berhasil direload.", Map.of());
    }

    public void sendNoPermission(CommandSender sender) {
        send(sender, "no-permission", "%prefix% &cKamu tidak punya izin.", Map.of());
    }

    private void scheduleAutoReply(Player player, String message) {
        if (!configManager.isAutoReplyEnabled() || player == null || message == null || message.isBlank()) return;
        if (hasAdminPermission(player)) return;

        long now = System.currentTimeMillis();
        long last = autoReplyCooldowns.getOrDefault(player.getUniqueId(), 0L);
        long cooldownMillis = configManager.getAutoReplyCooldownSeconds() * 1000L;
        if (cooldownMillis > 0 && now - last < cooldownMillis) return;

        String normalizedMessage = message.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, ChatConfigManager.AutoReplyEntry> entry : autoReplies.entrySet()) {
            ChatConfigManager.AutoReplyEntry reply = entry.getValue();
            if (!matchesAnyTrigger(normalizedMessage, reply.triggers())) continue;

            autoReplyCooldowns.put(player.getUniqueId(), now);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) return;
                player.sendMessage(configManager.color(configManager.getAutoReplyPrefix() + "&7Auto bantuan: &f" + entry.getKey()));
                for (String line : reply.lines()) {
                    player.sendMessage(configManager.color(line));
                }
            }, configManager.getAutoReplyDelayTicks());
            return;
        }
    }

    private boolean matchesAnyTrigger(String normalizedMessage, List<String> triggers) {
        for (String trigger : triggers) {
            if (trigger == null || trigger.isBlank()) continue;
            if (normalizedMessage.contains(trigger.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    private int getCommandCooldownSeconds(String command) {
        Integer extra = configManager.getExtraCommandCooldowns().get(command);
        if (extra != null) {
            return extra;
        }
        return configManager.isCommandSpamApplyToAllCommands() ? configManager.getCommandSpamSeconds() : 0;
    }

    private boolean isIgnoredCommand(String command) {
        String withSlash = "/" + command;
        for (String ignored : configManager.getIgnoredCommands()) {
            if (ignored == null || ignored.isBlank()) continue;
            String normalized = ignored.trim().toLowerCase(Locale.ROOT);
            if (!normalized.startsWith("/")) normalized = "/" + normalized;
            if (normalized.equals(withSlash)) {
                return true;
            }
        }
        return false;
    }

    private String normalizeCommand(String commandLine) {
        if (commandLine == null || commandLine.isBlank()) {
            return "";
        }
        String command = commandLine.trim().split("\\s+")[0].toLowerCase(Locale.ROOT);
        if (command.startsWith("/")) {
            command = command.substring(1);
        }
        return command;
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
