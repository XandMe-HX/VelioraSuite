package id.velioragardens.veliorasuite.module.chat;

import id.velioragardens.veliorasuite.VelioraSuite;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ChatManager {

    private static final Pattern INTERACTIVE_TOKEN = Pattern.compile("\\[(/[^\\s\\]]+(?:\\s+[^\\]]+)?|@[A-Za-z0-9_]{3,16}|item|inv(?:entory)?|ender(?:chest)?)\\]", Pattern.CASE_INSENSITIVE);
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final VelioraSuite plugin;
    private final ChatConfigManager configManager;
    private final ChatCooldownManager cooldownManager;
    private final ChatCooldownManager commandCooldownManager;
    private final ChatFilterManager filterManager;
    private final ChatPlaceholderManager placeholderManager;
    private final ChatFormatManager formatManager;
    private final Map<UUID, Long> autoReplyCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> interactiveShareCooldowns = new ConcurrentHashMap<>();
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
        interactiveShareCooldowns.clear();
        filterManager.clear();
    }

    public void shutdown() {
        cooldownManager.clear();
        commandCooldownManager.clear();
        autoReplyCooldowns.clear();
        interactiveShareCooldowns.clear();
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

    public boolean isInteractiveChatEnabled() {
        return configManager.isInteractiveChatEnabled();
    }

    public boolean broadcastInteractive(Player player, String message) {
        if (containsShareToken(message) && !canShareInteractive(player)) {
            long remaining = getShareCooldownRemaining(player);
            send(player, "interactive-share-cooldown", "%prefix% &cTunggu &f%time% detik &csebelum membagikan item atau inventory lagi.", Map.of("%time%", String.valueOf(remaining)));
            return false;
        }
        String formatted = formatManager.formatPublicChat(player, message);
        Component component = interactiveComponent(player, formatted);
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                onlinePlayer.sendMessage(component);
            }
            Bukkit.getConsoleSender().sendMessage(formatted);
        });
        if (containsShareToken(message)) {
            interactiveShareCooldowns.put(player.getUniqueId(), System.currentTimeMillis());
        }
        return true;
    }

    private Component interactiveComponent(Player sender, String formatted) {
        Pattern tokenPattern = Pattern.compile(INTERACTIVE_TOKEN.pattern() + "|" + Pattern.quote(sender.getName()), Pattern.CASE_INSENSITIVE);
        Matcher matcher = tokenPattern.matcher(formatted);
        Component result = Component.empty();
        int position = 0;
        while (matcher.find()) {
            result = result.append(LEGACY.deserialize(formatted.substring(position, matcher.start())));
            String token = matcher.group(1);
            if (token == null && matcher.group().equalsIgnoreCase(sender.getName())) {
                Component name = LEGACY.deserialize("&f" + sender.getName())
                        .clickEvent(ClickEvent.suggestCommand("/msg " + sender.getName() + " "))
                        .hoverEvent(HoverEvent.showText(playerHover(sender)));
                result = result.append(name);
            } else if (token.startsWith("/") && isAllowedInteractiveCommand(token)) {
                String command = token;
                String hover = configManager.color(configManager.getInteractiveChatHover().replace("%command%", command));
                Component button = LEGACY.deserialize("&b" + matcher.group());
                if (configManager.getInteractiveChatAction().equals("RUN_COMMAND")) {
                    button = button.clickEvent(ClickEvent.runCommand(command));
                } else {
                    button = button.clickEvent(ClickEvent.suggestCommand(command));
                }
                result = result.append(button.hoverEvent(HoverEvent.showText(LEGACY.deserialize(hover))));
            } else if (token.equalsIgnoreCase("item") && configManager.isInteractiveItemEnabled()) {
                ItemStack item = sender.getInventory().getItemInMainHand();
                if (item == null || item.getType().isAir()) {
                    result = result.append(LEGACY.deserialize("&7[item kosong]"));
                } else {
                    Component button = LEGACY.deserialize("&d[ITEM: &f" + item.getType().name().replace('_', ' ') + "&d]");
                    result = result.append(button.hoverEvent(item.asHoverEvent()));
                }
            } else if ((token.equalsIgnoreCase("inv") || token.equalsIgnoreCase("inventory")) && configManager.isInteractiveInventoryEnabled()) {
                Component button = LEGACY.deserialize("&a[INVENTORY: &f" + sender.getName() + "&a]")
                        .hoverEvent(HoverEvent.showText(inventoryHover(sender, false)));
                result = result.append(button);
            } else if ((token.equalsIgnoreCase("ender") || token.equalsIgnoreCase("enderchest")) && configManager.isInteractiveEnderEnabled()) {
                Component button = LEGACY.deserialize("&5[ENDER CHEST: &f" + sender.getName() + "&5]")
                        .hoverEvent(HoverEvent.showText(inventoryHover(sender, true)));
                result = result.append(button);
            } else if (token.startsWith("@") && configManager.isInteractiveMentionEnabled()) {
                String name = token.substring(1);
                Player target = Bukkit.getPlayerExact(name);
                if (target == null) {
                    result = result.append(LEGACY.deserialize(matcher.group()));
                } else {
                    Component button = LEGACY.deserialize("&e[@" + target.getName() + "]")
                            .clickEvent(ClickEvent.suggestCommand("/tpa " + target.getName()))
                            .hoverEvent(HoverEvent.showText(LEGACY.deserialize("&bKlik untuk menulis &f/tpa " + target.getName())));
                    result = result.append(button);
                }
            } else {
                result = result.append(LEGACY.deserialize(matcher.group()));
            }
            position = matcher.end();
        }
        return result.append(LEGACY.deserialize(formatted.substring(position)));
    }

    private Component playerHover(Player player) {
        String world = player.getWorld().getName();
        String location = player.getLocation().getBlockX() + ", " + player.getLocation().getBlockY() + ", " + player.getLocation().getBlockZ();
        String team = placeholderManager.getTeamName(player.getUniqueId());
        String lines = "&b&l" + player.getName()
                + "\n&7HP: &c" + Math.ceil(player.getHealth()) + "/" + Math.ceil(player.getMaxHealth())
                + "\n&7Dunia: &f" + world
                + "\n&7Lokasi: &f" + location
                + (team.isBlank() ? "" : "\n&7Tim: &d" + team)
                + "\n&eKlik untuk menulis pesan pribadi.";
        return LEGACY.deserialize(lines);
    }

    private boolean containsShareToken(String message) {
        if (message == null || message.isBlank()) return false;
        String normalized = message.toLowerCase(Locale.ROOT);
        return normalized.contains("[item]") || normalized.contains("[inv]") || normalized.contains("[inventory]")
                || normalized.contains("[ender]") || normalized.contains("[enderchest]");
    }

    private boolean canShareInteractive(Player player) {
        if (hasAdminPermission(player) || player.hasPermission("veliorasuite.chat.bypasssharecooldown")) return true;
        return getShareCooldownRemaining(player) <= 0;
    }

    private long getShareCooldownRemaining(Player player) {
        long seconds = configManager.getInteractiveShareCooldownSeconds();
        if (seconds <= 0) return 0;
        long last = interactiveShareCooldowns.getOrDefault(player.getUniqueId(), 0L);
        long elapsed = System.currentTimeMillis() - last;
        return Math.max(0L, (seconds * 1000L - elapsed + 999L) / 1000L);
    }

    private Component inventoryHover(Player sender, boolean enderChest) {
        ItemStack[] contents = enderChest ? sender.getEnderChest().getContents() : sender.getInventory().getStorageContents();
        List<String> items = new ArrayList<>();
        int maxItems = configManager.getInteractiveShareMaxItems();
        for (ItemStack item : contents) {
            if (item == null || item.getType().isAir()) continue;
            if (items.size() >= maxItems) break;
            String material = item.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
            String displayName = item.hasItemMeta() && item.getItemMeta().hasDisplayName()
                    ? item.getItemMeta().getDisplayName() : material;
            items.add("&7- &f" + displayName + " &8x&f" + item.getAmount());
        }
        String title = enderChest ? "&5&lENDER CHEST" : "&a&lINVENTORY";
        Component result = LEGACY.deserialize(title + " &7" + sender.getName());
        if (items.isEmpty()) return result.append(Component.newline()).append(LEGACY.deserialize("&8Kosong"));
        for (String item : items) {
            result = result.append(Component.newline()).append(LEGACY.deserialize(item));
        }
        if (items.size() >= maxItems) {
            result = result.append(Component.newline()).append(LEGACY.deserialize("&8Ditampilkan maksimal " + maxItems + " item."));
        }
        return result;
    }

    private boolean isAllowedInteractiveCommand(String command) {
        String root = command.substring(1).trim().split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        for (String allowed : configManager.getInteractiveChatCommands()) {
            String normalized = allowed == null ? "" : allowed.trim().toLowerCase(Locale.ROOT);
            if (normalized.startsWith("/")) normalized = normalized.substring(1);
            if (root.equals(normalized)) return true;
        }
        return false;
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
