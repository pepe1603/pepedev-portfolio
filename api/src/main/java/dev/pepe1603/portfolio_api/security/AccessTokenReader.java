package dev.pepe1603.portfolio_api.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

@Component
public class AccessTokenReader {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenService jwtTokenService;

    public AccessTokenReader(JwtTokenService jwtTokenService) {
        this.jwtTokenService = jwtTokenService;
    }

    public Optional<Claims> read(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return Optional.empty();
        }
        try {
            return Optional.of(jwtTokenService.parseAccessToken(header.substring(BEARER_PREFIX.length())));
        } catch (JwtException e) {
            return Optional.empty();
        }
    }
}