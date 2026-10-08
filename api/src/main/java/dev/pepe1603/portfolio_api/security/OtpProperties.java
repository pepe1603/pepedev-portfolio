package dev.pepe1603.portfolio_api.security;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Getter
@Component
public class OtpProperties {

    @Value("${APP_AUTH_OTP_ENABLED:false}")
    private boolean enabled;

    @Value("${APP_AUTH_OTP_TTL:300}")
    private long ttlSeconds;

    @Value("${APP_AUTH_OTP_MAX_ATTEMPTS:5}")
    private int maxAttempts;

    /** Enfriamiento entre reenvíos del mismo challenge, para no usarlo de altavoz de correo. */
    @Value("${APP_AUTH_OTP_RESEND_COOLDOWN:30}")
    private long resendCooldownSeconds;
}