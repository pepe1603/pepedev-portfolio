package dev.pepe1603.portfolio_api.dto.auth;

import java.time.Instant;
import java.util.UUID;

public record SessionResponse(
        UUID id,
        Instant createdAt,
        Instant lastSeenAt,
        Instant expiresAt,
        String ipAddress,
        String userAgent,
        boolean current) {
}