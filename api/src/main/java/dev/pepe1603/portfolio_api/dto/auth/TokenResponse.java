package dev.pepe1603.portfolio_api.dto.auth;

public record TokenResponse(String accessToken, String tokenType, long expiresIn) {
}