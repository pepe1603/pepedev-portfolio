package dev.pepe1603.api.dto.auth;

public record TokenResponse(String accessToken, String tokenType, long expiresIn) {
}