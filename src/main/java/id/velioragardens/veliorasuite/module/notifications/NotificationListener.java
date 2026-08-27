package id.velioragardens.veliorasuite.module.notifications;

import id.velioragardens.veliorasuite.VelioraSuite;
import org.bukkit.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.File;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class NotificationListener implements Listener {
    private final VelioraSuite plugin;
    private final Map<String, Long> mentionCooldowns = new HashMap<>();
    private static final DecimalFormat MONEY_FORMAT = new DecimalFormat("#,##0.##");
    private YamlConfiguration config;

    public NotificationListener(VelioraSuite plugin) { this.plugin = plugin; }

    public void load() {
        config = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "modules/notifications.yml"));
        mentionCooldowns.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        if (!config.getBoolean("world-warning.enabled", true)) return;
        Player player = event.getPlayer();
        String world = player.getWorld().getName();
        if (!config.getStringList("world-warning.worlds").stream().anyMatch(name -> name.equalsIgnoreCase(world))) return;
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> warnWorld(player), 5L);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> warnWorld(event.getPlayer()), 35L);
    }

    private void warnWorld(Player player) {
        if (!player.isOnline() || !config.getBoolean("world-warning.enabled", true)) return;
        String world = player.getWorld().getName();
        if (!config.getStringList("world-warning.worlds").stream().anyMatch(name -> name.equalsIgnoreCase(world))) return;
        player.sendTitle(color(config.getString("world-warning.title", "&cHATI-HATI")),
                color(config.getString("world-warning.subtitle", "&fKeepInventory &ctidak aktif &fdi dunia ini.")),
                ticks("world-warning.fade-in", 10), ticks("world-warning.stay", 80), ticks("world-warning.fade-out", 20));
        play(player, config.getString("world-warning.sound", "ENTITY_ENDER_DRAGON_GROWL"), 0.7F, 0.8F);
    }

    @SuppressWarnings("deprecation")
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onChat(AsyncPlayerChatEvent event) {
        if (!config.getBoolean("mentions.enabled", true)) return;
        UUID senderId = event.getPlayer().getUniqueId();
        String message = event.getMessage().toLowerCase(Locale.ROOT);
        plugin.getServer().getScheduler().runTask(plugin, () -> processMentions(senderId, message));
    }

    private void processMentions(UUID senderId, String message) {
        Player sender = plugin.getServer().getPlayer(senderId);
        if (sender == null || !sender.isOnline()) return;
        boolean requireAt = config.getBoolean("mentions.require-at", true);
        long cooldown = Math.max(0L, config.getLong("mentions.cooldown-seconds", 10L)) * 1000L;
        long now = System.currentTimeMillis();
        for (Player target : plugin.getServer().getOnlinePlayers()) {
            if (target.getUniqueId().equals(sender.getUniqueId())) continue;
            String name = target.getName().toLowerCase(Locale.ROOT);
            String token = (requireAt ? "@" : "") + name;
            if (!containsToken(message, token)) continue;
            String key = sender.getUniqueId() + ":" + target.getUniqueId();
            if (mentionCooldowns.getOrDefault(key, 0L) > now) continue;
            mentionCooldowns.put(key, now + cooldown);
            notifyMention(sender, target);
        }
    }

    private boolean containsToken(String message, String token) {
        int index = message.indexOf(token);
        while (index >= 0) {
            int end = index + token.length();
            boolean before = index == 0 || !isUsernameCharacter(message.charAt(index - 1));
            boolean after = end >= message.length() || !isUsernameCharacter(message.charAt(end));
            if (before && after) return true;
            index = message.indexOf(token, index + 1);
        }
        return false;
    }

    private boolean isUsernameCharacter(char character) {
        return Character.isLetterOrDigit(character) || character == '_';
    }

    private void notifyMention(Player sender, Player target) {
        if (!target.isOnline()) return;
        String title = config.getString("mentions.title", "&bKAMU DI-MENTION");
        String subtitle = config.getString("mentions.subtitle", "&f%player% &7menyebut namamu di chat.")
                .replace("%player%", sender.getName());
        target.sendTitle(color(title), color(subtitle), ticks("mentions.fade-in", 5), ticks("mentions.stay", 50), ticks("mentions.fade-out", 15));
        play(target, config.getString("mentions.sound", "BLOCK_NOTE_BLOCK_BELL"), 0.9F, 1.25F);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPayCommand(PlayerCommandPreprocessEvent event) {
        if (!config.getBoolean("pay-notifications.enabled", true)) return;
        String[] parts = event.getMessage().trim().split("\\s+");
        if (parts.length < 3) return;
        String command = parts[0].startsWith("/") ? parts[0].substring(1).toLowerCase(Locale.ROOT) : parts[0].toLowerCase(Locale.ROOT);
        if (!config.getStringList("pay-notifications.commands").stream().anyMatch(value -> value.equalsIgnoreCase(command))) return;
        Player recipient = Bukkit.getPlayerExact(parts[1]);
        if (recipient == null || recipient.getUniqueId().equals(event.getPlayer().getUniqueId())) return;
        double previousBalance = vaultBalance(recipient);
        if (Double.isNaN(previousBalance)) return;
        UUID senderId = event.getPlayer().getUniqueId();
        UUID recipientId = recipient.getUniqueId();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            Player sender = Bukkit.getPlayer(senderId);
            Player target = Bukkit.getPlayer(recipientId);
            if (sender == null || target == null || !target.isOnline()) return;
            double received = vaultBalance(target) - previousBalance;
            if (received <= 0.0001D) return;
            notifyPayment(sender, target, received);
        }, 2L);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private double vaultBalance(Player player) {
        try {
            Class<?> economyClass = Class.forName("net.milkbowl.vault.economy.Economy");
            org.bukkit.plugin.RegisteredServiceProvider<?> registration = plugin.getServer().getServicesManager().getRegistration((Class) economyClass);
            if (registration == null || registration.getProvider() == null) return Double.NaN;
            Object balance = economyClass.getMethod("getBalance", OfflinePlayer.class).invoke(registration.getProvider(), player);
            return balance instanceof Number number ? number.doubleValue() : Double.NaN;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return Double.NaN;
        }
    }

    private void notifyPayment(Player sender, Player target, double amount) {
        String title = config.getString("pay-notifications.title", "&aGIFT MASUK");
        String subtitle = config.getString("pay-notifications.subtitle", "&f%sender% &7mengirim &a$%amount%")
                .replace("%sender%", sender.getName())
                .replace("%amount%", MONEY_FORMAT.format(amount));
        target.sendTitle(color(title), color(subtitle), ticks("pay-notifications.fade-in", 5), ticks("pay-notifications.stay", 50), ticks("pay-notifications.fade-out", 15));
        play(target, config.getString("pay-notifications.sound", "ENTITY_EXPERIENCE_ORB_PICKUP"), 0.9F, 1.15F);
    }

    private int ticks(String path, int fallback) { return Math.max(0, config.getInt(path, fallback)); }
    private String color(String text) { return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text); }
    private void play(Player player, String name, float volume, float pitch) {
        try { player.playSound(player.getLocation(), Sound.valueOf(name.toUpperCase(Locale.ROOT)), volume, pitch); }
        catch (IllegalArgumentException ignored) { }
    }
}
