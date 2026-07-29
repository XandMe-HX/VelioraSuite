package id.velioragardens.veliorasuite.module.report;

import id.velioragardens.veliorasuite.module.report.model.Report;

import java.util.HashMap;
import java.util.Map;

public final class ReportPlaceholders {

    private ReportPlaceholders() {
    }

    public static Map<String, String> of(Report report) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("%id%", String.valueOf(report.getId()));
        placeholders.put("%type%", report.getType().name());
        placeholders.put("%status%", report.getStatus().name());
        placeholders.put("%reporter%", report.getReporterName());
        placeholders.put("%target%", report.getDisplayTarget());
        placeholders.put("%reason%", report.getReason());
        placeholders.put("%world%", report.getWorld());
        placeholders.put("%x%", String.valueOf(report.getX()));
        placeholders.put("%y%", String.valueOf(report.getY()));
        placeholders.put("%z%", String.valueOf(report.getZ()));
        placeholders.put("%created%", report.getCreatedAt());
        placeholders.put("%closed_at%", report.getClosedAt());
        placeholders.put("%closed_by%", report.getClosedBy());
        placeholders.put("%close_note%", report.getCloseNote());
        return placeholders;
    }

    public static String apply(String text, Map<String, String> placeholders) {
        String result = text == null ? "" : text;

        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }

        return result;
    }
}
