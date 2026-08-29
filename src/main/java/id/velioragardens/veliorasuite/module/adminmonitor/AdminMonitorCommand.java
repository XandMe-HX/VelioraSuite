package id.velioragardens.veliorasuite.module.adminmonitor;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

public final class AdminMonitorCommand implements CommandExecutor, TabCompleter {
    private final AdminMonitorManager manager;
    public AdminMonitorCommand(AdminMonitorManager manager) { this.manager = manager; }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) { if (!manager.canView(sender)) manager.sendNoPermission(sender); else if (sender instanceof org.bukkit.entity.Player player) player.performCommand("adminmanager"); else manager.sendHelp(sender); return true; }
        if (args[0].equalsIgnoreCase("help")) { if (!manager.canView(sender)) manager.sendNoPermission(sender); else manager.sendHelp(sender); return true; }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("reload")) { if (!manager.canReload(sender)) manager.sendNoPermission(sender); else { manager.load(); manager.sendReloadSuccess(sender); } return true; }
        if (!manager.canView(sender)) { manager.sendNoPermission(sender); return true; }
        switch (sub) {
            case "online" -> manager.sendOnline(sender);
            case "log" -> {
                if (args.length < 2) manager.sendHelp(sender);
                else { LocalDate date = args.length >= 3 ? manager.parseDate(args[2]) : manager.currentDate(); if (date == null) manager.sendInvalidDate(sender); else manager.sendLog(sender, args[1], date); }
            }
            case "today" -> manager.sendToday(sender, args.length >= 2 ? args[1] : null);
            case "date" -> { if (args.length < 2) manager.sendHelp(sender); else { LocalDate date = manager.parseDate(args[1]); if (date == null) manager.sendInvalidDate(sender); else manager.sendDate(sender, date); } }
            default -> manager.sendHelp(sender);
        }
        return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!manager.canView(sender)) return List.of();
        if (args.length == 1) return List.of("help", "online", "log", "today", "date", "reload").stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        return List.of();
    }
}
