package dev.pepe1603.portfolio_api.controller;

import dev.pepe1603.portfolio_api.dto.auth.LoginRequest;
import dev.pepe1603.portfolio_api.dto.auth.MeResponse;
import dev.pepe1603.portfolio_api.dto.auth.OtpChallengeResponse;
import dev.pepe1603.portfolio_api.dto.auth.OtpLoginVerifyRequest;
import dev.pepe1603.portfolio_api.dto.auth.OtpResendRequest;
import dev.pepe1603.portfolio_api.dto.auth.PasswordResetConfirm;
import dev.pepe1603.portfolio_api.dto.auth.PasswordResetRequest;
import dev.pepe1603.portfolio_api.dto.auth.SessionResponse;
import dev.pepe1603.portfolio_api.dto.auth.TokenResponse;
import dev.pepe1603.portfolio_api.dto.common.ApiProblemDetail;
import dev.pepe1603.portfolio_api.entity.AuthSession;
import dev.pepe1603.portfolio_api.entity.User;
import dev.pepe1603.portfolio_api.enums.AuditAction;
import dev.pepe1603.portfolio_api.enums.AuditResource;
import dev.pepe1603.portfolio_api.repository.AuthSessionRepository;
import dev.pepe1603.portfolio_api.repository.UserRepository;
import dev.pepe1603.portfolio_api.security.AccessTokenReader;
import dev.pepe1603.portfolio_api.security.AppUserDetails;
import dev.pepe1603.portfolio_api.security.JwtProperties;
import dev.pepe1603.portfolio_api.security.JwtTokenService;
import dev.pepe1603.portfolio_api.security.OtpProperties;
import dev.pepe1603.portfolio_api.security.ResetRateLimiter;
import dev.pepe1603.portfolio_api.service.AuditService;
import dev.pepe1603.portfolio_api.service.OtpService;
import dev.pepe1603.portfolio_api.service.PasswordResetService;
import dev.pepe1603.portfolio_api.service.SecurityNotificationService;
import dev.pepe1603.portfolio_api.exception.LoginRateLimitedException;
import dev.pepe1603.portfolio_api.exception.OtpResendTooSoonException;
import dev.pepe1603.portfolio_api.security.LoginRateLimiter;
import dev.pepe1603.portfolio_api.security.RateLimitProperties;
import dev.pepe1603.portfolio_api.security.TokenBlacklist;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final AuthSessionRepository authSessionRepository;
    private final JwtTokenService jwtTokenService;
    private final JwtProperties jwtProperties;
    private final RateLimitProperties rateLimitProperties;
    private final LoginRateLimiter loginRateLimiter;
    private final TokenBlacklist tokenBlacklist;
    private final AuditService auditService;
    private final PasswordResetService passwordResetService;
    private final ResetRateLimiter resetRateLimiter;
    private final OtpService otpService;
    private final OtpProperties otpProperties;
    private final SecurityNotificationService securityNotificationService;
    private final AccessTokenReader accessTokenReader;
    private final PasswordEncoder passwordEncoder;

    public AuthController(AuthenticationManager authenticationManager, UserRepository userRepository,
            AuthSessionRepository authSessionRepository, JwtTokenService jwtTokenService, JwtProperties jwtProperties,
            RateLimitProperties rateLimitProperties, LoginRateLimiter loginRateLimiter, TokenBlacklist tokenBlacklist,
            AuditService auditService, PasswordResetService passwordResetService, ResetRateLimiter resetRateLimiter,
            OtpService otpService, OtpProperties otpProperties,
            SecurityNotificationService securityNotificationService, AccessTokenReader accessTokenReader,
            PasswordEncoder passwordEncoder) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.authSessionRepository = authSessionRepository;
        this.jwtTokenService = jwtTokenService;
        this.jwtProperties = jwtProperties;
        this.rateLimitProperties = rateLimitProperties;
        this.loginRateLimiter = loginRateLimiter;
        this.tokenBlacklist = tokenBlacklist;
        this.auditService = auditService;
        this.passwordResetService = passwordResetService;
        this.resetRateLimiter = resetRateLimiter;
        this.otpService = otpService;
        this.otpProperties = otpProperties;
        this.securityNotificationService = securityNotificationService;
        this.accessTokenReader = accessTokenReader;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/login")
    @Operation(summary = "Iniciar sesión",
            description = "Valida credenciales y emite access token (body) + refresh token (cookie HttpOnly SameSite=Lax). "
                    + "Sujeto a rate limit por IP y email (429 con Retry-After). "
                    + "Si el OTP está activo para el usuario, responde 202 con un challengeId "
                    + "y el token se obtiene en /auth/login/verify.")
    @ApiResponse(responseCode = "200", description = "Tokens emitidos",
            content = @Content(schema = @Schema(implementation = TokenResponse.class)))
    @ApiResponse(responseCode = "202", description = "Credenciales correctas; se requiere verificación OTP",
            content = @Content(schema = @Schema(implementation = OtpChallengeResponse.class)))
    @ApiResponse(responseCode = "400", description = "Campos obligatorios ausentes o inválidos",
            content = @Content(schema = @Schema(implementation = ApiProblemDetail.class)))
    @ApiResponse(responseCode = "401", description = "Credenciales inválidas",
            content = @Content(schema = @Schema(implementation = ApiProblemDetail.class)))
    @ApiResponse(responseCode = "429", description = "Demasiados intentos (Retry-After en segundos)",
            content = @Content(schema = @Schema(implementation = ApiProblemDetail.class)))
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest, HttpServletResponse response) {
        String ip = servletRequest.getRemoteAddr();
        if (loginRateLimiter.isBlocked(ip, request.email())) {
            throw new LoginRateLimitedException(rateLimitProperties.getWindowSeconds());
        }
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password()));
            AppUserDetails details = (AppUserDetails) authentication.getPrincipal();
            User user = details.getUser();
            loginRateLimiter.onSuccess(ip, request.email());
            if (otpProperties.isEnabled() && Boolean.TRUE.equals(user.getOtpEnabled())) {
                String challengeId = otpService.createChallenge(user.getEmail(), servletRequest.getLocale());
                return ResponseEntity.accepted().body(new OtpChallengeResponse(challengeId));
            }
            return issueSessionAndTokens(user, ip, servletRequest.getHeader(HttpHeaders.USER_AGENT),
                    servletRequest.getLocale(), response);
        } catch (BadCredentialsException e) {
            loginRateLimiter.recordFailure(ip, request.email());
            throw e;
        }
    }

    @PostMapping("/login/verify")
    @Operation(summary = "Verificar el código OTP del login",
            description = "Consume el challenge de un solo uso (5 min, máx. 5 intentos) y emite los tokens. "
                    + "En este punto se registra la sesión y se actualiza last_login_at.")
    @ApiResponse(responseCode = "200", description = "Tokens emitidos",
            content = @Content(schema = @Schema(implementation = TokenResponse.class)))
    @ApiResponse(responseCode = "400", description = "Campos obligatorios ausentes o inválidos",
            content = @Content(schema = @Schema(implementation = ApiProblemDetail.class)))
    @ApiResponse(responseCode = "401", description = "Código o challenge inválido/caducado/agotado",
            content = @Content(schema = @Schema(implementation = ApiProblemDetail.class)))
    public ResponseEntity<TokenResponse> loginVerify(@Valid @RequestBody OtpLoginVerifyRequest request,
            HttpServletRequest servletRequest, HttpServletResponse response) {
        User user = otpService.verify(request.challengeId(), request.code())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Código de verificación inválido o caducado"));
        return issueSessionAndTokens(user, servletRequest.getRemoteAddr(),
                servletRequest.getHeader(HttpHeaders.USER_AGENT), servletRequest.getLocale(), response);
    }

    private ResponseEntity<TokenResponse> issueSessionAndTokens(User user, String ip, String userAgent, Locale locale,
            HttpServletResponse response) {
        Instant loginAt = Instant.now();
        user.setLastLoginAt(loginAt);
        userRepository.save(user);

        // Antes de guardar la sesión: el aviso de "acceso nuevo" compara con el histórico y
        // encontraría la sesión que aún no existe como si ya fuera conocida.
        securityNotificationService.notifyNewLogin(user, ip, userAgent, locale);

        String refreshJti = UUID.randomUUID().toString();
        UUID sessionId = UUID.randomUUID();
        AuthSession session = new AuthSession();
        session.setSessionId(sessionId);
        session.setUserId(user.getId());
        session.setRefreshJti(refreshJti);
        session.setCreatedAt(loginAt);
        session.setLastSeenAt(loginAt);
        session.setExpiresAt(loginAt.plus(jwtProperties.refreshTtl()));
        session.setIpAddress(ip);
        session.setUserAgent(truncate(userAgent, 255));
        authSessionRepository.save(session);

        JwtTokenService.TokenPair tokens = jwtTokenService.issueTokenPair(user, sessionId.toString(), refreshJti);
        setRefreshCookie(response, tokens.refreshToken(), jwtProperties.refreshTtl());
        return ResponseEntity.ok(new TokenResponse(tokens.accessToken(), "Bearer",
                jwtProperties.accessTtl().toSeconds()));
    }

    @PostMapping("/login/resend")
    @Operation(summary = "Reenviar el código OTP del login",
            description = "Genera un código nuevo para el challenge indicado y lo vuelve a enviar por email. "
                    + "El código anterior deja de valer y el challenge no se alarga: el login sigue teniendo "
                    + "5 min en total (APP_AUTH_OTP_TTL), no 5 min por reenvío. Responde 202 siempre que el "
                    + "correo se haya encolado; el envío es best-effort, sin reintentos ni cola.")
    @ApiResponse(responseCode = "202", description = "Reenvío encolado",
            content = @Content(schema = @Schema(implementation = ApiProblemDetail.class)))
    @ApiResponse(responseCode = "400", description = "challengeId ausente",
            content = @Content(schema = @Schema(implementation = ApiProblemDetail.class)))
    @ApiResponse(responseCode = "401", description = "Challenge inválido o caducado: hay que empezar el login otra vez",
            content = @Content(schema = @Schema(implementation = ApiProblemDetail.class)))
    @ApiResponse(responseCode = "429", description = "Reenviado hace poco (Retry-After en segundos)",
            content = @Content(schema = @Schema(implementation = ApiProblemDetail.class)))
    public ResponseEntity<Void> loginResend(@Valid @RequestBody OtpResendRequest request,
            HttpServletRequest servletRequest) {
        OtpService.OtpResendResult result = otpService.resend(request.challengeId(), servletRequest.getLocale());
        if (result.status() == OtpService.OtpResendResult.Status.NOT_FOUND) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Challenge inválido o caducado");
        }
        if (result.status() == OtpService.OtpResendResult.Status.TOO_SOON) {
            throw new OtpResendTooSoonException(result.retryAfterSeconds());
        }
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(HttpServletRequest request, HttpServletResponse response) {
        String token = extractRefreshCookie(request);
        if (token == null || token.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Falta la cookie de refresh");
        }
        Claims claims;
        try {
            claims = jwtTokenService.parseRefreshToken(token);
        } catch (JwtException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token inválido o caducado");
        }
        if (tokenBlacklist.isRevoked(claims.getId())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token revocado");
        }
        User user = userRepository.findByEmail(claims.getSubject())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no encontrado"));
        Integer tokenVersion = claims.get("tv", Integer.class);
        if (tokenVersion == null || !tokenVersion.equals(user.getTokenVersion())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sesión revocada");
        }
        AuthSession session = findActiveSession(claims);
        String refreshJti = UUID.randomUUID().toString();
        JwtTokenService.TokenPair tokens = jwtTokenService.issueTokenPair(user, session.getSessionId().toString(),
                refreshJti);
        revokeToken(claims);
        session.setLastSeenAt(Instant.now());
        session.setExpiresAt(Instant.now().plus(jwtProperties.refreshTtl()));
        session.setRefreshJti(refreshJti);
        authSessionRepository.save(session);
        setRefreshCookie(response, tokens.refreshToken(), jwtProperties.refreshTtl());
        return ResponseEntity.ok(new TokenResponse(tokens.accessToken(), "Bearer",
                jwtProperties.accessTtl().toSeconds()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        String cookieToken = extractRefreshCookie(request);
        if (cookieToken != null && !cookieToken.isBlank()) {
            revokeRefreshSessionQuietly(cookieToken);
            revokeTokenQuietly(jwtTokenService::parseRefreshToken, cookieToken);
        }
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            revokeTokenQuietly(jwtTokenService::parseAccessToken, header.substring(BEARER_PREFIX.length()));
        }
        clearRefreshCookie(response);
        // El logout nunca falla por el aviso: si no se puede resolver el usuario (token caducado,
        // logout desde otra pestaña) simplemente no se notifica.
        optionalUser(request).ifPresent(user -> securityNotificationService.notifyLogout(user,
                request.getRemoteAddr(), request.getHeader(HttpHeaders.USER_AGENT), request.getLocale()));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reset/request")
    @Operation(summary = "Solicitar restablecimiento de contraseña",
            description = "Genera un token de un solo uso (30 min) y lo envía por email. Responde 202 siempre: "
                    + "exista o no la cuenta (para no filtrar qué emails están registrados) y también si el "
                    + "correo no se pudo entregar. El envío es best-effort, sin reintentos ni cola.")
    @ApiResponse(responseCode = "202", description = "Solicitud aceptada",
            content = @Content(schema = @Schema(implementation = ApiProblemDetail.class)))
    @ApiResponse(responseCode = "400", description = "Email ausente o inválido",
            content = @Content(schema = @Schema(implementation = ApiProblemDetail.class)))
    @ApiResponse(responseCode = "429", description = "Demasiadas solicitudes (Retry-After en segundos)",
            content = @Content(schema = @Schema(implementation = ApiProblemDetail.class)))
    public ResponseEntity<Void> resetRequest(@Valid @RequestBody PasswordResetRequest request,
            HttpServletRequest servletRequest) {
        String ip = servletRequest.getRemoteAddr();
        if (resetRateLimiter.isBlocked(ip)) {
            throw new LoginRateLimitedException(rateLimitProperties.getResetWindowSeconds());
        }
        resetRateLimiter.record(ip);
        passwordResetService.requestReset(request.email().strip(), servletRequest.getLocale());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/reset/confirm")
    @Operation(summary = "Confirmar restablecimiento de contraseña",
            description = "Valida el token de un solo uso, actualiza la contraseña, invalida todos los tokens "
                    + "previos (bump de token_version) y revoca las sesiones activas del usuario.")
    @ApiResponse(responseCode = "204", description = "Contraseña actualizada")
    @ApiResponse(responseCode = "400", description = "Email, token o contraseña inválidos",
            content = @Content(schema = @Schema(implementation = ApiProblemDetail.class)))
    public ResponseEntity<Void> resetConfirm(@Valid @RequestBody PasswordResetConfirm request) {
        User user = passwordResetService.consumeUser(request.token(), request.email())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Token de restablecimiento inválido o caducado"));
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        userRepository.save(user);
        revokeAllSessions(user);
        auditService.record(AuditAction.RESET, AuditResource.USER, user.getId(), "password-reset");
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public MeResponse me(Authentication authentication) {
        String role = authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .filter(authority -> authority.startsWith("ROLE_"))
                .map(authority -> authority.substring("ROLE_".length()))
                .findFirst()
                .orElse(null);
        return new MeResponse(authentication.getName(), role);
    }

    @GetMapping("/sessions")
    public List<SessionResponse> sessions(HttpServletRequest request) {
        User user = requireUser(request);
        authSessionRepository.prune(Instant.now());
        String currentSid = currentSid(request);
        return authSessionRepository.findAllByUserIdAndRevokedAtIsNullOrderByLastSeenAtDesc(user.getId())
                .stream()
                .map(session -> toSessionResponse(session, currentSid))
                .toList();
    }

    @PostMapping("/sessions/revoke-others")
    public ResponseEntity<Void> revokeOthers(HttpServletRequest request) {
        User user = requireUser(request);
        String currentSid = currentSid(request);
        List<AuthSession> active =
                authSessionRepository.findAllByUserIdAndRevokedAtIsNullOrderByLastSeenAtDesc(user.getId());
        int revoked = 0;
        for (AuthSession session : active) {
            if (!session.getSessionId().toString().equals(currentSid)) {
                revokeActiveSession(session);
                revoked++;
            }
        }
        if (revoked > 0) {
            auditService.record(AuditAction.REVOKE, AuditResource.SESSION, null, "revoke-others (" + revoked + ")");
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/sessions/{sessionId}/revoke")
    public ResponseEntity<Void> revokeSession(@PathVariable String sessionId) {
        UUID id = parseSessionId(sessionId);
        AuthSession session = authSessionRepository.findBySessionId(id).orElse(null);
        if (session == null || session.getRevokedAt() != null) {
            return ResponseEntity.noContent().build();
        }
        revokeActiveSession(session);
        auditService.record(AuditAction.REVOKE, AuditResource.SESSION, id, "session:" + id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll(HttpServletRequest request, HttpServletResponse response) {
        revokeAllSessions(requireUser(request));
        auditService.record(AuditAction.REVOKE, AuditResource.SESSION, null, "logout-all");
        clearRefreshCookie(response);
        return ResponseEntity.noContent().build();
    }

    private void setRefreshCookie(HttpServletResponse response, String token, Duration ttl) {
        ResponseCookie cookie = ResponseCookie.from(jwtProperties.getRefreshCookie(), token)
                .httpOnly(true)
                .secure(jwtProperties.isRefreshCookieSecure())
                .sameSite("Lax")
                .path("/auth")
                .maxAge(ttl)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(jwtProperties.getRefreshCookie(), "")
                .httpOnly(true)
                .secure(jwtProperties.isRefreshCookieSecure())
                .sameSite("Lax")
                .path("/auth")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private String extractRefreshCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        return Arrays.stream(cookies)
                .filter(cookie -> jwtProperties.getRefreshCookie().equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    private void revokeRefreshSessionQuietly(String token) {
        try {
            Claims claims = jwtTokenService.parseRefreshToken(token);
            authSessionRepository.findByRefreshJti(claims.getId()).ifPresent(session -> {
                if (session.getRevokedAt() == null) {
                    session.setRevokedAt(Instant.now());
                    authSessionRepository.save(session);
                }
            });
        } catch (JwtException ignored) {
        }
    }

    private void revokeTokenQuietly(Function<String, Claims> parser, String token) {
        try {
            revokeToken(parser.apply(token));
        } catch (JwtException ignored) {
        }
    }

    private void revokeToken(Claims claims) {
        long remainingMillis = claims.getExpiration().getTime() - System.currentTimeMillis();
        if (remainingMillis > 0) {
            tokenBlacklist.revoke(claims.getId(), Duration.ofMillis(remainingMillis));
        }
    }

    private AuthSession findActiveSession(Claims claims) {
        String sid = claims.get("sid", String.class);
        if (sid == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sesión revocada");
        }
        AuthSession session;
        try {
            session = authSessionRepository.findBySessionId(UUID.fromString(sid))
                    .orElse(null);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sesión revocada");
        }
        if (session == null || session.getRevokedAt() != null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sesión revocada");
        }
        return session;
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private User requireUser(HttpServletRequest request) {
        String email = accessTokenReader.read(request).map(Claims::getSubject).orElse(null);
        if (email == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token de acceso ausente o inválido");
        }
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no encontrado"));
    }

    private Optional<User> optionalUser(HttpServletRequest request) {
        return accessTokenReader.read(request)
                .map(Claims::getSubject)
                .filter(email -> email != null && !email.isBlank())
                .flatMap(userRepository::findByEmail);
    }

    private String currentSid(HttpServletRequest request) {
        return accessTokenReader.read(request).map(claims -> claims.get("sid", String.class)).orElse(null);
    }

    private UUID parseSessionId(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Identificador de sesión inválido");
        }
    }

    private void revokeActiveSession(AuthSession session) {
        session.setRevokedAt(Instant.now());
        authSessionRepository.save(session);
        long remainingMillis = Duration.between(Instant.now(), session.getExpiresAt()).toMillis();
        if (remainingMillis > 0) {
            tokenBlacklist.revoke(session.getRefreshJti(), Duration.ofMillis(remainingMillis));
        }
    }

    private void revokeAllSessions(User user) {
        user.setTokenVersion(user.getTokenVersion() + 1);
        userRepository.save(user);
        List<AuthSession> active =
                authSessionRepository.findAllByUserIdAndRevokedAtIsNullOrderByLastSeenAtDesc(user.getId());
        for (AuthSession session : active) {
            revokeActiveSession(session);
        }
    }

    private SessionResponse toSessionResponse(AuthSession session, String currentSid) {
        return new SessionResponse(
                session.getSessionId(),
                session.getCreatedAt(),
                session.getLastSeenAt(),
                session.getExpiresAt(),
                session.getIpAddress(),
                session.getUserAgent(),
                session.getSessionId().toString().equals(currentSid));
    }
}