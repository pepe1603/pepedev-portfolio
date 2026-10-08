package dev.pepe1603.portfolio_api.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record OtpResendRequest(
        @NotBlank(message = "El challengeId es obligatorio")
        String challengeId) {
}
