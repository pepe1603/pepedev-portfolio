package dev.pepe1603.portfolio_api.security;

import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class ContactRateLimiter {

    private static final String PREFIX = "rl:contact:ip:";

    private final StringRedisTemplate redis;
    private final int maxPerIp;
    private final long windowSeconds;

    public ContactRateLimiter(StringRedisTemplate redis,
            @Value("${APP_CONTACT_RATE_MAX_IP:5}") int maxPerIp,
            @Value("${APP_CONTACT_RATE_WINDOW:900}") long windowSeconds) {
        this.redis = redis;
        this.maxPerIp = maxPerIp;
        this.windowSeconds = windowSeconds;
    }

    public boolean isBlocked(String ip) {
        String value = redis.opsForValue().get(PREFIX + ip);
        return value != null && Long.parseLong(value) >= maxPerIp;
    }

    public void recordRequest(String ip) {
        Long count = redis.opsForValue().increment(PREFIX + ip);
        if (count != null && count == 1L) {
            redis.expire(PREFIX + ip, windowSeconds, TimeUnit.SECONDS);
        }
    }

    public long getWindowSeconds() {
        return windowSeconds;
    }
}