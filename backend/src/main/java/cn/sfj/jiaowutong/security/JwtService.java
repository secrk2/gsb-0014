package cn.sfj.jiaowutong.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * JWT 签发/解析。载荷携带 userId、username、realName、role、officeId、offenderId。
 */
@Component
public class JwtService {

    private final SecretKey key;
    private final long ttlMs;

    public JwtService(@Value("${jwt.secret}") String secret,
                      @Value("${jwt.ttl}") long ttlMs) {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        // 短密钥时补齐，避免 HMAC-SHA256 最低 256 位要求（演示环境；生产用环境变量注入强密钥）
        this.key = Keys.hmacShaKeyFor(padOrUse(keyBytes));
        this.ttlMs = ttlMs;
    }

    private byte[] padOrUse(byte[] bytes) {
        if (bytes.length >= 32) {
            return bytes;
        }
        byte[] padded = new byte[32];
        System.arraycopy(bytes, 0, padded, 0, bytes.length);
        return padded;
    }

    public String issue(LoginUser user) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(String.valueOf(user.userId()))
                .claim("username", user.username())
                .claim("realName", user.realName())
                .claim("role", user.role().name())
                .claim("officeId", user.officeId())
                .claim("offenderId", user.offenderId())
                .issuedAt(new Date(now))
                .expiration(new Date(now + ttlMs))
                .signWith(key)
                .compact();
    }

    public LoginUser parse(String token) {
        Claims c = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        Long userId = Long.valueOf(c.getSubject());
        Long officeId = c.get("officeId") == null ? null : ((Number) c.get("officeId")).longValue();
        Long offenderId = c.get("offenderId") == null ? null : ((Number) c.get("offenderId")).longValue();
        return new LoginUser(
                userId,
                (String) c.get("username"),
                (String) c.get("realName"),
                cn.sfj.jiaowutong.domain.Role.valueOf((String) c.get("role")),
                officeId,
                offenderId
        );
    }
}
