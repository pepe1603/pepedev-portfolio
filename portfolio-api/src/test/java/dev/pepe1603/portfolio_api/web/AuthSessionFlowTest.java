package dev.pepe1603.portfolio_api.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.pepe1603.portfolio_api.config.StorageProperties;
import dev.pepe1603.portfolio_api.exception.ApiExceptionHandler;
import dev.pepe1603.portfolio_api.controller.AuthController;
import dev.pepe1603.portfolio_api.dto.auth.LoginRequest;
import dev.pepe1603.portfolio_api.entity.AuthSession;
import dev.pepe1603.portfolio_api.entity.User;
import dev.pepe1603.portfolio_api.enums.UserRole;
import dev.pepe1603.portfolio_api.repository.AuthSessionRepository;
import dev.pepe1603.portfolio_api.repository.UserRepository;
import dev.pepe1603.portfolio_api.service.AuditService;
import dev.pepe1603.portfolio_api.security.AppUserDetails;
import dev.pepe1603.portfolio_api.security.JwtProperties;
import dev.pepe1603.portfolio_api.security.JwtTokenService;
import dev.pepe1603.portfolio_api.security.LoginRateLimiter;
import dev.pepe1603.portfolio_api.security.RateLimitProperties;
import dev.pepe1603.portfolio_api.security.TokenBlacklist;
import io.jsonwebtoken.Claims;
import java.time.Instant;
import java.util.Date;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({ApiExceptionHandler.class, StorageProperties.class})
class AuthSessionFlowTest {

    private static final UUID SESSION_UUID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

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

    private User admin() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("admin@pepe.dev");
        user.setRole(UserRole.ADMIN);
        user.setTokenVersion(1);
        return user;
    }

    private Claims refreshClaims() {
        Claims claims = org.mockito.Mockito.mock(Claims.class);
        given(claims.getId()).willReturn("jti-refresh");
        given(claims.getSubject()).willReturn("admin@pepe.dev");
        given(claims.getExpiration()).willReturn(Date.from(Instant.now().plusSeconds(600)));
        given(claims.get("tv", Integer.class)).willReturn(1);
        given(claims.get("typ", String.class)).willReturn("refresh");
        given(claims.get("sid", String.class)).willReturn(SESSION_UUID.toString());
        return claims;
    }

    private AuthSession activeSession(User user) {
        AuthSession session = new AuthSession();
        session.setSessionId(SESSION_UUID);
        session.setUserId(user.getId());
        session.setRefreshJti("jti-refresh");
        session.setCreatedAt(Instant.now().minusSeconds(60));
        session.setLastSeenAt(Instant.now().minusSeconds(60));
        session.setExpiresAt(Instant.now().plusSeconds(600));
        return session;
    }

    @Test
    void loginRegistraSesionConJtiYEmiteTokensConSid() throws Exception {
        User user = admin();
        given(jwtProperties.refreshTtl()).willReturn(java.time.Duration.ofDays(7));
        given(jwtProperties.accessTtl()).willReturn(java.time.Duration.ofMinutes(15));
        given(jwtProperties.getRefreshCookie()).willReturn("refresh_token");
        given(jwtProperties.isRefreshCookieSecure()).willReturn(false);
        given(loginRateLimiter.isBlocked(anyString(), anyString())).willReturn(false);
        given(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .willReturn(new UsernamePasswordAuthenticationToken(new AppUserDetails(user), null,
                        java.util.List.of()));
        given(jwtTokenService.issueTokenPair(any(), any(), anyString())).willReturn(
                new JwtTokenService.TokenPair("access-1", "refresh-1"));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@pepe.dev\",\"password\":\"secret\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<AuthSession> captor = ArgumentCaptor.forClass(AuthSession.class);
        verify(authSessionRepository).save(captor.capture());
        AuthSession saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(user.getId());
        assertThat(saved.getRefreshJti()).isNotBlank();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getExpiresAt()).isAfter(saved.getCreatedAt());

        verify(jwtTokenService).issueTokenPair(any(), eq(saved.getSessionId().toString()),
                eq(saved.getRefreshJti()));
    }

    @Test
    void refreshRotaJtiReblacklisteaElViejoYActualizaLastSeen() throws Exception {
        User user = admin();
        Claims claims = refreshClaims();
        given(jwtProperties.refreshTtl()).willReturn(java.time.Duration.ofDays(7));
        given(jwtProperties.getRefreshCookie()).willReturn("refresh_token");
        given(jwtProperties.isRefreshCookieSecure()).willReturn(false);
        given(tokenBlacklist.isRevoked(anyString())).willReturn(false);
        given(jwtTokenService.parseRefreshToken("old-token")).willReturn(claims);
        given(userRepository.findByEmail("admin@pepe.dev")).willReturn(Optional.of(user));
        given(authSessionRepository.findBySessionId(SESSION_UUID)).willReturn(Optional.of(activeSession(user)));
        given(jwtTokenService.issueTokenPair(any(), any(), anyString())).willReturn(
                new JwtTokenService.TokenPair("access-2", "refresh-2"));

        mockMvc.perform(post("/auth/refresh").cookie(new jakarta.servlet.http.Cookie("refresh_token", "old-token")))
                .andExpect(status().isOk());

        verify(tokenBlacklist).revoke(eq("jti-refresh"), any());
        ArgumentCaptor<AuthSession> captor = ArgumentCaptor.forClass(AuthSession.class);
        verify(authSessionRepository).save(captor.capture());
        AuthSession saved = captor.getValue();
        assertThat(saved.getRefreshJti()).isNotEqualTo("jti-refresh");
        assertThat(saved.getLastSeenAt()).isAfter(Instant.now().minusSeconds(2));
    }
}