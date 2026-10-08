package dev.pepe1603.portfolio_api.dto.admin;

import dev.pepe1603.portfolio_api.entity.Experience;
import dev.pepe1603.portfolio_api.entity.Skill;
import dev.pepe1603.portfolio_api.validation.LocalizedNonBlank;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import org.hibernate.validator.constraints.URL;

public record ProfileRequest(
        @NotBlank(message = "El nombre completo es obligatorio")
        @Size(max = 120, message = "El nombre completo no puede superar 120 caracteres")
        String fullName,
        @LocalizedNonBlank
        Map<String, String> headline,
        @LocalizedNonBlank
        Map<String, String> bio,
        @Size(max = 120, message = "La ubicación no puede superar 120 caracteres")
        String location,
        @Size(max = 255, message = "La URL no puede superar 255 caracteres")
        @URL(message = "Debe ser una URL válida")
        String githubUrl,
        @Size(max = 255, message = "La URL no puede superar 255 caracteres")
        @URL(message = "Debe ser una URL válida")
        String linkedinUrl,
        @Size(max = 320, message = "El email no puede superar 320 caracteres")
        @Email(message = "Debe ser un email válido")
        String emailPublic,
        @Size(max = 255, message = "La URL no puede superar 255 caracteres")
        @URL(message = "Debe ser una URL válida")
        String websiteUrl,
        @Size(max = 255, message = "La URL no puede superar 255 caracteres")
        @URL(message = "Debe ser una URL válida")
        String cvUrlEs,
        @Size(max = 255, message = "La URL no puede superar 255 caracteres")
        @URL(message = "Debe ser una URL válida")
        String cvUrlEn,
        @Size(max = 255, message = "La URL no puede superar 255 caracteres")
        @URL(message = "Debe ser una URL válida")
        String avatarUrl,
        @Size(max = 50, message = "Las skills no pueden superar 50 elementos")
        List<Skill> skills,
        @Size(max = 50, message = "Las experiencias no pueden superar 50 elementos")
        List<Experience> experiences) {
}