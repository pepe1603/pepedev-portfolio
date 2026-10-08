package dev.pepe1603.portfolio_api.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.pepe1603.portfolio_api.config.StorageProperties;
import dev.pepe1603.portfolio_api.controller.AuthController;
import dev.pepe1603.portfolio_api.entity.AuthSession;
import dev.pepe1603.portfolio_api.entity.User;
import dev.pepe1603.portfolio_api.enums.UserRole;
import dev.pepe1603.portfolio_api.exception.ApiExceptionHandler;
import dev.pepe1603.portfolio_api.repository.AuthSessionRepository;
import dev.pepe1603.portfolio_api.repository.UserRepository;
import dev.pepe1603.portfolio_api.security.AccessTokenReader;
import dev.pepe1603.portfolio_api.security.AppUserDetails;
import dev.pepe1603.portfolio_api.security.JwtProperties;
import dev.pepe1603.portfolio_api.security.JwtTokenService;
import dev.pepe1603.portfolio_api.security.LoginRateLimiter;
import dev.pepe1603.portfolio_api.security.OtpProperties;
import dev.pepe1603.portfolio_api.security.RateLimitProperties;
import dev.pepe1603.portfolio_api.security.ResetRateLimiter;
import dev.pepe1603.portfolio_api.security.TokenBlacklist;
import dev.pepe1603.portfolio_api.service.AuditService;
import dev.pepe1603.portfolio_api.service.OtpService;
import dev.pepe1603.portfolio_api.service.PasswordResetService;
import dev.pepe1603.portfolio_api.service.SecurityNotificationService;
import io.jsonwebtoken.Claims;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Cableado de los avisos de sesión: quién los dispara y en qué orden. El contenido de los correos
 * y el corte por IP/navegador conocidos están en {@code SecurityNotificationServiceTest}.
 */
@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({ApiExceptionHandler.class, StorageProperties.class})
class AuthSessionNotificationTest {

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

    private User admin() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("admin@pepe.dev");
        user.setRole(UserRole.ADMIN);
        user.setTokenVersion(1);
        return user;
    }

    @BeforeEach
    void stubJwt() {
        given(jwtProperties.refreshTtl()).willReturn(Duration.ofDays(7));
        given(jwtProperties.accessTtl()).willReturn(Duration.ofMinutes(15));
        given(jwtProperties.getRefreshCookie()).willReturn("refresh_token");
        given(jwtProperties.isRefreshCookieSecure()).willReturn(false);
        given(jwtTokenService.issueTokenPair(any(), any(), anyString())).willReturn(
                new JwtTokenService.TokenPair("access-1", "refresh-1"));
    }

    private void stubSuccessfulLogin(User user) {
        given(loginRateLimiter.isBlocked(anyString(), anyString())).willReturn(false);
        given(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .willReturn(new UsernamePasswordAuthenticationToken(new AppUserDetails(user), null, List.of()));
    }

    @Test
    void elLoginAvisaDelAccesoNuevoAntesDeGuardarLaSesion() throws Exception {
        User user = admin();
        stubSuccessfulLogin(user);

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@pepe.dev\",\"password\":\"secret\"}")
                        .header(HttpHeaders.USER_AGENT, "curl/8.7")
                        .header("Accept-Language", "en"))
                .andExpect(status().isOk());

        InOrder orden = inOrder(securityNotificationService, authSessionRepository);
        orden.verify(securityNotificationService).notifyNewLogin(user, "127.0.0.1", "curl/8.7",
                java.util.Locale.forLanguageTag("en"));
        orden.verify(authSessionRepository).save(any(AuthSession.class));
    }

    @Test
    void elLoginVerificadoPorOtpTambienAvisa() throws Exception {
        User user = admin();
        user.setOtpEnabled(true);
        stubSuccessfulLogin(user);
        given(otpProperties.isEnabled()).willReturn(true);
        given(otpService.createChallenge(anyString(), any())).willReturn("challenge-1");
        given(otpService.verify("challenge-1", "123456")).willReturn(Optional.of(user));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@pepe.dev\",\"password\":\"secret\"}"))
                .andExpect(status().isAccepted());
        mockMvc.perform(post("/auth/login/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"challengeId\":\"challenge-1\",\"code\":\"123456\"}"))
                .andExpect(status().isOk());

        verify(securityNotificationService).notifyNewLogin(any(User.class), anyString(), any(), any());
    }

    @Test
    void elLogoutAvisaConLosDatosDeLaPeticion() throws Exception {
        User user = admin();
        Claims claims = org.mockito.Mockito.mock(Claims.class);
        given(accessTokenReader.read(any())).willReturn(Optional.of(claims));
        given(claims.getSubject()).willReturn("admin@pepe.dev");
        given(claims.getExpiration()).willReturn(java.util.Date.from(Instant.now().plusSeconds(600)));
        given(userRepository.findByEmail("admin@pepe.dev")).willReturn(Optional.of(user));
        given(jwtTokenService.parseAccessToken("access-1")).willReturn(claims);

        mockMvc.perform(post("/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access-1")
                        .header(HttpHeaders.USER_AGENT, "Firefox/1"))
                .andExpect(status().isNoContent());

        verify(securityNotificationService).notifyLogout(user, "127.0.0.1", "Firefox/1",
                java.util.Locale.forLanguageTag("en"));
    }

    @Test
    void elLogoutSinTokenValidoResponde204PeroNoAvisa() throws Exception {
        given(accessTokenReader.read(any())).willReturn(Optional.empty());

        mockMvc.perform(post("/auth/logout"))
                .andExpect(status().isNoContent());

        verify(securityNotificationService, never()).notifyLogout(any(), anyString(), any(), any());
    }

}
