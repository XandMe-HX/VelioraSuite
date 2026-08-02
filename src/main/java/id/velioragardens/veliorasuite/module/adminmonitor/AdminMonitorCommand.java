package id.velioragardens.veliorasuite.module.adminmonitor;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;
import java.util.Locale;

public final class AdminMonitorCommand implements CommandExecutor, TabCompleter {
    private final AdminMonitorManager manager;
    public AdminMonitorCommand(AdminMonitorManager manager) { this.manager = manager; }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!manager.canAdmin(sender)) { manager.sendNoPermission(sender); return true; }
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) { manager.sendHelp(sender); return true; }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "online" -> manager.sendOnline(sender);
            case "log" -> { if (args.length < 2) manager.sendHelp(sender); else manager.sendLog(sender, args[1]); }
            case "today" -> manager.sendToday(sender);
            case "reload" -> { manager.load(); manager.sendReloadSuccess(sender); }
            default -> manager.sendHelp(sender);
        }
        return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!manager.canAdmin(sender)) return List.of();
        if (args.length == 1) return List.of("help", "online", "log", "today", "reload").stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        return List.of();
    }
}
