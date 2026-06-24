package id.velioragardens.veliorasuite.module.report;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class ReportsCommand implements CommandExecutor, TabCompleter {

    private final ReportManager reportManager;

    public ReportsCommand(ReportManager reportManager) {
        this.reportManager = reportManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            if (!reportManager.hasStaffPermission(sender)) {
                reportManager.sendNoPermission(sender);
                return true;
            }
            reportManager.sendStaffHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase(Locale.ROOT);

        switch (subCommand) {
            case "list" -> {
                if (!reportManager.hasStaffPermission(sender)) {
                    reportManager.sendNoPermission(sender);
                    return true;
                }
                reportManager.sendOpenReports(sender);
                return true;
            }

            case "view" -> {
                if (!reportManager.hasStaffPermission(sender)) {
                    reportManager.sendNoPermission(sender);
                    return true;
                }
                if (args.length < 2) {
                    reportManager.sendInvalidUsageStaff(sender);
                    return true;
                }
                reportManager.viewReport(sender, parseId(args[1]));
                return true;
            }

            case "close" -> {
                if (!reportManager.hasStaffPermission(sender)) {
                    reportManager.sendNoPermission(sender);
                    return true;
                }
                if (args.length < 3) {
                    reportManager.sendInvalidUsageStaff(sender);
                    return true;
                }
                reportManager.closeReport(sender, parseId(args[1]), join(args, 2));
                return true;
            }

            case "reopen" -> {
                if (!reportManager.hasStaffPermission(sender)) {
                    reportManager.sendNoPermission(sender);
                    return true;
                }
                if (args.length < 2) {
                    reportManager.sendInvalidUsageStaff(sender);
                    return true;
                }
                reportManager.reopenReport(sender, parseId(args[1]));
                return true;
            }

            case "reload" -> {
                if (!reportManager.hasReloadPermission(sender)) {
                    reportManager.sendNoPermission(sender);
                    return true;
                }
                reportManager.reload();
                reportManager.sendReloadSuccess(sender);
                return true;
            }

            default -> {
                if (!reportManager.hasStaffPermission(sender)) {
                    reportManager.sendNoPermission(sender);
                    return true;
                }
                reportManager.sendStaffHelp(sender);
                return true;
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>();

            if (reportManager.hasStaffPermission(sender)) {
                options.addAll(Arrays.asList("help", "list", "view", "close", "reopen"));
            }

            if (reportManager.hasReloadPermission(sender)) {
                options.add("reload");
            }

            return filter(options, args[0]);
        }

        if (args.length == 2 && Arrays.asList("view", "close", "reopen").contains(args[0].toLowerCase(Locale.ROOT)) && reportManager.hasStaffPermission(sender)) {
            return filter(reportManager.getDataManager().getReportIdSuggestions(), args[1]);
        }

        return new ArrayList<>();
    }

    private int parseId(String input) {
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    private String join(String[] args, int startIndex) {
        StringBuilder builder = new StringBuilder();

        for (int i = startIndex; i < args.length; i++) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(args[i]);
        }

        return builder.toString();
    }

    private List<String> filter(List<String> options, String input) {
        List<String> result = new ArrayList<>();
        String lowerInput = input.toLowerCase(Locale.ROOT);

        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lowerInput)) {
                result.add(option);
            }
        }

        return result;
    }
}
