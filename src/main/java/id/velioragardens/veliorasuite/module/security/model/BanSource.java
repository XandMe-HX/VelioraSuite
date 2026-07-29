package id.velioragardens.veliorasuite.module.security.model;

/**
 * Hanya AUTO_ALT dan AUTO_IP yang dianggap sebagai ban internal VelioraSuite.
 * Nilai lain dipertahankan untuk membaca data lama tanpa mengambil alih ban manual.
 */
public enum BanSource {
    AUTO_ALT,
    AUTO_IP,
    MANUAL,
    MANUAL_OWNER,
    EXTERNAL,
    UNKNOWN;

    public boolean isInternalAuto() {
        return this == AUTO_ALT || this == AUTO_IP;
    }
}
