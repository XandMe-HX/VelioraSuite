package id.velioragardens.veliorasuite.module.security.model;

/**
 * Hanya sumber AUTO_* yang dibuat VelioraSuite dianggap sebagai ban internal.
 * Nilai lain dipertahankan untuk membaca data lama tanpa mengambil alih ban manual.
 */
public enum BanSource {
    AUTO_ALT,
    AUTO_IP,
    AUTO_XRAY,
    MANUAL,
    MANUAL_OWNER,
    EXTERNAL,
    UNKNOWN;

    public boolean isInternalAuto() {
        return this == AUTO_ALT || this == AUTO_IP || this == AUTO_XRAY;
    }
}
