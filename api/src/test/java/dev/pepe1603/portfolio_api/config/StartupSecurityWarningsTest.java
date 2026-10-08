package dev.pepe1603.portfolio_api.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.pepe1603.portfolio_api.security.JwtProperties;
import dev.pepe1603.portfolio_api.security.OtpProperties;
import dev.pepe1603.portfolio_api.security.SecurityMailProperties;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class StartupSecurityWarningsTest {

    private static final String STRONG = Base64.getEncoder()
            .encodeToString("vQ8#zR2!pLm5^tY7&wX9@qN3$kD6*hJ1".getBytes());
    private static final String OTHER_STRONG = Base64.getEncoder()
            .encodeToString("Zm2@Lp4^Rt7*Ny1#Ws6&Ke3%Dc8!Xg5-".getBytes());

    private final JwtProperties jwtProperties = new JwtProperties();
    private final OtpProperties otpProperties = new OtpProperties();
    private final SecurityMailProperties mailProperties = new SecurityMailProperties();

    private StartupSecurityWarnings warnings(String accessSecret, String refreshSecret, boolean cookieSecure,
            boolean otpEnabled, String fromEmail) {
        return warnings(accessSecret, refreshSecret, cookieSecure, otpEnabled, fromEmail, false, false, true);
    }

    private StartupSecurityWarnings warnings(String accessSecret, String refreshSecret, boolean cookieSecure,
            boolean otpEnabled, String fromEmail, boolean loginMail, boolean logoutMail, boolean resetMail) {
        ReflectionTestUtils.setField(jwtProperties, "accessSecret", accessSecret);
        ReflectionTestUtils.setField(jwtProperties, "refreshSecret", refreshSecret);
        ReflectionTestUtils.setField(jwtProperties, "refreshCookieSecure", cookieSecure);
        ReflectionTestUtils.setField(otpProperties, "enabled", otpEnabled);
        ReflectionTestUtils.setField(mailProperties, "loginEnabled", loginMail);
        ReflectionTestUtils.setField(mailProperties, "logoutEnabled", logoutMail);
        ReflectionTestUtils.setField(mailProperties, "resetEnabled", resetMail);
        return new StartupSecurityWarnings(jwtProperties, otpProperties, mailProperties, fromEmail);
    }

    @Test
    void avisaSiLosSecretosJwtSonIguales() {
        assertThat(warnings(STRONG, STRONG, true, false, "no-reply@pepe.dev").warnings())
                .anyMatch(warning -> warning.contains("son iguales"));
    }

    @Test
    void avisaSiLosSecretosParecenDeDesarrollo() {
        assertThat(warnings("c2VjcmV0by1kZS1kZXYtc2VjcmV0by1kZXYtc2VjcmV0by1kZXY=", OTHER_STRONG, true, false,
                "no-reply@pepe.dev").warnings())
                .anyMatch(warning -> warning.contains("parecen de desarrollo"));
    }

    @Test
    void avisaSiLaCookieDeRefreshNoEsSecure() {
        assertThat(warnings(STRONG, OTHER_STRONG, false, false, "no-reply@pepe.dev").warnings())
                .anyMatch(warning -> warning.contains("APP_JWT_REFRESH_COOKIE_SECURE"));
    }

    @Test
    void avisaSiElOtpEstaActivoSinRemitente() {
        assertThat(warnings(STRONG, OTHER_STRONG, true, true, "").warnings())
                .anyMatch(warning -> warning.contains("APP_CONTACT_FROM_EMAIL"));
    }

    @Test
    void avisaSiElResetEstaActivoSinRemitente() {
        assertThat(warnings(STRONG, OTHER_STRONG, true, false, "", false, false, true).warnings())
                .anyMatch(warning -> warning.contains("APP_MAIL_SECURITY_RESET"));
    }

    @Test
    void avisaSiLosAvisosDeSesionEstanActivosSinRemitente() {
        assertThat(warnings(STRONG, OTHER_STRONG, true, false, "", true, true, false).warnings())
                .anyMatch(warning -> warning.contains("APP_MAIL_SECURITY_LOGIN/LOGOUT"));
    }

    @Test
    void conTodosLosCorreosDeSeguridadApagadosNoAvisaPorFaltaDeRemitente() {
        assertThat(warnings(STRONG, OTHER_STRONG, true, false, "", false, false, false).warnings()).isEmpty();
    }

    @Test
    void sinProblemasDeConfiguracionNoAvisa() {
        assertThat(warnings(STRONG, OTHER_STRONG, true, true, "no-reply@pepe.dev").warnings()).isEmpty();
    }
}