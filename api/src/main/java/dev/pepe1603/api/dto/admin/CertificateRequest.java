package dev.pepe1603.api.dto.admin;

import dev.pepe1603.api.enums.CertificateKind;
import dev.pepe1603.api.validation.LocalizedNonBlank;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.Map;

public record CertificateRequest(
        @LocalizedNonBlank
        Map<String, String> title,
        @NotBlank(message = "El emisor es obligatorio")
        @Size(max = 120, message = "El emisor no puede superar 120 caracteres")
        String issuer,
        @NotNull(message = "El tipo es obligatorio")
        CertificateKind kind,
        LocalDate issueDate,
        LocalDate expiryDate,
        @Size(max = 255, message = "La URL de la credencial no puede superar 255 caracteres")
        String credentialUrl,
        @Size(max = 255, message = "La URL de la imagen no puede superar 255 caracteres")
        String imageUrl,
        boolean featured,
        Integer sortOrder) {
}