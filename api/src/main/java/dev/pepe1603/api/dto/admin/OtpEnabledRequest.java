package dev.pepe1603.api.dto.admin;

import jakarta.validation.constraints.NotNull;

public record OtpEnabledRequest(
        @NotNull(message = "El campo enabled es obligatorio")
        Boolean enabled) {
}