package dev.pepe1603.portfolio_api.dto.common;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ErrorField",
        description = "Campo que falló la validación (o error global de objeto si field es null)")
public record ErrorField(
        @Schema(description = "Nombre del campo; null si es un error global de objeto",
                example = "email")
        String field,
        @Schema(description = "Mensaje de validación en español",
                example = "El email es obligatorio")
        String message) {
}