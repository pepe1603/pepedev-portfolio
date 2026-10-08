package dev.pepe1603.api.security;

import java.time.Duration;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Getter
@Component
public class JwtProperties {

    @Value("${APP_JWT_SECRET}")
    private String accessSecret;

    @Value("${APP_JWT_REFRESH_SECRET}")
    private String refreshSecret;

    @Value("${APP_JWT_ACCESS_TTL:900}")
    private long accessTtlSeconds;

    @Value("${APP_JWT_REFRESH_TTL:604800}")
    private long refreshTtlSeconds;

    @Value("${APP_JWT_REFRESH_COOKIE:refresh_token}")
    private String refreshCookie;

    @Value("${APP_JWT_REFRESH_COOKIE_SECURE:false}")
    private boolean refreshCookieSecure;

    public Duration accessTtl() {
        return Duration.ofSeconds(accessTtlSeconds);
    }

    public Duration refreshTtl() {
        return Duration.ofSeconds(refreshTtlSeconds);
    }
}