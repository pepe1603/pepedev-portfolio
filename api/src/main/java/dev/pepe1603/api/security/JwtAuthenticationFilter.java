package dev.pepe1603.api.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenService jwtTokenService;
    private final TokenBlacklist tokenBlacklist;

    public JwtAuthenticationFilter(JwtTokenService jwtTokenService, TokenBlacklist tokenBlacklist) {
        this.jwtTokenService = jwtTokenService;
        this.tokenBlacklist = tokenBlacklist;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }
        String token = header.substring(BEARER_PREFIX.length());
        try {
            Claims claims = jwtTokenService.parseAccessToken(token);
            if (!tokenBlacklist.isRevoked(claims.getId())) {
                var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + claims.get("role", String.class)));
                var authentication = new UsernamePasswordAuthenticationToken(claims.getSubject(), null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (JwtException | IllegalArgumentException e) {
            SecurityContextHolder.clearContext();
        }
        filterChain.doFilter(request, response);
    }
}