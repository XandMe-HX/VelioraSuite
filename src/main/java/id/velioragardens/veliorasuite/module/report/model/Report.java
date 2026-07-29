package id.velioragardens.veliorasuite.module.report.model;

import java.util.UUID;

public final class Report {

    private final int id;
    private final ReportType type;
    private ReportStatus status;
    private final UUID reporterUuid;
    private final String reporterName;
    private final UUID targetUuid;
    private final String targetName;
    private final String reason;
    private final String world;
    private final int x;
    private final int y;
    private final int z;
    private final String createdAt;
    private String closedAt;
    private String closedBy;
    private String closeNote;

    public Report(
            int id,
            ReportType type,
            ReportStatus status,
            UUID reporterUuid,
            String reporterName,
            UUID targetUuid,
            String targetName,
            String reason,
            String world,
            int x,
            int y,
            int z,
            String createdAt,
            String closedAt,
            String closedBy,
            String closeNote
    ) {
        this.id = id;
        this.type = type == null ? ReportType.OTHER : type;
        this.status = status == null ? ReportStatus.OPEN : status;
        this.reporterUuid = reporterUuid;
        this.reporterName = emptyFallback(reporterName, "Unknown");
        this.targetUuid = targetUuid;
        this.targetName = targetName == null ? "" : targetName;
        this.reason = emptyFallback(reason, "-");
        this.world = emptyFallback(world, "-");
        this.x = x;
        this.y = y;
        this.z = z;
        this.createdAt = emptyFallback(createdAt, "-");
        this.closedAt = closedAt == null ? "" : closedAt;
        this.closedBy = closedBy == null ? "" : closedBy;
        this.closeNote = closeNote == null ? "" : closeNote;
    }

    public int getId() {
        return id;
    }

    public ReportType getType() {
        return type;
    }

    public ReportStatus getStatus() {
        return status;
    }

    public void setStatus(ReportStatus status) {
        this.status = status == null ? ReportStatus.OPEN : status;
    }

    public UUID getReporterUuid() {
        return reporterUuid;
    }

    public String getReporterName() {
        return reporterName;
    }

    public UUID getTargetUuid() {
        return targetUuid;
    }

    public String getTargetName() {
        return targetName;
    }

    public String getDisplayTarget() {
        return targetName == null || targetName.isBlank() ? "-" : targetName;
    }

    public String getReason() {
        return reason;
    }

    public String getWorld() {
        return world;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public String getClosedAt() {
        return closedAt == null || closedAt.isBlank() ? "-" : closedAt;
    }

    public void setClosedAt(String closedAt) {
        this.closedAt = closedAt == null ? "" : closedAt;
    }

    public String getClosedBy() {
        return closedBy == null || closedBy.isBlank() ? "-" : closedBy;
    }

    public void setClosedBy(String closedBy) {
        this.closedBy = closedBy == null ? "" : closedBy;
    }

    public String getCloseNote() {
        return closeNote == null || closeNote.isBlank() ? "-" : closeNote;
    }

    public void setCloseNote(String closeNote) {
        this.closeNote = closeNote == null ? "" : closeNote;
    }

    private String emptyFallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
