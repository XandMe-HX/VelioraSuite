package id.velioragardens.veliorasuite.module.notifications;

import id.velioragardens.veliorasuite.VelioraSuite;
import org.bukkit.ChatColor;
import org.bukkit.GameRule;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class NotificationListener implements Listener {
    private final VelioraSuite plugin;
    private final Map<String, Long> mentionCooldowns = new HashMap<>();
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
        Boolean keepInventory = player.getWorld().getGameRuleValue(GameRule.KEEP_INVENTORY);
        if (Boolean.TRUE.equals(keepInventory)) return;
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

    private int ticks(String path, int fallback) { return Math.max(0, config.getInt(path, fallback)); }
    private String color(String text) { return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text); }
    private void play(Player player, String name, float volume, float pitch) {
        try { player.playSound(player.getLocation(), Sound.valueOf(name.toUpperCase(Locale.ROOT)), volume, pitch); }
        catch (IllegalArgumentException ignored) { }
    }
}
