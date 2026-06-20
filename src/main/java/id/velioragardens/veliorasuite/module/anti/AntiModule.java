package id.velioragardens.veliorasuite.module.anti;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.module.AbstractModule;
import id.velioragardens.veliorasuite.util.ColorUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class AntiModule extends AbstractModule implements Listener, CommandExecutor, TabCompleter {

    private final Map<UUID, Long> lastChat = new HashMap<>();
    private final Map<UUID, String> lastMessage = new HashMap<>();
    private final Map<UUID, Long> lastRepeated = new HashMap<>();
    private final Map<String, Long> commandCooldown = new HashMap<>();
    private final Map<String, Long> joinIpTime = new HashMap<>();
    private final Map<String, Integer> joinIpCount = new HashMap<>();

    public AntiModule(VelioraSuite plugin) { super(plugin, "anti", "anti"); }

    @Override protected void onEnable() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        PluginCommand command = plugin.getCommand("vanti");
        if (command != null) { command.setExecutor(this); command.setTabCompleter(this); }
        plugin.getLogger().info("VelioraAnti module started.");
    }

    @Override protected void onDisable() {
        HandlerList.unregisterAll(this);
        lastChat.clear(); lastMessage.clear(); lastRepeated.clear(); commandCooldown.clear();
        plugin.getLogger().info("VelioraAnti module stopped.");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("veliorasuite.anti.bypass")) return;
        FileConfiguration cfg = configFile.get();
        String msg = event.getMessage();

        if (cfg.getBoolean("chat-filter.enabled", true)) {
            for (String blocked : cfg.getStringList("chat-filter.blocked-words")) {
                if (!blocked.isBlank() && msg.toLowerCase(Locale.ROOT).contains(blocked.toLowerCase(Locale.ROOT))) {
                    event.setCancelled(true); send(player, "blocked-word"); return;
                }
            }
        }
        if (cfg.getBoolean("anti-spam.enabled", true)) {
            long now = System.currentTimeMillis();
            long cooldown = cfg.getLong("anti-spam.chat-cooldown-seconds", 2) * 1000L;
            long last = lastChat.getOrDefault(player.getUniqueId(), 0L);
            if (now - last < cooldown) { event.setCancelled(true); send(player, "spam"); return; }
            lastChat.put(player.getUniqueId(), now);
        }
        if (cfg.getBoolean("anti-repeated.enabled", true)) {
            String previous = lastMessage.get(player.getUniqueId());
            if (previous != null && previous.equalsIgnoreCase(msg)) {
                long now = System.currentTimeMillis();
                long repeatedCooldown = cfg.getLong("anti-repeated.cooldown-seconds", 10) * 1000L;
                if (now - lastRepeated.getOrDefault(player.getUniqueId(), 0L) < repeatedCooldown) {
                    event.setCancelled(true); send(player, "repeated"); return;
                }
                lastRepeated.put(player.getUniqueId(), now);
            }
            lastMessage.put(player.getUniqueId(), msg);
        }
        if (cfg.getBoolean("anti-caps.enabled", false) && msg.length() >= cfg.getInt("anti-caps.min-length", 8)) {
            int letters = 0, caps = 0;
            for (char c : msg.toCharArray()) if (Character.isLetter(c)) { letters++; if (Character.isUpperCase(c)) caps++; }
            int percent = letters == 0 ? 0 : (caps * 100 / letters);
            if (percent >= cfg.getInt("anti-caps.max-caps-percent", 80)) {
                event.setMessage(msg.toLowerCase(Locale.ROOT));
                send(player, "caps");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("veliorasuite.anti.bypass")) return;
        FileConfiguration cfg = configFile.get();
        if (!cfg.getBoolean("anti-command-spam.enabled", true)) return;
        String base = event.getMessage().replaceFirst("^/", "").split(" ")[0].toLowerCase(Locale.ROOT);
        int seconds = cfg.getInt("anti-command-spam.commands." + base, cfg.getInt("anti-command-spam.default-cooldown-seconds", 3));
        if (seconds <= 0) return;
        String key = player.getUniqueId() + ":" + base;
        long now = System.currentTimeMillis();
        long last = commandCooldown.getOrDefault(key, 0L);
        if (now - last < seconds * 1000L) {
            event.setCancelled(true);
            send(player, "command-cooldown");
            return;
        }
        commandCooldown.put(key, now);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (!configFile.get().getBoolean("anti-bot.enabled", true)) return;
        String ip = event.getPlayer().getAddress() == null ? "unknown" : event.getPlayer().getAddress().getAddress().getHostAddress();
        long now = System.currentTimeMillis();
        long window = configFile.get().getLong("anti-bot.window-seconds", 10) * 1000L;
        if (now - joinIpTime.getOrDefault(ip, 0L) > window) joinIpCount.put(ip, 0);
        joinIpTime.put(ip, now);
        int count = joinIpCount.getOrDefault(ip, 0) + 1;
        joinIpCount.put(ip, count);
        int max = configFile.get().getInt("anti-bot.max-joins-same-ip", 3);
        if (count > max) {
            event.getPlayer().kickPlayer(ColorUtil.color(configFile.get().getString("messages.bot-kick", "&cToo many joins from your IP. Try again later.")));
        }
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("veliorasuite.anti.admin")) { sender.sendMessage(ColorUtil.color(msg("no-permission"))); return true; }
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) { configFile.reload(); sender.sendMessage(ColorUtil.color(msg("reload"))); return true; }
        sender.sendMessage(ColorUtil.color("&8【&aVelioraAnti&8】 &fStatus: &aACTIVE"));
        sender.sendMessage(ColorUtil.color("&7Chat cooldown cache: &f" + lastChat.size()));
        sender.sendMessage(ColorUtil.color("&7Command cooldown cache: &f" + commandCooldown.size()));
        return true;
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("status", "reload").stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        return List.of();
    }

    private void send(Player player, String key) { player.sendMessage(ColorUtil.color(msg(key))); }
    private String msg(String key) { return configFile.get().getString("messages." + key, "&8【&aVelioraAnti&8】 &cMessage not found: " + key); }
}
