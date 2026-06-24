package id.velioragardens.veliorasuite.module.report;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.module.report.model.Report;
import id.velioragardens.veliorasuite.module.report.model.ReportStatus;
import id.velioragardens.veliorasuite.module.report.model.ReportType;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ReportManager {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final VelioraSuite plugin;
    private final ReportConfigManager configManager;
    private final ReportDataManager dataManager;
    private final ReportCooldownManager cooldownManager;
    private final ReportNotificationManager notificationManager;

    public ReportManager(VelioraSuite plugin) {
        this.plugin = plugin;
        this.configManager = new ReportConfigManager(plugin);
        this.dataManager = new ReportDataManager(plugin);
        this.cooldownManager = new ReportCooldownManager();
        this.notificationManager = new ReportNotificationManager(configManager);
    }

    public void load() {
        configManager.load();
        dataManager.load();
        plugin.getLogger().info("VelioraReport loaded.");
    }

    public void reload() {
        configManager.load();
    }

    public void shutdown() {
        cooldownManager.clear();
    }

    public ReportConfigManager getConfigManager() {
        return configManager;
    }

    public ReportDataManager getDataManager() {
        return dataManager;
    }

    public boolean hasUsePermission(CommandSender sender) {
        return sender.hasPermission(configManager.getUsePermission()) || hasStaffPermission(sender);
    }

    public boolean hasStaffPermission(CommandSender sender) {
        return sender.hasPermission(configManager.getStaffPermission()) || hasAdminPermission(sender);
    }

    public boolean hasAdminPermission(CommandSender sender) {
        return sender.hasPermission(configManager.getAdminPermission());
    }

    public boolean hasReloadPermission(CommandSender sender) {
        return sender.hasPermission(configManager.getReloadPermission()) || hasAdminPermission(sender);
    }

    public void sendPlayerHelp(CommandSender sender) {
        sendLines(sender, configManager.getMessageList("help-player", List.of(
                "&8&m--------------------------------",
                "&c&lVelioraReport",
                "&f/report <player> <alasan> &7- Laporkan player.",
                "&f/report bug <alasan> &7- Laporkan bug.",
                "&8&m--------------------------------"
        )), Map.of());
    }

    public void sendStaffHelp(CommandSender sender) {
        sendLines(sender, configManager.getMessageList("help-staff", List.of(
                "&8&m--------------------------------",
                "&c&lVelioraReport Staff",
                "&f/reports list &7- Lihat report terbuka.",
                "&f/reports view <id> &7- Lihat detail report.",
                "&f/reports close <id> <catatan> &7- Tutup report.",
                "&f/reports reopen <id> &7- Buka ulang report.",
                "&f/reports reload &7- Reload config.",
                "&8&m--------------------------------"
        )), Map.of());
    }

    public void createPlayerReport(Player reporter, Player target, String reason) {
        if (!validateReportBase(reporter, reason)) {
            return;
        }

        if (target == null) {
            send(reporter, "target-not-found", "%prefix% &cPlayer &f%target% &ctidak ditemukan.", Map.of("%target%", "-"));
            return;
        }

        if (configManager.isBlockSelfReport() && reporter.getUniqueId().equals(target.getUniqueId())) {
            send(reporter, "cannot-report-self", "%prefix% &cKamu tidak bisa melaporkan diri sendiri.", Map.of());
            return;
        }

        Report report = createReport(reporter, ReportType.PLAYER, target.getUniqueId(), target.getName(), reason);
        finishCreateReport(reporter, report);
    }

    public void createBugReport(Player reporter, String reason) {
        if (!validateReportBase(reporter, reason)) {
            return;
        }

        Report report = createReport(reporter, ReportType.BUG, null, "", reason);
        finishCreateReport(reporter, report);
    }

    public void sendOpenReports(CommandSender sender) {
        List<Report> reports = dataManager.getOpenReports();

        if (reports.isEmpty()) {
            send(sender, "no-open-reports", "%prefix% &aTidak ada report terbuka.", Map.of());
            return;
        }

        sendLines(sender, configManager.getFormatList("list-header", List.of(
                "&8&m--------------------------------",
                "&c&lReport Terbuka"
        )), Map.of());

        String format = configManager.getFormat("list-format", "&7#%id% &8| &f%type% &8| &f%reporter% &7-> &f%target% &8| &c%reason%");
        for (Report report : reports) {
            sender.sendMessage(configManager.color(ReportPlaceholders.apply(format, ReportPlaceholders.of(report))));
        }

        sendLines(sender, configManager.getFormatList("list-footer", List.of("&8&m--------------------------------")), Map.of());
    }

    public void viewReport(CommandSender sender, int id) {
        Report report = dataManager.getReport(id);

        if (report == null) {
            send(sender, "report-not-found", "%prefix% &cReport ID &f#%id% &ctidak ditemukan.", Map.of("%id%", String.valueOf(id)));
            return;
        }

        sendLines(sender, configManager.getFormatList("view", List.of(
                "&8&m--------------------------------",
                "&c&lDetail Report #%id%",
                "&7Status: &f%status%",
                "&7Type: &f%type%",
                "&7Reporter: &f%reporter%",
                "&7Target: &f%target%",
                "&7World: &f%world%",
                "&7Lokasi: &f%x%, %y%, %z%",
                "&7Alasan: &f%reason%",
                "&7Dibuat: &f%created%",
                "&7Ditutup oleh: &f%closed_by%",
                "&7Catatan close: &f%close_note%",
                "&8&m--------------------------------"
        )), ReportPlaceholders.of(report));
    }

    public void closeReport(CommandSender sender, int id, String note) {
        Report report = dataManager.getReport(id);

        if (report == null) {
            send(sender, "report-not-found", "%prefix% &cReport ID &f#%id% &ctidak ditemukan.", Map.of("%id%", String.valueOf(id)));
            return;
        }

        report.setStatus(ReportStatus.CLOSED);
        report.setClosedAt(now());
        report.setClosedBy(sender.getName());
        report.setCloseNote(note == null || note.isBlank() ? "-" : note);
        dataManager.saveReport(report);
        send(sender, "report-closed", "%prefix% &aReport &f#%id% &aberhasil ditutup.", Map.of("%id%", String.valueOf(id)));
    }

    public void reopenReport(CommandSender sender, int id) {
        Report report = dataManager.getReport(id);

        if (report == null) {
            send(sender, "report-not-found", "%prefix% &cReport ID &f#%id% &ctidak ditemukan.", Map.of("%id%", String.valueOf(id)));
            return;
        }

        report.setStatus(ReportStatus.OPEN);
        report.setClosedAt("");
        report.setClosedBy("");
        report.setCloseNote("");
        dataManager.saveReport(report);
        send(sender, "report-reopened", "%prefix% &aReport &f#%id% &aberhasil dibuka kembali.", Map.of("%id%", String.valueOf(id)));
    }

    public void sendReloadSuccess(CommandSender sender) {
        send(sender, "reload-success", "%prefix% &aVelioraReport berhasil direload.", Map.of());
    }

    public void sendNoPermission(CommandSender sender) {
        send(sender, "no-permission", "%prefix% &cKamu tidak punya izin.", Map.of());
    }

    public void sendPlayerOnly(CommandSender sender) {
        send(sender, "player-only", "%prefix% &cCommand ini hanya bisa digunakan oleh player.", Map.of());
    }

    public void sendInvalidUsagePlayer(CommandSender sender) {
        send(sender, "invalid-usage-player", "%prefix% &cGunakan: &f/report <player> <alasan>", Map.of());
    }

    public void sendInvalidUsageBug(CommandSender sender) {
        send(sender, "invalid-usage-bug", "%prefix% &cGunakan: &f/report bug <alasan>", Map.of());
    }

    public void sendInvalidUsageStaff(CommandSender sender) {
        send(sender, "invalid-usage-staff", "%prefix% &cGunakan: &f/reports <list|view|close|reopen|reload>", Map.of());
    }

    private boolean validateReportBase(Player reporter, String reason) {
        if (!configManager.isEnabled()) {
            send(reporter, "module-disabled", "%prefix% &cVelioraReport sedang dimatikan.", Map.of());
            return false;
        }

        String safeReason = reason == null ? "" : reason.trim();

        if (safeReason.length() < configManager.getMinReasonLength()) {
            send(reporter, "reason-too-short", "%prefix% &cAlasan terlalu pendek. Minimal &f%min% &ckarakter.", Map.of("%min%", String.valueOf(configManager.getMinReasonLength())));
            return false;
        }

        if (safeReason.length() > configManager.getMaxReasonLength()) {
            send(reporter, "reason-too-long", "%prefix% &cAlasan terlalu panjang. Maksimal &f%max% &ckarakter.", Map.of("%max%", String.valueOf(configManager.getMaxReasonLength())));
            return false;
        }

        if (!reporter.hasPermission(configManager.getBypassCooldownPermission()) && !reporter.hasPermission(configManager.getAdminPermission()) && cooldownManager.isOnCooldown(reporter.getUniqueId())) {
            String time = cooldownManager.formatTime(cooldownManager.getRemainingMillis(reporter.getUniqueId()));
            send(reporter, "on-cooldown", "%prefix% &cTunggu &f%time% &csebelum membuat report lagi.", Map.of("%time%", time));
            return false;
        }

        return true;
    }

    private Report createReport(Player reporter, ReportType type, UUID targetUuid, String targetName, String reason) {
        int id = dataManager.nextId();
        Location location = reporter.getLocation();
        boolean saveLocation = configManager.isSaveLocation();

        return new Report(
                id,
                type,
                ReportStatus.OPEN,
                reporter.getUniqueId(),
                reporter.getName(),
                targetUuid,
                targetName,
                reason.trim(),
                saveLocation ? location.getWorld().getName() : "-",
                saveLocation ? location.getBlockX() : 0,
                saveLocation ? location.getBlockY() : 0,
                saveLocation ? location.getBlockZ() : 0,
                now(),
                "",
                "",
                ""
        );
    }

    private void finishCreateReport(Player reporter, Report report) {
        dataManager.saveReport(report);
        cooldownManager.setCooldown(reporter.getUniqueId(), configManager.getCooldownMillis());
        notificationManager.notifyStaff(report);
        send(reporter, "report-created", "%prefix% &aReport berhasil dibuat dengan ID &f#%id%&a.", Map.of("%id%", String.valueOf(report.getId())));
    }

    private void send(CommandSender sender, String path, String fallback, Map<String, String> placeholders) {
        String message = configManager.getMessage(path, fallback);
        sender.sendMessage(configManager.color(ReportPlaceholders.apply(message, placeholders)));
    }

    private void sendLines(CommandSender sender, List<String> lines, Map<String, String> placeholders) {
        for (String line : lines) {
            sender.sendMessage(configManager.color(ReportPlaceholders.apply(line, placeholders)));
        }
    }

    private String now() {
        return LocalDateTime.now().format(TIME_FORMATTER);
    }
}
