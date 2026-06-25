package id.velioragardens.veliorasuite.module.loginsecurity;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

public final class LoginSecurityPasswordManager {

    private final LoginSecurityConfigManager configManager;
    private final SecureRandom secureRandom = new SecureRandom();

    public LoginSecurityPasswordManager(LoginSecurityConfigManager configManager) {
        this.configManager = configManager;
    }

    public PasswordHash createHash(String password) throws Exception {
        byte[] salt = new byte[configManager.getSaltLength()];
        secureRandom.nextBytes(salt);
        String saltBase64 = Base64.getEncoder().encodeToString(salt);
        String hash = hash(password, saltBase64);
        return new PasswordHash(hash, saltBase64);
    }

    public boolean verify(String password, String expectedHash, String saltBase64) throws Exception {
        if (password == null || expectedHash == null || saltBase64 == null) {
            return false;
        }
        String actualHash = hash(password, saltBase64);
        return MessageDigest.isEqual(actualHash.getBytes(), expectedHash.getBytes());
    }

    public String hashIp(String ip) {
        if (ip == null || ip.isBlank()) return "";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(ip.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashed);
        } catch (Exception exception) {
            return "";
        }
    }

    private String hash(String password, String saltBase64) throws Exception {
        byte[] salt = Base64.getDecoder().decode(saltBase64);
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, configManager.getHashIterations(), configManager.getHashKeyLength());
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance(configManager.getHashAlgorithm());
            return Base64.getEncoder().encodeToString(factory.generateSecret(spec).getEncoded());
        } finally {
            spec.clearPassword();
        }
    }

    public record PasswordHash(String hash, String salt) {
    }
}
