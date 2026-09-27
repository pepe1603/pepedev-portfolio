package dev.pepe1603.portfolio_api.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.pepe1603.portfolio_api.config.StorageProperties;
import dev.pepe1603.portfolio_api.controller.AuthController;
import dev.pepe1603.portfolio_api.entity.AuthSession;
import dev.pepe1603.portfolio_api.entity.User;
import dev.pepe1603.portfolio_api.enums.AuditAction;
import dev.pepe1603.portfolio_api.enums.AuditResource;
import dev.pepe1603.portfolio_api.enums.UserRole;
import dev.pepe1603.portfolio_api.exception.ApiExceptionHandler;
import dev.pepe1603.portfolio_api.repository.AuthSessionRepository;
import dev.pepe1603.portfolio_api.repository.UserRepository;
import dev.pepe1603.portfolio_api.security.AccessTokenReader;
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
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({ApiExceptionHandler.class, StorageProperties.class})
@TestPropertySource(properties = {"APP_ADMIN_SECRET=test-secret"})
class AuthResetTest {

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

    private User admin() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("admin@pepe.dev");
        user.setRole(UserRole.ADMIN);
        user.setTokenVersion(1);
        user.setPasswordHash("old-hash");
        return user;
    }

    @Test
    void requestRespond202SiempreAunqueNoExistaLaCuenta() throws Exception {
        given(resetRateLimiter.isBlocked(anyString())).willReturn(false);

        mockMvc.perform(post("/auth/reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nadie@pepe.dev\"}"))
                .andExpect(status().isAccepted());

        verify(passwordResetService).requestReset(eq("nadie@pepe.dev"), any());
    }

    @Test
    void requestConEmailInvalidoDevuelve400() throws Exception {
        mockMvc.perform(post("/auth/reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"no-es-email\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void confirmValidoCambiaPasswordBumpaTvRevocaSesionesYAudita() throws Exception {
        User user = admin();
        given(passwordResetService.consumeUser("tok123", "admin@pepe.dev")).willReturn(Optional.of(user));
        given(passwordEncoder.encode("NuevaClave123")).willReturn("nuevo-hash");
        given(authSessionRepository.findAllByUserIdAndRevokedAtIsNullOrderByLastSeenAtDesc(any()))
                .willReturn(List.of(activeSession(user)));

        mockMvc.perform(post("/auth/reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@pepe.dev\",\"token\":\"tok123\",\"password\":\"NuevaClave123\"}"))
                .andExpect(status().isNoContent());

        assertThat(user.getPasswordHash()).isEqualTo("nuevo-hash");
        assertThat(user.getTokenVersion()).isEqualTo(2);
        verify(authSessionRepository).save(any());
        verify(auditService).record(eq(AuditAction.RESET), eq(AuditResource.USER), eq(user.getId()), anyString());
    }

    private AuthSession activeSession(User user) {
        AuthSession session = new AuthSession();
        session.setSessionId(UUID.randomUUID());
        session.setUserId(user.getId());
        session.setRefreshJti("jti-old");
        session.setCreatedAt(Instant.now().minusSeconds(60));
        session.setLastSeenAt(Instant.now());
        session.setExpiresAt(Instant.now().plusSeconds(600));
        return session;
    }

    @Test
    void confirmConTokenInvalidoDevuelve401() throws Exception {
        given(passwordResetService.consumeUser("malo", "admin@pepe.dev")).willReturn(Optional.empty());

        mockMvc.perform(post("/auth/reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@pepe.dev\",\"token\":\"malo\",\"password\":\"NuevaClave123\"}"))
                .andExpect(status().isUnauthorized());

        verify(authSessionRepository, never()).save(any());
        verify(auditService, never()).record(any(), any(), any(), anyString());
    }

    @Test
    void confirmConPasswordCortaDevuelve400() throws Exception {
        mockMvc.perform(post("/auth/reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@pepe.dev\",\"token\":\"tok123\",\"password\":\"corta\"}"))
                .andExpect(status().isBadRequest());
    }
}