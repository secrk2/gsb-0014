package cn.sfj.jiaowutong.security;

import org.springframework.stereotype.Component;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 口令哈希：PBKDF2-HMAC-SHA256，每账号独立盐。
 */
@Component
public class PasswordEncoder {

    private static final int ITERATIONS = 12000;
    private static final int KEY_LENGTH_BITS = 256;
    private static final int SALT_BYTES = 16;

    private final SecureRandom random = new SecureRandom();

    public String[] newSaltAndHash(String rawPassword) {
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        String saltStr = Base64.getEncoder().encodeToString(salt);
        return new String[]{saltStr, hash(rawPassword, saltStr)};
    }

    public String hash(String rawPassword, String salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(
                    rawPassword.toCharArray(),
                    Base64.getDecoder().decode(salt),
                    ITERATIONS,
                    KEY_LENGTH_BITS);
            byte[] hash = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec).getEncoded();
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException("口令哈希失败", e);
        }
    }

    public boolean matches(String rawPassword, String salt, String expectedHash) {
        return constantTimeEquals(expectedHash, hash(rawPassword, salt));
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }
}
