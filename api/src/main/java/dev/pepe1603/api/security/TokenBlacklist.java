package dev.pepe1603.api.security;

import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class TokenBlacklist {

    private static final String KEY_PREFIX = "jwt:revoked:";

    private final StringRedisTemplate redis;

    public TokenBlacklist(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void revoke(String jti, Duration ttl) {
        redis.opsForValue().set(KEY_PREFIX + jti, "1", ttl);
    }

    public boolean isRevoked(String jti) {
        return Boolean.TRUE.equals(redis.hasKey(KEY_PREFIX + jti));
    }
}