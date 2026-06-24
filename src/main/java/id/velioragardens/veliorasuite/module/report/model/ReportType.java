package id.velioragardens.veliorasuite.module.report.model;

public enum ReportType {
    PLAYER,
    BUG,
    OTHER;

    public static ReportType fromString(String value) {
        if (value == null || value.isBlank()) {
            return OTHER;
        }

        try {
            return ReportType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            return OTHER;
        }
    }
}
