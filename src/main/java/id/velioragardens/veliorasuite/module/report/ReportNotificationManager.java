package id.velioragardens.veliorasuite.module.report;

import id.velioragardens.veliorasuite.module.report.model.Report;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

public final class ReportNotificationManager {

    private final ReportConfigManager configManager;

    public ReportNotificationManager(ReportConfigManager configManager) {
        this.configManager = configManager;
    }

    public void notifyStaff(Report report) {
        if (!configManager.isNotifyStaff() || report == null) {
            return;
        }

        List<String> lines = configManager.getFormatList("staff-notify", List.of(
                "&8&m--------------------------------",
                "&c&lReport Baru",
                "&7ID: &f#%id%",
                "&7Type: &f%type%",
                "&7Reporter: &f%reporter%",
                "&7Target: &f%target%",
                "&7Alasan: &f%reason%",
                "&7Gunakan: &f/reports view %id%",
                "&8&m--------------------------------"
        ));

        Map<String, String> placeholders = ReportPlaceholders.of(report);

        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (!canReceive(staff)) {
                continue;
            }

            for (String line : lines) {
                staff.sendMessage(configManager.color(ReportPlaceholders.apply(line, placeholders)));
            }
        }
    }

    private boolean canReceive(Player player) {
        if (player == null) {
            return false;
        }
        if (configManager.isNotifyOpOnly()) {
            return player.isOp();
        }
        return player.hasPermission(configManager.getStaffPermission()) || player.hasPermission(configManager.getAdminPermission());
    }
}
