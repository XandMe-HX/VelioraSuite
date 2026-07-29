package id.velioragardens.veliorasuite.module.security.model;

public record SecurityDecision(
        boolean blocked,
        boolean alert,
        String type,
        String player,
        int risk,
        String reason,
        String action,
        String messageKey,
        String fallbackMessage
) {
    public static SecurityDecision allow() {
        return new SecurityDecision(false, false, "", "", 0, "", "ALLOW", "", "");
    }
}
