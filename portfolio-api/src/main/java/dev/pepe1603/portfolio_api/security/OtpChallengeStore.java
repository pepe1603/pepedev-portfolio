package dev.pepe1603.portfolio_api.security;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class OtpChallengeStore {

    private static final String KEY_PREFIX = "auth:otp:";
    private static final String ATTEMPTS_SUFFIX = ":attempts";

    private final StringRedisTemplate redis;

    public OtpChallengeStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public String create(String email, String codeHash, Duration ttl) {
        String challengeId = UUID.randomUUID().toString();
        redis.opsForValue().set(KEY_PREFIX + challengeId, email + "|" + codeHash, ttl);
        return challengeId;
    }

    public Optional<OtpChallenge> peek(String challengeId) {
        String raw = redis.opsForValue().get(KEY_PREFIX + challengeId);
        if (raw == null) {
            return Optional.empty();
        }
        int separator = raw.indexOf('|');
        if (separator < 0) {
            return Optional.empty();
        }
        return Optional.of(new OtpChallenge(raw.substring(0, separator), raw.substring(separator + 1)));
    }

    public long incrementAttempts(String challengeId) {
        String key = KEY_PREFIX + challengeId + ATTEMPTS_SUFFIX;
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redis.expire(key, Duration.ofMinutes(10));
        }
        return count == null ? 0L : count;
    }

    public void delete(String challengeId) {
        redis.delete(KEY_PREFIX + challengeId);
        redis.delete(KEY_PREFIX + challengeId + ATTEMPTS_SUFFIX);
    }

    public record OtpChallenge(String email, String codeHash) {
    }
}