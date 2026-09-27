package dev.pepe1603.portfolio_api.web;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.pepe1603.portfolio_api.config.StorageProperties;
import dev.pepe1603.portfolio_api.exception.ApiExceptionHandler;
import dev.pepe1603.portfolio_api.controller.AuthController;
import dev.pepe1603.portfolio_api.entity.AuthSession;
import dev.pepe1603.portfolio_api.entity.User;
import dev.pepe1603.portfolio_api.enums.AuditAction;
import dev.pepe1603.portfolio_api.enums.AuditResource;
import dev.pepe1603.portfolio_api.enums.UserRole;
import dev.pepe1603.portfolio_api.repository.AuthSessionRepository;
import dev.pepe1603.portfolio_api.repository.UserRepository;
import dev.pepe1603.portfolio_api.security.JwtProperties;
import dev.pepe1603.portfolio_api.security.JwtTokenService;
import dev.pepe1603.portfolio_api.security.LoginRateLimiter;
import dev.pepe1603.portfolio_api.security.RateLimitProperties;
import dev.pepe1603.portfolio_api.security.TokenBlacklist;
import dev.pepe1603.portfolio_api.security.ResetRateLimiter;
import dev.pepe1603.portfolio_api.service.AuditService;
import dev.pepe1603.portfolio_api.service.PasswordResetService;
import io.jsonwebtoken.Claims;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({ApiExceptionHandler.class, StorageProperties.class})
class AuthSessionControlTest {

    private static final UUID CURRENT = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final UUID OTHER = UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8");

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

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "admin@pepe.dev", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        given(jwtProperties.getRefreshCookie()).willReturn("refresh_token");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private User admin() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("admin@pepe.dev");
        user.setRole(UserRole.ADMIN);
        user.setTokenVersion(1);
        return user;
    }

    private AuthSession session(UUID id, String jti) {
        AuthSession s = new AuthSession();
        s.setSessionId(id);
        s.setUserId(admin().getId());
        s.setRefreshJti(jti);
        s.setCreatedAt(Instant.now().minusSeconds(60));
        s.setLastSeenAt(Instant.now());
        s.setExpiresAt(Instant.now().plusSeconds(600));
        s.setIpAddress("127.0.0.1");
        s.setUserAgent("curl/8.0");
        return s;
    }

    private void stubAccessSid(String sid) {
        Claims claims = org.mockito.Mockito.mock(Claims.class);
        given(claims.getSubject()).willReturn("admin@pepe.dev");
        given(claims.get("sid", String.class)).willReturn(sid);
        given(jwtTokenService.parseAccessToken("access-token")).willReturn(claims);
    }

    @Test
    void sessionsListaActivasConPruneOportunistaYMarcaActual() throws Exception {
        given(userRepository.findByEmail("admin@pepe.dev")).willReturn(Optional.of(admin()));
        stubAccessSid(CURRENT.toString());
        given(authSessionRepository.findAllByUserIdAndRevokedAtIsNullOrderByLastSeenAtDesc(any()))
                .willReturn(List.of(session(CURRENT, "jti-current"), session(OTHER, "jti-other")));

        mockMvc.perform(get("/auth/sessions")
                        .header("Authorization", "Bearer access-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].current").value(true))
                .andExpect(jsonPath("$[1].current").value(false))
                .andExpect(jsonPath("$[0].id").value(CURRENT.toString()))
                .andExpect(jsonPath("$[0].ipAddress").value("127.0.0.1"));

        verify(authSessionRepository).prune(any());
    }

    @Test
    void revokeSesionMarcaRevocadaYBlacklisteaJtiYAudita() throws Exception {
        AuthSession target = session(OTHER, "jti-other");
        given(authSessionRepository.findBySessionId(OTHER)).willReturn(Optional.of(target));

        mockMvc.perform(post("/auth/sessions/" + OTHER + "/revoke"))
                .andExpect(status().isNoContent());

        verify(authSessionRepository).save(any());
        org.assertj.core.api.Assertions.assertThat(target.getRevokedAt()).isNotNull();
        verify(tokenBlacklist).revoke(eq("jti-other"), any());
        verify(auditService).record(eq(AuditAction.REVOKE), eq(AuditResource.SESSION), eq(OTHER), anyString());
    }

    @Test
    void revokeSesionInexistenteEsIdempotente() throws Exception {
        given(authSessionRepository.findBySessionId(OTHER)).willReturn(Optional.empty());

        mockMvc.perform(post("/auth/sessions/" + OTHER + "/revoke"))
                .andExpect(status().isNoContent());

        verify(authSessionRepository, never()).save(any());
        verify(tokenBlacklist, never()).revoke(anyString(), any());
    }

    @Test
    void revokeOthersSaltaLaSesionActual() throws Exception {
        given(userRepository.findByEmail("admin@pepe.dev")).willReturn(Optional.of(admin()));
        stubAccessSid(CURRENT.toString());
        given(authSessionRepository.findAllByUserIdAndRevokedAtIsNullOrderByLastSeenAtDesc(any()))
                .willReturn(List.of(session(CURRENT, "jti-current"), session(OTHER, "jti-other")));

        mockMvc.perform(post("/auth/sessions/revoke-others")
                        .header("Authorization", "Bearer access-token"))
                .andExpect(status().isNoContent());

        verify(tokenBlacklist).revoke(eq("jti-other"), any());
        verify(tokenBlacklist, never()).revoke(eq("jti-current"), any());
        verify(auditService).record(eq(AuditAction.REVOKE), eq(AuditResource.SESSION), eq(null), anyString());
    }

    @Test
    void logoutAllBumpaTvRevocaTodasYAclaraCookie() throws Exception {
        User user = admin();
        stubAccessSid(CURRENT.toString());
        given(userRepository.findByEmail("admin@pepe.dev")).willReturn(Optional.of(user));
        given(authSessionRepository.findAllByUserIdAndRevokedAtIsNullOrderByLastSeenAtDesc(any()))
                .willReturn(List.of(session(CURRENT, "jti-current"), session(OTHER, "jti-other")));

        mockMvc.perform(post("/auth/logout-all").header("Authorization", "Bearer access-token"))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("Max-Age=0")));

        org.assertj.core.api.Assertions.assertThat(user.getTokenVersion()).isEqualTo(2);
        verify(userRepository).save(eq(user));
        verify(tokenBlacklist).revoke(eq("jti-current"), any());
        verify(tokenBlacklist).revoke(eq("jti-other"), any());
        verify(auditService).record(eq(AuditAction.REVOKE), eq(AuditResource.SESSION), eq(null), anyString());
    }
}