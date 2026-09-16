package dev.pepe1603.portfolio_api.security;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Getter
@Component
public class RateLimitProperties {

    @Value("${APP_LOGIN_RATE_MAX_IP:5}")
    private int maxAttemptsPerIp;

    @Value("${APP_LOGIN_RATE_MAX_EMAIL:10}")
    private int maxAttemptsPerEmail;

    @Value("${APP_LOGIN_RATE_WINDOW:900}")
    private long windowSeconds;
}