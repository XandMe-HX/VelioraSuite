package id.velioragardens.veliorasuite.module.report;

import id.velioragardens.veliorasuite.util.ColorUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class VReportCommand implements CommandExecutor, TabCompleter {
    private final ReportManager reportManager;
    public VReportCommand(ReportManager reportManager) { this.reportManager = reportManager; }
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("veliorasuite.report.admin")) { sender.sendMessage(ColorUtil.color(reportManager.msg("no-permission"))); return true; }
        if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
            sender.sendMessage(ColorUtil.color("&8&m------------------------------"));
            sender.sendMessage(ColorUtil.color("&aOpen Reports"));
            List<Integer> ids = reportManager.openReports();
            if (ids.isEmpty()) sender.sendMessage(ColorUtil.color("&7Tidak ada report terbuka."));
            for (Integer id : ids) sender.sendMessage(ColorUtil.color("&7- &f#" + id + " &7gunakan &e/vreport info " + id));
            sender.sendMessage(ColorUtil.color("&8&m------------------------------"));
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "info" -> { if (args.length < 2) return usage(sender, "/vreport info <id>"); reportManager.sendInfo(sender, parse(args[1])); }
            case "close" -> { if (args.length < 2) return usage(sender, "/vreport close <id>"); boolean ok = reportManager.close(sender, parse(args[1])); sender.sendMessage(ColorUtil.color(ok ? reportManager.msg("closed").replace("%id%", args[1]) : reportManager.msg("not-found").replace("%id%", args[1]))); }
            case "banip" -> { if (!sender.hasPermission("veliorasuite.report.banip")) { sender.sendMessage(ColorUtil.color(reportManager.msg("no-permission"))); return true; } if (args.length < 3) return usage(sender, "/vreport banip <id> <alasan>"); String reason = String.join(" ", Arrays.copyOfRange(args, 2, args.length)); boolean ok = reportManager.banIp(sender, parse(args[1]), reason); sender.sendMessage(ColorUtil.color(ok ? reportManager.msg("banip-success").replace("%id%", args[1]) : reportManager.msg("banip-failed").replace("%id%", args[1]))); }
            case "reload" -> { reportManager.reload(); sender.sendMessage(ColorUtil.color(reportManager.msg("reload"))); }
            default -> usage(sender, "/vreport list|info|close|banip|reload");
        }
        return true;
    }
    private boolean usage(CommandSender sender, String usage) { sender.sendMessage(ColorUtil.color("&cGunakan: &f" + usage)); return true; }
    private int parse(String raw) { try { return Integer.parseInt(raw); } catch (NumberFormatException e) { return -1; } }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) { if (args.length == 1) return filter(Arrays.asList("list", "info", "close", "banip", "reload"), args[0]); return new ArrayList<>(); }
    private List<String> filter(List<String> options, String input) { List<String> r = new ArrayList<>(); String lower = input.toLowerCase(Locale.ROOT); for (String o : options) if (o.startsWith(lower)) r.add(o); return r; }
}
