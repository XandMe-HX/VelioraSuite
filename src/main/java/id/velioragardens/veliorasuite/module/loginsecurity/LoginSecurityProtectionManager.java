package id.velioragardens.veliorasuite.module.loginsecurity;

import id.velioragardens.veliorasuite.module.loginsecurity.model.AuthPlayerData;

public final class LoginSecurityProtectionManager {

    private final LoginSecurityConfigManager configManager;

    public LoginSecurityProtectionManager(LoginSecurityConfigManager configManager) {
        this.configManager = configManager;
    }

    public PasswordValidation validatePassword(String password) {
        if (password == null || password.length() < configManager.getMinPasswordLength()) {
            return PasswordValidation.TOO_SHORT;
        }
        if (password.length() > configManager.getMaxPasswordLength()) {
            return PasswordValidation.TOO_LONG;
        }
        return PasswordValidation.OK;
    }

    public boolean isLocked(AuthPlayerData data) {
        return data != null && data.getLockedUntil() > System.currentTimeMillis();
    }

    public long getRemainingLockSeconds(AuthPlayerData data) {
        if (!isLocked(data)) return 0L;
        long remaining = data.getLockedUntil() - System.currentTimeMillis();
        return Math.max(1L, (remaining + 999L) / 1000L);
    }

    public void recordFailedAttempt(AuthPlayerData data) {
        if (data == null) return;
        int attempts = data.getFailedAttempts() + 1;
        data.setFailedAttempts(attempts);
        if (attempts >= configManager.getMaxLoginAttempts()) {
            data.setLockedUntil(System.currentTimeMillis() + (configManager.getLockSeconds() * 1000L));
        }
    }

    public void resetAttempts(AuthPlayerData data) {
        if (data == null) return;
        data.setFailedAttempts(0);
        data.setLockedUntil(0L);
    }

    public enum PasswordValidation {
        OK,
        TOO_SHORT,
        TOO_LONG
    }
}
