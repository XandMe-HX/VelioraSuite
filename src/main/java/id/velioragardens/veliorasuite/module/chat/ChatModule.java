package id.velioragardens.veliorasuite.module.chat;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.module.AbstractModule;
import id.velioragardens.veliorasuite.module.Module;
import id.velioragardens.veliorasuite.module.team.TeamModule;
import id.velioragardens.veliorasuite.util.ColorUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ChatModule extends AbstractModule implements Listener, CommandExecutor, TabCompleter {
    public ChatModule(VelioraSuite plugin) { super(plugin, "chat", "chat"); }
    @Override protected void onEnable() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        PluginCommand c = plugin.getCommand("vchat"); if (c != null) { c.setExecutor(this); c.setTabCompleter(this); }
        plugin.getLogger().info("VelioraChat module started.");
    }
    @Override protected void onDisable() { HandlerList.unregisterAll(this); plugin.getLogger().info("VelioraChat module stopped."); }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        if (!configFile.get().getBoolean("format.enabled", false)) return;
        Player player = event.getPlayer();
        String format = configFile.get().getString("format.pattern", "%team_prefix%%player% &8: &f%message%");
        String teamPrefix = getTeamPrefix(player);
        String rankPrefix = getLuckPermsPrefix(player);
        format = format.replace("%team_prefix%", teamPrefix)
                .replace("%luckperms_prefix%", rankPrefix)
                .replace("%player%", "%1$s")
                .replace("%message%", "%2$s");
        event.setFormat(ColorUtil.color(format));
    }

    private String getTeamPrefix(Player player) {
        try {
            Module module = plugin.getModuleManager().getModule("team");
            if (module instanceof TeamModule teamModule && teamModule.getTeamManager() != null) return teamModule.getTeamManager().getTeamPrefix(player.getUniqueId());
        } catch (Throwable ignored) { }
        return "";
    }

    private String getLuckPermsPrefix(Player player) {
        // Placeholder ringan. Untuk format rank lengkap, biarkan EssentialsChat/LuckPerms mengatur prefix.
        return configFile.get().getString("format.default-rank-prefix", "");
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("veliorasuite.chat.admin")) { sender.sendMessage(color(msg("no-permission", Map.of()))); return true; }
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) { configFile.reload(); sender.sendMessage(color(msg("reload", Map.of()))); return true; }
        sender.sendMessage(color("&8【&aVelioraChat&8】 &f/vchat reload")); return true;
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) { if(args.length==1) return List.of("reload").stream().filter(s->s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList(); return List.of(); }
    private String msg(String key, Map<String,String> vars) { String s=configFile.get().getString("messages."+key,"&8【&aVelioraChat&8】 &cMessage not found: "+key); for(var e:vars.entrySet()) s=s.replace("%"+e.getKey()+"%",e.getValue()); return s; }
    private String color(String s) { return ColorUtil.color(s); }
}
