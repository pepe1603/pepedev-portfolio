package dev.pepe1603.portfolio_api.dto.common;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(name = "ApiProblemDetail",
        description = "Envelope de error único del API (RFC 9457). title/status/detail/instance; "
                + "el campo type no se serializa (about:blank)")
public record ApiProblemDetail(
        @Schema(description = "Reason phrase en inglés", example = "Bad Request")
        String title,
        @Schema(description = "Código HTTP", example = "400")
        int status,
        @Schema(description = "Mensaje en español, específico del caso",
                example = "Validación fallida: se encontraron 2 errores")
        String detail,
        @Schema(description = "Ruta del request que produjo el error", example = "/auth/login")
        String instance,
        @Schema(description = "Solo en 400 de validación: errores por campo")
        List<ErrorField> errors) {
}