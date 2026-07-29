package id.velioragardens.veliorasuite.module.security.model;

/**
 * Metadata untuk ban yang benar-benar dibuat oleh VelioraSuite.
 */
public record VelioraBanRecord(
        String playerName,
        String reason,
        BanSource source,
        long timestamp,
        boolean isPermanent,
        long expiresAt
) {
    public boolean isValid() {
        return playerName != null
                && !playerName.isBlank()
                && source != null
                && source.isInternalAuto()
                && !isExpired();
    }

    public boolean isExpired() {
        return !isPermanent && expiresAt > 0L && System.currentTimeMillis() > expiresAt;
    }
}
