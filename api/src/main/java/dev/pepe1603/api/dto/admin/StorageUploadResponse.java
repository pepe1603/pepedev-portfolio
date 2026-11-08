package dev.pepe1603.api.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Respuesta de POST /admin/storage. {@code url} es la URL pública que hay que guardar en la
 * base de datos; {@code width}/{@code height} son las dimensiones de la imagen, que el panel
 * usa para avisar si no encajan con la recomendación del tipo. En un PDF u otro fichero sin
 * dimensiones van a null.
 */
@Schema(name = "StorageUploadResponse")
public record StorageUploadResponse(
        @Schema(description = "URL pública con la que se sirve el fichero", example = "http://localhost:8080/files/<uuid>.png")
        String url,
        @Schema(description = "Ancho en píxeles; null si no es una imagen legible", example = "400", nullable = true)
        Integer width,
        @Schema(description = "Alto en píxeles; null si no es una imagen legible", example = "400", nullable = true)
        Integer height) {
}
