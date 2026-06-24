package id.velioragardens.veliorasuite.module.report.model;

public enum ReportPriority {
    LOW,
    NORMAL,
    HIGH;

    public static ReportPriority fromString(String value) {
        if (value == null || value.isBlank()) {
            return NORMAL;
        }

        try {
            return ReportPriority.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            return NORMAL;
        }
    }
}
