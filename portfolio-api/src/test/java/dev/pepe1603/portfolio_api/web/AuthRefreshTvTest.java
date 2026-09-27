package dev.pepe1603.portfolio_api.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.pepe1603.portfolio_api.config.StorageProperties;
import dev.pepe1603.portfolio_api.exception.ApiExceptionHandler;
import dev.pepe1603.portfolio_api.controller.AuthController;
import dev.pepe1603.portfolio_api.entity.AuthSession;
import dev.pepe1603.portfolio_api.entity.User;
import dev.pepe1603.portfolio_api.enums.UserRole;
import dev.pepe1603.portfolio_api.repository.AuthSessionRepository;
import dev.pepe1603.portfolio_api.repository.UserRepository;
import dev.pepe1603.portfolio_api.service.AuditService;
import dev.pepe1603.portfolio_api.security.JwtProperties;
import dev.pepe1603.portfolio_api.security.JwtTokenService;
import dev.pepe1603.portfolio_api.security.LoginRateLimiter;
import dev.pepe1603.portfolio_api.security.RateLimitProperties;
import dev.pepe1603.portfolio_api.security.TokenBlacklist;
import io.jsonwebtoken.Claims;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({ApiExceptionHandler.class, StorageProperties.class})
class AuthRefreshTvTest {

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
        user.setEmail("admin@pepe.dev");
        user.setRole(UserRole.ADMIN);
        user.setTokenVersion(1);
        return user;
    }

    private Claims refreshClaims(int tv) {
        Claims claims = org.mockito.Mockito.mock(Claims.class);
        given(claims.getId()).willReturn("jti-refresh");
        given(claims.getSubject()).willReturn("admin@pepe.dev");
        given(claims.getExpiration()).willReturn(Date.from(Instant.now().plusSeconds(600)));
        given(claims.get("tv", Integer.class)).willReturn(tv);
        given(claims.get("typ", String.class)).willReturn("refresh");
        given(claims.get("sid", String.class)).willReturn("550e8400-e29b-41d4-a716-446655440000");
        return claims;
    }

    private AuthSession adminSession() {
        AuthSession session = new AuthSession();
        session.setSessionId(java.util.UUID.fromString("550e8400-e29b-41d4-a716-446655440000"));
        session.setUserId(java.util.UUID.randomUUID());
        session.setRefreshJti("jti-refresh");
        session.setCreatedAt(Instant.now().minusSeconds(60));
        session.setLastSeenAt(Instant.now().minusSeconds(60));
        session.setExpiresAt(Instant.now().plusSeconds(600));
        return session;
    }

    private void stubCommon(String cookieName) {
        given(jwtProperties.refreshTtl()).willReturn(java.time.Duration.ofDays(7));
        given(jwtProperties.getRefreshCookie()).willReturn(cookieName);
        given(jwtProperties.isRefreshCookieSecure()).willReturn(false);
        given(tokenBlacklist.isRevoked(anyString())).willReturn(false);
    }

    @Test
    void refreshValidoConTvCoincidenteRotaTokens() throws Exception {
        stubCommon("refresh_token");
        Claims claims = refreshClaims(1);
        given(jwtTokenService.parseRefreshToken("abc")).willReturn(claims);
        given(userRepository.findByEmail("admin@pepe.dev")).willReturn(Optional.of(admin()));
        given(authSessionRepository.findBySessionId(java.util.UUID.fromString("550e8400-e29b-41d4-a716-446655440000")))
                .willReturn(Optional.of(adminSession()));
        given(jwtTokenService.issueTokenPair(any(), any(), any())).willReturn(
                new JwtTokenService.TokenPair("new-access", "new-refresh"));

        mockMvc.perform(post("/auth/refresh").cookie(new jakarta.servlet.http.Cookie("refresh_token", "abc")))
                .andExpect(status().isOk());
    }

    @Test
    void refreshConTvDesactualizadoDevuelve401() throws Exception {
        stubCommon("refresh_token");
        Claims claims = refreshClaims(0);
        given(jwtTokenService.parseRefreshToken("abc")).willReturn(claims);
        given(userRepository.findByEmail("admin@pepe.dev")).willReturn(Optional.of(admin()));

        mockMvc.perform(post("/auth/refresh").cookie(new jakarta.servlet.http.Cookie("refresh_token", "abc")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshConSesionRevocadaDevuelve401() throws Exception {
        stubCommon("refresh_token");
        Claims claims = refreshClaims(1);
        given(jwtTokenService.parseRefreshToken("abc")).willReturn(claims);
        given(userRepository.findByEmail("admin@pepe.dev")).willReturn(Optional.of(admin()));
        AuthSession session = adminSession();
        session.setRevokedAt(Instant.now());
        given(authSessionRepository.findBySessionId(java.util.UUID.fromString("550e8400-e29b-41d4-a716-446655440000")))
                .willReturn(Optional.of(session));

        mockMvc.perform(post("/auth/refresh").cookie(new jakarta.servlet.http.Cookie("refresh_token", "abc")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshSinSesionDevuelve401() throws Exception {
        stubCommon("refresh_token");
        Claims claims = refreshClaims(1);
        given(jwtTokenService.parseRefreshToken("abc")).willReturn(claims);
        given(userRepository.findByEmail("admin@pepe.dev")).willReturn(Optional.of(admin()));
        given(authSessionRepository.findBySessionId(any())).willReturn(Optional.empty());

        mockMvc.perform(post("/auth/refresh").cookie(new jakarta.servlet.http.Cookie("refresh_token", "abc")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshSinCookieDevuelve401() throws Exception {
        stubCommon("refresh_token");

        mockMvc.perform(post("/auth/refresh"))
                .andExpect(status().isUnauthorized());
    }
}