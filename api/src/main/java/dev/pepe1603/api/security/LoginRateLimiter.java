package dev.pepe1603.api.security;

import java.util.concurrent.TimeUnit;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class LoginRateLimiter {

    private static final String IP_PREFIX = "rl:login:ip:";
    private static final String EMAIL_PREFIX = "rl:login:email:";

    private final StringRedisTemplate redis;
    private final RateLimitProperties properties;

    public LoginRateLimiter(StringRedisTemplate redis, RateLimitProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    public boolean isBlocked(String ip, String email) {
        return overLimit(IP_PREFIX + ip, properties.getMaxAttemptsPerIp())
                || overLimit(EMAIL_PREFIX + email, properties.getMaxAttemptsPerEmail());
    }

    public void recordFailure(String ip, String email) {
        increment(IP_PREFIX + ip);
        increment(EMAIL_PREFIX + email);
    }

    public void onSuccess(String ip, String email) {
        redis.delete(IP_PREFIX + ip);
        redis.delete(EMAIL_PREFIX + email);
    }

    private boolean overLimit(String key, long max) {
        String value = redis.opsForValue().get(key);
        return value != null && Long.parseLong(value) >= max;
    }

    private void increment(String key) {
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redis.expire(key, properties.getWindowSeconds(), TimeUnit.SECONDS);
        }
    }
}