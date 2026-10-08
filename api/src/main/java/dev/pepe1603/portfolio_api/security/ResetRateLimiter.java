package dev.pepe1603.portfolio_api.security;

import java.util.concurrent.TimeUnit;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class ResetRateLimiter {

    private static final String IP_PREFIX = "rl:reset:ip:";

    private final StringRedisTemplate redis;
    private final RateLimitProperties properties;

    public ResetRateLimiter(StringRedisTemplate redis, RateLimitProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    public boolean isBlocked(String ip) {
        return overLimit(IP_PREFIX + ip, properties.getResetMaxAttemptsPerIp());
    }

    public void record(String ip) {
        String key = IP_PREFIX + ip;
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redis.expire(key, properties.getResetWindowSeconds(), TimeUnit.SECONDS);
        }
    }

    public void onSuccess(String ip) {
        redis.delete(IP_PREFIX + ip);
    }

    private boolean overLimit(String key, long max) {
        String value = redis.opsForValue().get(key);
        return value != null && Long.parseLong(value) >= max;
    }
}