package dev.pepe1603.portfolio_api.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record OtpLoginVerifyRequest(
        @NotBlank(message = "El challengeId es obligatorio")
        String challengeId,
        @NotBlank(message = "El código es obligatorio")
        String code) {
}