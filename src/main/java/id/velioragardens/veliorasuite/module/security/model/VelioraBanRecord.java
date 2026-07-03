package id.velioragardens.veliorasuite.module.security.model;

/**
 * Record untuk menyimpan informasi ban dengan metadata internal VelioraSuite
 * Memastikan VelioraBan hanya memproses ban yang dibuat oleh VelioraSuite
 */
public record VelioraBanRecord(
    String playerName,
    String reason,
    BanSource source,
    long timestamp,
    boolean isPermanent,
    long expiresAt
) {
    /**
     * Cek apakah ban ini valid untuk diproses
     */
    public boolean isValid() {
        return playerName != null && !playerName.isBlank() && 
               source != null && source.isInternal();
    }
    
    /**
     * Cek apakah ban sudah expired (jika temporary)
     */
    public boolean isExpired() {
        if (isPermanent) return false;
        return System.currentTimeMillis() > expiresAt;
    }
}
