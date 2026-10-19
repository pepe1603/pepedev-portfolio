package dev.pepe1603.api.dto.admin;

import java.time.Instant;

/**
 * Un objeto del bucket tal como lo ve el panel de Medios.
 *
 * <p>{@code key} es el nombre del objeto ({@code <uuid>.<ext>}) y {@code url} la URL pública
 * con la que se sirve, que es la misma forma que se guarda en la base de datos. El resto es
 * lo que S3 devuelve en un listado y sirve para pintar la rejilla: tamaño, fecha y tipo.
 */
public record StorageObjectResponse(
        String key,
        String url,
        long size,
        Instant lastModified,
        String contentType) {
}
