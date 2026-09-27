package dev.pepe1603.portfolio_api.security;

import dev.pepe1603.portfolio_api.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {

    public record TokenPair(String accessToken, String refreshToken) {
    }

    private final JwtProperties properties;
    private final SecretKey accessKey;
    private final SecretKey refreshKey;

    public JwtTokenService(JwtProperties properties) {
        this.properties = properties;
        this.accessKey = parseKey(properties.getAccessSecret(), "APP_JWT_SECRET");
        this.refreshKey = parseKey(properties.getRefreshSecret(), "APP_JWT_REFRESH_SECRET");
    }

    public TokenPair issueTokenPair(User user, String sid, String refreshJti) {
        Date now = new Date();
        String access = buildToken(user, accessKey, "access", now, properties.accessTtl(), sid, null);
        String refresh = buildToken(user, refreshKey, "refresh", now, properties.refreshTtl(), sid, refreshJti);
        return new TokenPair(access, refresh);
    }

    public Claims parseAccessToken(String token) {
        return parse(token, accessKey, "access");
    }

    public Claims parseRefreshToken(String token) {
        return parse(token, refreshKey, "refresh");
    }

    private String buildToken(User user, SecretKey key, String type, Date issuedAt, java.time.Duration ttl,
            String sid, String jti) {
        Date expiration = new Date(issuedAt.getTime() + ttl.toMillis());
        io.jsonwebtoken.JwtBuilder builder = Jwts.builder()
                .id(jti != null ? jti : UUID.randomUUID().toString())
                .subject(user.getEmail())
                .claim("role", user.getRole().name())
                .claim("tv", user.getTokenVersion())
                .claim("typ", type);
        if (sid != null) {
            builder.claim("sid", sid);
        }
        return builder
                .issuedAt(issuedAt)
                .expiration(expiration)
                .signWith(key)
                .compact();
    }

    private Claims parse(String token, SecretKey key, String expectedType) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        if (!expectedType.equals(claims.get("typ", String.class))) {
            throw new JwtException("Tipo de token incorrecto: " + claims.get("typ", String.class));
        }
        return claims;
    }

    private SecretKey parseKey(String secret, String source) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(source + " no está definido en el entorno");
        }
        try {
            byte[] keyBytes = Base64.getDecoder().decode(secret.trim());
            if (keyBytes.length < 32) {
                throw new IllegalStateException(source + " debe ser un secreto Base64 de al menos 32 bytes (HS256)");
            }
            return Keys.hmacShaKeyFor(keyBytes);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(source + " debe ser un secreto codificado en Base64 válido", e);
        }
    }
}