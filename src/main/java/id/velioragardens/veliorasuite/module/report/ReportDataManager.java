package id.velioragardens.veliorasuite.module.report;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.module.report.model.Report;
import id.velioragardens.veliorasuite.module.report.model.ReportStatus;
import id.velioragardens.veliorasuite.module.report.model.ReportType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class ReportDataManager {

    private final VelioraSuite plugin;
    private File file;
    private FileConfiguration data;

    public ReportDataManager(VelioraSuite plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.createFolder("data");
        this.file = new File(plugin.getDataFolder(), "data/reports.yml");

        if (!file.exists()) {
            try {
                boolean created = file.createNewFile();
                if (!created) {
                    plugin.getLogger().warning("VelioraReport: reports.yml sudah ada atau gagal dibuat.");
                }
            } catch (IOException exception) {
                plugin.getLogger().severe("VelioraReport: gagal membuat reports.yml: " + exception.getMessage());
            }
        }

        try {
            this.data = YamlConfiguration.loadConfiguration(file);
            if (!data.contains("last-id")) {
                data.set("last-id", 0);
            }
            if (!data.contains("reports")) {
                data.createSection("reports");
            }
            save();
        } catch (Exception exception) {
            plugin.getLogger().severe("VelioraReport: data/reports.yml rusak atau gagal dibaca. Fallback data kosong. Error: " + exception.getMessage());
            this.data = new YamlConfiguration();
            data.set("last-id", 0);
            data.createSection("reports");
        }
    }

    public void save() {
        if (data == null || file == null) {
            return;
        }

        try {
            data.save(file);
        } catch (IOException exception) {
            plugin.getLogger().severe("VelioraReport: gagal menyimpan reports.yml: " + exception.getMessage());
        }
    }

    public int nextId() {
        int nextId = data.getInt("last-id", 0) + 1;
        data.set("last-id", nextId);
        save();
        return nextId;
    }

    public void saveReport(Report report) {
        if (report == null) {
            return;
        }

        String path = "reports." + report.getId();
        data.set(path + ".type", report.getType().name());
        data.set(path + ".status", report.getStatus().name());
        data.set(path + ".reporter-uuid", report.getReporterUuid() == null ? "" : report.getReporterUuid().toString());
        data.set(path + ".reporter-name", report.getReporterName());
        data.set(path + ".target-uuid", report.getTargetUuid() == null ? "" : report.getTargetUuid().toString());
        data.set(path + ".target-name", report.getTargetName());
        data.set(path + ".reason", report.getReason());
        data.set(path + ".world", report.getWorld());
        data.set(path + ".x", report.getX());
        data.set(path + ".y", report.getY());
        data.set(path + ".z", report.getZ());
        data.set(path + ".created-at", report.getCreatedAt());
        data.set(path + ".closed-at", report.getClosedAt().equals("-") ? "" : report.getClosedAt());
        data.set(path + ".closed-by", report.getClosedBy().equals("-") ? "" : report.getClosedBy());
        data.set(path + ".close-note", report.getCloseNote().equals("-") ? "" : report.getCloseNote());
        save();
    }

    public Report getReport(int id) {
        String path = "reports." + id;

        if (!data.contains(path)) {
            return null;
        }

        return readReport(path, id);
    }

    public List<Report> getOpenReports() {
        return getReportsByStatus(ReportStatus.OPEN);
    }

    public List<String> getReportIdSuggestions() {
        List<String> ids = new ArrayList<>();
        ConfigurationSection section = data.getConfigurationSection("reports");

        if (section == null) {
            return ids;
        }

        for (String id : section.getKeys(false)) {
            ids.add(id);
        }

        ids.sort(Comparator.comparingInt(this::safeParseInt));
        return ids;
    }

    private List<Report> getReportsByStatus(ReportStatus status) {
        List<Report> reports = new ArrayList<>();
        ConfigurationSection section = data.getConfigurationSection("reports");

        if (section == null) {
            return reports;
        }

        for (String rawId : section.getKeys(false)) {
            int id = safeParseInt(rawId);
            if (id <= 0) {
                continue;
            }

            Report report = readReport("reports." + rawId, id);
            if (report != null && report.getStatus() == status) {
                reports.add(report);
            }
        }

        reports.sort(Comparator.comparingInt(Report::getId));
        return reports;
    }

    private Report readReport(String path, int id) {
        try {
            return new Report(
                    id,
                    ReportType.fromString(data.getString(path + ".type", "OTHER")),
                    ReportStatus.fromString(data.getString(path + ".status", "OPEN")),
                    parseUuid(data.getString(path + ".reporter-uuid", "")),
                    data.getString(path + ".reporter-name", "Unknown"),
                    parseUuid(data.getString(path + ".target-uuid", "")),
                    data.getString(path + ".target-name", ""),
                    data.getString(path + ".reason", "-"),
                    data.getString(path + ".world", "-"),
                    data.getInt(path + ".x", 0),
                    data.getInt(path + ".y", 0),
                    data.getInt(path + ".z", 0),
                    data.getString(path + ".created-at", "-"),
                    data.getString(path + ".closed-at", ""),
                    data.getString(path + ".closed-by", ""),
                    data.getString(path + ".close-note", "")
            );
        } catch (Exception exception) {
            plugin.getLogger().warning("VelioraReport: gagal membaca report #" + id + ". Report dilewati. Error: " + exception.getMessage());
            return null;
        }
    }

    private UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private int safeParseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return 0;
        }
    }
}
