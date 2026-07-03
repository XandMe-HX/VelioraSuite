package id.velioragardens.veliorasuite.module.security;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class SecurityCommand implements CommandExecutor, TabCompleter {

    private final SecurityManager manager;

    public SecurityCommand(SecurityManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String commandName = command.getName().toLowerCase(Locale.ROOT);
        if (commandName.equals("vxray") || label.equalsIgnoreCase("vxray") || label.equalsIgnoreCase("vorewatch") || label.equalsIgnoreCase("orecheck")) {
            return handleOreCommand(sender, args);
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            if (!manager.getConfigManager().hasAdmin(sender)) {
                manager.sendNoPermission(sender);
                return true;
            }
            manager.sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "status" -> {
                if (!manager.getConfigManager().hasAdmin(sender)) {
                    manager.sendNoPermission(sender);
                    return true;
                }
                manager.sendStatus(sender);
                return true;
            }
            case "alerts" -> {
                if (!manager.getConfigManager().hasAlerts(sender)) {
                    manager.sendNoPermission(sender);
                    return true;
                }
                manager.sendAlerts(sender);
                return true;
            }
            case "reload" -> {
                if (!manager.getConfigManager().hasReload(sender)) {
                    manager.sendNoPermission(sender);
                    return true;
                }
                manager.reload();
                manager.sendReloadSuccess(sender);
                return true;
            }
            default -> {
                manager.sendHelp(sender);
                return true;
            }
        }
    }

    private boolean handleOreCommand(CommandSender sender, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            manager.sendOreHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "status" -> manager.sendOreStatus(sender);
            case "alerts" -> manager.sendOreAlerts(sender);
            case "suspects", "top" -> manager.sendOreSuspects(sender);
            case "allreport" -> manager.sendOreAllReport(sender);
            case "check" -> {
                if (args.length < 2) manager.sendOreHelp(sender);
                else manager.sendOreCheck(sender, args[1]);
            }
            case "logs" -> {
                if (args.length < 2) manager.sendOreHelp(sender);
                else manager.sendOreLogs(sender, args[1]);
            }
            case "reset" -> {
                if (args.length < 2) manager.sendOreHelp(sender);
                else manager.resetOre(sender, args[1]);
            }
            case "exempt" -> {
                if (args.length < 2) manager.sendOreHelp(sender);
                else manager.exemptOre(sender, args[1], true);
            }
            case "unexempt" -> {
                if (args.length < 2) manager.sendOreHelp(sender);
                else manager.exemptOre(sender, args[1], false);
            }
            case "reload" -> {
                if (!manager.getConfigManager().hasReload(sender)) manager.sendNoPermission(sender);
                else {
                    manager.reload();
                    manager.sendReloadSuccess(sender);
                }
            }
            default -> manager.sendOreHelp(sender);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String commandName = command.getName().toLowerCase(Locale.ROOT);
        if (commandName.equals("vxray") || alias.equalsIgnoreCase("vxray") || alias.equalsIgnoreCase("vorewatch") || alias.equalsIgnoreCase("orecheck")) {
            if (args.length != 1) return new ArrayList<>();
            if (!manager.getConfigManager().hasAdmin(sender)) return new ArrayList<>();
            return filter(new ArrayList<>(Arrays.asList("help", "status", "check", "logs", "suspects", "top", "alerts", "allreport", "reset", "exempt", "unexempt", "reload")), args[0]);
        }

        if (args.length != 1) return new ArrayList<>();
        List<String> options = new ArrayList<>();
        if (manager.getConfigManager().hasAdmin(sender)) options.addAll(Arrays.asList("help", "status"));
        if (manager.getConfigManager().hasAlerts(sender)) options.add("alerts");
        if (manager.getConfigManager().hasReload(sender)) options.add("reload");
        return filter(options, args[0]);
    }

    private List<String> filter(List<String> options, String input) {
        List<String> result = new ArrayList<>();
        String lowerInput = input.toLowerCase(Locale.ROOT);
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lowerInput)) result.add(option);
        }
        return result;
    }
}
