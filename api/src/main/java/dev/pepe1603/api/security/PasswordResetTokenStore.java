package dev.pepe1603.api.security;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class PasswordResetTokenStore {

    private static final String KEY_PREFIX = "pwreset:";

    private final StringRedisTemplate redis;

    public PasswordResetTokenStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public String create(String email, Duration ttl) {
        String token = UUID.randomUUID().toString().replace("-", "");
        redis.opsForValue().set(KEY_PREFIX + token, email, ttl);
        return token;
    }

    public Optional<String> consume(String token, String email) {
        String key = KEY_PREFIX + token;
        String stored = redis.opsForValue().get(key);
        if (stored == null || !stored.equals(email)) {
            return Optional.empty();
        }
        redis.delete(key);
        return Optional.of(stored);
    }
}