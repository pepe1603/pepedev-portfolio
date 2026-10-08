package dev.pepe1603.api.dto.admin;

import dev.pepe1603.api.entity.GalleryImage;
import dev.pepe1603.api.validation.LocalizedNonBlank;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record ProjectRequest(
        @NotBlank(message = "El slug es obligatorio")
        @Size(max = 120, message = "El slug no puede superar 120 caracteres")
        @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$", message = "El slug solo admite minúsculas, números y guiones")
        String slug,
        @LocalizedNonBlank
        Map<String, String> title,
        Map<String, String> subtitle,
        Map<String, String> summary,
        Map<String, String> descriptionMd,
        @Size(max = 255, message = "La URL de la miniatura no puede superar 255 caracteres")
        String thumbnailUrl,
        @Size(max = 50, message = "La galería no puede superar 50 imágenes")
        List<GalleryImage> gallery,
        @Size(max = 255, message = "La URL del repositorio no puede superar 255 caracteres")
        String repoUrl,
        @Size(max = 255, message = "La URL de la demo no puede superar 255 caracteres")
        String demoUrl,
        @Size(max = 50, message = "El stack no puede superar 50 tecnologías")
        List<String> stack,
        LocalDate periodStart,
        LocalDate periodEnd,
        boolean featured,
        Integer sortOrder) {
}