package id.velioragardens.veliorasuite.module.report;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ReportCommand implements CommandExecutor, TabCompleter {

    private final ReportManager reportManager;

    public ReportCommand(ReportManager reportManager) {
        this.reportManager = reportManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            reportManager.sendPlayerOnly(sender);
            return true;
        }

        if (!reportManager.hasUsePermission(sender)) {
            reportManager.sendNoPermission(sender);
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            reportManager.sendPlayerHelp(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("bug")) {
            if (args.length < 2) {
                reportManager.sendInvalidUsageBug(sender);
                return true;
            }

            String reason = join(args, 1);
            reportManager.createBugReport(player, reason);
            return true;
        }

        if (args.length < 2) {
            reportManager.sendInvalidUsagePlayer(sender);
            return true;
        }

        String targetName = args[0];
        Player target = Bukkit.getPlayerExact(targetName);

        if (target == null) {
            sender.sendMessage(reportManager.getConfigManager().color(reportManager.getConfigManager()
                    .getMessage("target-not-found", "%prefix% &cPlayer &f%target% &ctidak ditemukan.")
                    .replace("%target%", targetName)));
            return true;
        }

        String reason = join(args, 1);
        reportManager.createPlayerReport(player, target, reason);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!reportManager.hasUsePermission(sender)) {
            return new ArrayList<>();
        }

        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            options.add("help");
            options.add("bug");

            for (Player player : Bukkit.getOnlinePlayers()) {
                options.add(player.getName());
            }

            return filter(options, args[0]);
        }

        return new ArrayList<>();
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
