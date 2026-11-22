package dev.pepe1603.api.config;

import dev.pepe1603.api.security.JwtProperties;
import dev.pepe1603.api.security.OtpProperties;
import dev.pepe1603.api.security.SecurityMailProperties;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class StartupSecurityWarnings {

    private static final Logger LOG = LoggerFactory.getLogger(StartupSecurityWarnings.class);
    private static final List<String> DEV_SECRET_HINTS =
            List.of("dev", "change", "changeme", "test", "example", "placeholder", "secret", "dummy", "localdev");

    private final JwtProperties jwtProperties;
    private final OtpProperties otpProperties;
    private final SecurityMailProperties mailProperties;
    private final String fromEmail;

    public StartupSecurityWarnings(JwtProperties jwtProperties, OtpProperties otpProperties,
            SecurityMailProperties mailProperties,
            @Value("${APP_CONTACT_FROM_EMAIL:}") String fromEmail) {
        this.jwtProperties = jwtProperties;
        this.otpProperties = otpProperties;
        this.mailProperties = mailProperties;
        this.fromEmail = fromEmail;
    }

    @PostConstruct
    void logWarnings() {
        warnings().forEach(warning -> LOG.warn("[seguridad] {}", warning));
    }

    List<String> warnings() {
        List<String> warnings = new ArrayList<>();
        String accessSecret = jwtProperties.getAccessSecret();
        String refreshSecret = jwtProperties.getRefreshSecret();
        if (accessSecret != null && accessSecret.equals(refreshSecret)) {
            warnings.add("APP_JWT_SECRET y APP_JWT_REFRESH_SECRET son iguales: usa secretos distintos por "
                    + "entorno para que un refresh filtrado no sirva para firmar access tokens.");
        }
        if (looksLikeDevSecret(accessSecret) || looksLikeDevSecret(refreshSecret)) {
            warnings.add("Los secretos JWT parecen de desarrollo: genera secretos aleatorios distintos "
                    + "por entorno con `openssl rand -base64 64`.");
        }
        if (!jwtProperties.isRefreshCookieSecure()) {
            warnings.add("APP_JWT_REFRESH_COOKIE_SECURE=false: la cookie de refresh viaja sin Secure. "
                    + "Ponla en true en producción (HTTPS).");
        }
        boolean sinRemitente = fromEmail == null || fromEmail.isBlank();
        if (otpProperties.isEnabled() && sinRemitente) {
            warnings.add("APP_AUTH_OTP_ENABLED=true pero APP_CONTACT_FROM_EMAIL está vacío: "
                    + "los códigos OTP no se podrán enviar.");
        }
        if (sinRemitente && mailProperties.isResetEnabled()) {
            warnings.add("APP_MAIL_SECURITY_RESET=true pero APP_CONTACT_FROM_EMAIL está vacío: "
                    + "nadie podrá restablecer la contraseña por correo.");
        }
        if (sinRemitente && (mailProperties.isLoginEnabled() || mailProperties.isLogoutEnabled())) {
            warnings.add("APP_MAIL_SECURITY_LOGIN/LOGOUT activos pero APP_CONTACT_FROM_EMAIL está vacío: "
                    + "los avisos de acceso nuevo y de cierre de sesión no se podrán enviar.");
        }
        return warnings;
    }

    private boolean looksLikeDevSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            return false;
        }
        if (containsDevHint(secret.toLowerCase(Locale.ROOT))) {
            return true;
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(secret.trim());
            if (containsDevHint(new String(bytes, java.nio.charset.StandardCharsets.UTF_8).toLowerCase(Locale.ROOT))) {
                return true;
            }
            byte first = bytes[0];
            for (byte b : bytes) {
                if (b != first) {
                    return false;
                }
            }
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private boolean containsDevHint(String normalized) {
        return DEV_SECRET_HINTS.stream().anyMatch(normalized::contains);
    }
}