package dev.pepe1603.api.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.pepe1603.api.config.StorageProperties;
import dev.pepe1603.api.controller.AdminOtpController;
import dev.pepe1603.api.controller.AuthController;
import dev.pepe1603.api.entity.AuthSession;
import dev.pepe1603.api.entity.User;
import dev.pepe1603.api.enums.AuditAction;
import dev.pepe1603.api.enums.AuditResource;
import dev.pepe1603.api.enums.UserRole;
import dev.pepe1603.api.exception.ApiExceptionHandler;
import dev.pepe1603.api.repository.AuthSessionRepository;
import dev.pepe1603.api.repository.UserRepository;
import dev.pepe1603.api.security.AccessTokenReader;
import dev.pepe1603.api.security.AppUserDetails;
import dev.pepe1603.api.security.JwtProperties;
import dev.pepe1603.api.security.JwtTokenService;
import dev.pepe1603.api.security.LoginRateLimiter;
import dev.pepe1603.api.security.OtpProperties;
import dev.pepe1603.api.security.RateLimitProperties;
import dev.pepe1603.api.security.ResetRateLimiter;
import dev.pepe1603.api.security.TokenBlacklist;
import dev.pepe1603.api.service.AuditService;
import dev.pepe1603.api.service.OtpService;
import dev.pepe1603.api.service.PasswordResetService;
import dev.pepe1603.api.service.SecurityNotificationService;
import io.jsonwebtoken.Claims;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {AuthController.class, AdminOtpController.class})
@AutoConfigureMockMvc(addFilters = false)
@Import({ApiExceptionHandler.class, StorageProperties.class})
@TestPropertySource(properties = "APP_AUTH_OTP_ENABLED=true")
class AuthOtpLoginTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthenticationManager authenticationManager;
    @MockitoBean
    private UserRepository userRepository;
    @MockitoBean
    private AuthSessionRepository authSessionRepository;
    @MockitoBean
    private JwtTokenService jwtTokenService;
    @MockitoBean
    private JwtProperties jwtProperties;
    @MockitoBean
    private RateLimitProperties rateLimitProperties;
    @MockitoBean
    private LoginRateLimiter loginRateLimiter;
    @MockitoBean
    private TokenBlacklist tokenBlacklist;
    @MockitoBean
    private AuditService auditService;
    @MockitoBean
    private PasswordResetService passwordResetService;
    @MockitoBean
    private ResetRateLimiter resetRateLimiter;
    @MockitoBean
    private PasswordEncoder passwordEncoder;
    @MockitoBean
    private OtpService otpService;
    @MockitoBean
    private OtpProperties otpProperties;
    @MockitoBean
    private AccessTokenReader accessTokenReader;
    @MockitoBean
    private SecurityNotificationService securityNotificationService;

    private User admin(boolean otpEnabled) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("admin@pepe.dev");
        user.setRole(UserRole.ADMIN);
        user.setTokenVersion(1);
        user.setOtpEnabled(otpEnabled);
        return user;
    }

    private void stubCredentials(User user) {
        given(jwtProperties.refreshTtl()).willReturn(java.time.Duration.ofDays(7));
        given(jwtProperties.accessTtl()).willReturn(java.time.Duration.ofMinutes(15));
        given(jwtProperties.getRefreshCookie()).willReturn("refresh_token");
        given(jwtProperties.isRefreshCookieSecure()).willReturn(false);
        given(otpProperties.isEnabled()).willReturn(true);
        given(loginRateLimiter.isBlocked(anyString(), anyString())).willReturn(false);
        given(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .willReturn(new UsernamePasswordAuthenticationToken(new AppUserDetails(user), null, List.of()));
    }

    @Test
    void loginConOtpActivoResponde202YNoEmiteTokens() throws Exception {
        User user = admin(true);
        stubCredentials(user);
        given(otpService.createChallenge(eq("admin@pepe.dev"), any())).willReturn("challenge-1");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@pepe.dev\",\"password\":\"secret\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.challengeId").value("challenge-1"));

        verify(authSessionRepository, never()).save(any());
        verify(jwtTokenService, never()).issueTokenPair(any(), anyString(), anyString());
    }

    @Test
    void loginConOtpDesactivadoEmiteTokens() throws Exception {
        User user = admin(false);
        stubCredentials(user);
        given(jwtTokenService.issueTokenPair(any(), anyString(), anyString()))
                .willReturn(new JwtTokenService.TokenPair("access-1", "refresh-1"));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@pepe.dev\",\"password\":\"secret\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-1"));

        verify(otpService, never()).createChallenge(anyString(), any());
        verify(authSessionRepository).save(any());
    }

    @Test
    void loginVerifyConsumeElChallengeRegistraSesionYEmiteTokens() throws Exception {
        User user = admin(true);
        given(jwtProperties.refreshTtl()).willReturn(java.time.Duration.ofDays(7));
        given(jwtProperties.accessTtl()).willReturn(java.time.Duration.ofMinutes(15));
        given(jwtProperties.getRefreshCookie()).willReturn("refresh_token");
        given(jwtProperties.isRefreshCookieSecure()).willReturn(false);
        given(otpService.verify("challenge-1", "123456")).willReturn(Optional.of(user));
        given(jwtTokenService.issueTokenPair(any(), anyString(), anyString()))
                .willReturn(new JwtTokenService.TokenPair("access-2", "refresh-2"));

        mockMvc.perform(post("/auth/login/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"challengeId\":\"challenge-1\",\"code\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-2"));

        assertThat(user.getLastLoginAt()).isNotNull();

        ArgumentCaptor<AuthSession> captor = ArgumentCaptor.forClass(AuthSession.class);
        verify(authSessionRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(user.getId());
        assertThat(captor.getValue().getRefreshJti()).isNotBlank();
        assertThat(captor.getValue().getExpiresAt()).isAfter(Instant.now());
    }

    @Test
    void loginVerifyConCodigoInvalidoDevuelve401() throws Exception {
        given(otpService.verify("challenge-1", "000000")).willReturn(Optional.empty());

        mockMvc.perform(post("/auth/login/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"challengeId\":\"challenge-1\",\"code\":\"000000\"}"))
                .andExpect(status().isUnauthorized());

        verify(authSessionRepository, never()).save(any());
    }

    @Test
    void loginVerifySinCamposDevuelve400() throws Exception {
        mockMvc.perform(post("/auth/login/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"challengeId\":\"\",\"code\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void patchAdminOtpActivaElSegundoFactorYAudita() throws Exception {
        User user = admin(false);
        Claims claims = org.mockito.Mockito.mock(Claims.class);
        given(claims.getSubject()).willReturn("admin@pepe.dev");
        given(accessTokenReader.read(any())).willReturn(Optional.of(claims));
        given(userRepository.findByEmail("admin@pepe.dev")).willReturn(Optional.of(user));

        mockMvc.perform(patch("/admin/otp")
                        .header("Authorization", "Bearer access-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}"))
                .andExpect(status().isNoContent());

        assertThat(user.getOtpEnabled()).isTrue();
        verify(auditService).record(eq(AuditAction.UPDATE), eq(AuditResource.USER), eq(user.getId()),
                eq("otp-enabled:true"));
    }

    @Test
    void patchAdminOtpDesactivaElSegundoFactor() throws Exception {
        User user = admin(true);
        Claims claims = org.mockito.Mockito.mock(Claims.class);
        given(claims.getSubject()).willReturn("admin@pepe.dev");
        given(accessTokenReader.read(any())).willReturn(Optional.of(claims));
        given(userRepository.findByEmail("admin@pepe.dev")).willReturn(Optional.of(user));

        mockMvc.perform(patch("/admin/otp")
                        .header("Authorization", "Bearer access-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isNoContent());

        assertThat(user.getOtpEnabled()).isFalse();
    }

    @Test
    void patchAdminOtpSinAccessTokenDevuelve401() throws Exception {
        given(accessTokenReader.read(any())).willReturn(Optional.empty());

        mockMvc.perform(patch("/admin/otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void patchAdminOtpSinCampoEnabledDevuelve400() throws Exception {
        mockMvc.perform(patch("/admin/otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void loginResendDevuelve202CuandoElCorreoQuedaEncolado() throws Exception {
        given(otpService.resend(eq("c1"), any(Locale.class)))
                .willReturn(new OtpService.OtpResendResult(OtpService.OtpResendResult.Status.RESENT, 0));

        mockMvc.perform(post("/auth/login/resend").contentType(MediaType.APPLICATION_JSON)
                .content("{\"challengeId\":\"c1\"}").accept("es-ES"))
                .andExpect(status().isAccepted());
    }

    @Test
    void loginResendDemasiadoProntoDevuelve429ConRetryAfter() throws Exception {
        given(otpService.resend(eq("c1"), any(Locale.class)))
                .willReturn(new OtpService.OtpResendResult(OtpService.OtpResendResult.Status.TOO_SOON, 18));

        mockMvc.perform(post("/auth/login/resend").contentType(MediaType.APPLICATION_JSON)
                .content("{\"challengeId\":\"c1\"}"))
                .andExpect(status().isTooManyRequests())
                // El boton del navegador tiene que saber cuanto queda, no inventarselo.
                .andExpect(header().string("Retry-After", "18"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("hace poco")));
    }

    @Test
    void loginResendConChallengeCaducadoDevuelve401() throws Exception {
        given(otpService.resend(eq("c1"), any(Locale.class)))
                .willReturn(new OtpService.OtpResendResult(OtpService.OtpResendResult.Status.NOT_FOUND, 0));

        mockMvc.perform(post("/auth/login/resend").contentType(MediaType.APPLICATION_JSON)
                .content("{\"challengeId\":\"c1\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginResendSinChallengeIdDevuelve400() throws Exception {
        mockMvc.perform(post("/auth/login/resend").contentType(MediaType.APPLICATION_JSON)
                .content("{\"challengeId\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void loginResendPasaElIdiomaDeLaCabecera() throws Exception {
        given(otpService.resend(eq("c1"), any(Locale.class)))
                .willReturn(new OtpService.OtpResendResult(OtpService.OtpResendResult.Status.RESENT, 0));
        ArgumentCaptor<Locale> locale = ArgumentCaptor.forClass(Locale.class);

        mockMvc.perform(post("/auth/login/resend").contentType(MediaType.APPLICATION_JSON)
                .content("{\"challengeId\":\"c1\"}").header("Accept-Language", "en-GB"))
                .andExpect(status().isAccepted());

        // El reenvio en ingles tiene que salir en ingles, no en el idioma por defecto del servidor.
        verify(otpService).resend(eq("c1"), locale.capture());
        assertThat(locale.getValue()).isEqualTo(new Locale("en", "GB"));
    }
}
