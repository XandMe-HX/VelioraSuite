package id.velioragardens.veliorasuite.module.report.model;

public enum ReportStatus {
    OPEN,
    CLOSED;

    public static ReportStatus fromString(String value) {
        if (value == null || value.isBlank()) {
            return OPEN;
        }

        try {
            return ReportStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            return OPEN;
        }
    }
}
