package id.velioragardens.veliorasuite.module.loginsecurity.model;

import java.util.UUID;

public final class AuthPlayerData {

    private UUID uuid;
    private String name;
    private String passwordHash;
    private String salt;
    private String registeredAt;
    private String lastLogin;
    private String lastIpHash;
    private int failedAttempts;
    private long lockedUntil;

    public AuthPlayerData(UUID uuid, String name, String passwordHash, String salt, String registeredAt, String lastLogin, String lastIpHash, int failedAttempts, long lockedUntil) {
        this.uuid = uuid;
        this.name = name;
        this.passwordHash = passwordHash;
        this.salt = salt;
        this.registeredAt = registeredAt;
        this.lastLogin = lastLogin;
        this.lastIpHash = lastIpHash;
        this.failedAttempts = failedAttempts;
        this.lockedUntil = lockedUntil;
    }

    public UUID getUuid() { return uuid; }
    public void setUuid(UUID uuid) { this.uuid = uuid; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getSalt() { return salt; }
    public void setSalt(String salt) { this.salt = salt; }

    public String getRegisteredAt() { return registeredAt; }
    public void setRegisteredAt(String registeredAt) { this.registeredAt = registeredAt; }

    public String getLastLogin() { return lastLogin; }
    public void setLastLogin(String lastLogin) { this.lastLogin = lastLogin; }

    public String getLastIpHash() { return lastIpHash; }
    public void setLastIpHash(String lastIpHash) { this.lastIpHash = lastIpHash; }

    public int getFailedAttempts() { return failedAttempts; }
    public void setFailedAttempts(int failedAttempts) { this.failedAttempts = failedAttempts; }

    public long getLockedUntil() { return lockedUntil; }
    public void setLockedUntil(long lockedUntil) { this.lockedUntil = lockedUntil; }
}
