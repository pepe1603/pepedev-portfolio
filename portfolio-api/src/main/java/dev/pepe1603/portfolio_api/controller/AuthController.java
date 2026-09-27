package dev.pepe1603.portfolio_api.controller;

import dev.pepe1603.portfolio_api.dto.auth.LoginRequest;
import dev.pepe1603.portfolio_api.dto.auth.MeResponse;
import dev.pepe1603.portfolio_api.dto.auth.TokenResponse;
import dev.pepe1603.portfolio_api.dto.common.ApiProblemDetail;
import dev.pepe1603.portfolio_api.entity.User;
import dev.pepe1603.portfolio_api.repository.UserRepository;
import dev.pepe1603.portfolio_api.security.AppUserDetails;
import dev.pepe1603.portfolio_api.security.JwtProperties;
import dev.pepe1603.portfolio_api.security.JwtTokenService;
import dev.pepe1603.portfolio_api.exception.LoginRateLimitedException;
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
import java.util.function.Function;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
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
    private final JwtTokenService jwtTokenService;
    private final JwtProperties jwtProperties;
    private final RateLimitProperties rateLimitProperties;
    private final LoginRateLimiter loginRateLimiter;
    private final TokenBlacklist tokenBlacklist;

    public AuthController(AuthenticationManager authenticationManager, UserRepository userRepository,
            JwtTokenService jwtTokenService, JwtProperties jwtProperties, RateLimitProperties rateLimitProperties,
            LoginRateLimiter loginRateLimiter, TokenBlacklist tokenBlacklist) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.jwtTokenService = jwtTokenService;
        this.jwtProperties = jwtProperties;
        this.rateLimitProperties = rateLimitProperties;
        this.loginRateLimiter = loginRateLimiter;
        this.tokenBlacklist = tokenBlacklist;
    }

    @PostMapping("/login")
    @Operation(summary = "Iniciar sesión",
            description = "Valida credenciales y emite access token (body) + refresh token (cookie HttpOnly SameSite=Lax). "
                    + "Sujeto a rate limit por IP y email (429 con Retry-After).")
    @ApiResponse(responseCode = "200", description = "Tokens emitidos",
            content = @Content(schema = @Schema(implementation = TokenResponse.class)))
    @ApiResponse(responseCode = "400", description = "Campos obligatorios ausentes o inválidos",
            content = @Content(schema = @Schema(implementation = ApiProblemDetail.class)))
    @ApiResponse(responseCode = "401", description = "Credenciales inválidas",
            content = @Content(schema = @Schema(implementation = ApiProblemDetail.class)))
    @ApiResponse(responseCode = "429", description = "Demasiados intentos (Retry-After en segundos)",
            content = @Content(schema = @Schema(implementation = ApiProblemDetail.class)))
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request,
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
            user.setLastLoginAt(Instant.now());
            userRepository.save(user);

            JwtTokenService.TokenPair tokens = jwtTokenService.issueTokenPair(user);
            setRefreshCookie(response, tokens.refreshToken(), jwtProperties.refreshTtl());
            loginRateLimiter.onSuccess(ip, request.email());
            return ResponseEntity.ok(new TokenResponse(tokens.accessToken(), "Bearer",
                    jwtProperties.accessTtl().toSeconds()));
        } catch (BadCredentialsException e) {
            loginRateLimiter.recordFailure(ip, request.email());
            throw e;
        }
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
        JwtTokenService.TokenPair tokens = jwtTokenService.issueTokenPair(user);
        revokeToken(claims);
        setRefreshCookie(response, tokens.refreshToken(), jwtProperties.refreshTtl());
        return ResponseEntity.ok(new TokenResponse(tokens.accessToken(), "Bearer",
                jwtProperties.accessTtl().toSeconds()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        String cookieToken = extractRefreshCookie(request);
        if (cookieToken != null && !cookieToken.isBlank()) {
            revokeTokenQuietly(jwtTokenService::parseRefreshToken, cookieToken);
        }
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            revokeTokenQuietly(jwtTokenService::parseAccessToken, header.substring(BEARER_PREFIX.length()));
        }
        clearRefreshCookie(response);
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
}