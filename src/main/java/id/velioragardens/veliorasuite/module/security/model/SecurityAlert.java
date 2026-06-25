package id.velioragardens.veliorasuite.module.security.model;

public record SecurityAlert(
        String type,
        String player,
        int risk,
        String reason,
        String action,
        long timestamp
) {
}
