package id.velioragardens.veliorasuite.module.report;

import id.velioragardens.veliorasuite.util.ColorUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;

public final class PlayerReportCommand implements CommandExecutor {
    private final ReportManager reportManager;
    private final boolean bug;
    public PlayerReportCommand(ReportManager reportManager, boolean bug) { this.reportManager = reportManager; this.bug = bug; }
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Command ini hanya untuk player."); return true; }
        if (bug) {
            if (args.length < 1) { player.sendMessage(ColorUtil.color(reportManager.msg("usage-bugreport"))); return true; }
            int id = reportManager.createBugReport(player, String.join(" ", args));
            player.sendMessage(ColorUtil.color(reportManager.msg("bug-success").replace("%id%", String.valueOf(id))));
        } else {
            if (args.length < 2) { player.sendMessage(ColorUtil.color(reportManager.msg("usage-report"))); return true; }
            String reason = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
            int id = reportManager.createPlayerReport(player, args[0], reason);
            player.sendMessage(ColorUtil.color(reportManager.msg("report-success").replace("%id%", String.valueOf(id))));
        }
        return true;
    }
}
