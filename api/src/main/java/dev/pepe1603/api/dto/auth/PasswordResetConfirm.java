package dev.pepe1603.api.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetConfirm(
        @NotBlank(message = "El email es obligatorio")
        @Email(message = "Email inválido")
        String email,
        @NotBlank(message = "El token es obligatorio")
        String token,
        @NotBlank(message = "La contraseña es obligatoria")
        @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres")
        String password) {
}