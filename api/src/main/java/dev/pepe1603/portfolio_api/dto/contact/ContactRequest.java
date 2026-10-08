package dev.pepe1603.portfolio_api.dto.contact;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ContactRequest(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 120, message = "El nombre no puede superar 120 caracteres")
        String name,
        @NotBlank(message = "El email es obligatorio")
        @Email(message = "Email inválido")
        @Size(max = 320, message = "El email no puede superar 320 caracteres")
        String email,
        @NotBlank(message = "El asunto es obligatorio")
        @Size(max = 160, message = "El asunto no puede superar 160 caracteres")
        String subject,
        @NotBlank(message = "El mensaje es obligatorio")
        @Size(min = 10, max = 5000, message = "El mensaje debe tener entre 10 y 5000 caracteres")
        String body,
        String website) {
}