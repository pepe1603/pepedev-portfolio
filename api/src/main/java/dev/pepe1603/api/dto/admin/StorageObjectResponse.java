package dev.pepe1603.api.dto.admin;

import java.time.Instant;

/**
 * Un objeto del bucket tal como lo ve el panel de Medios.
 *
 * <p>{@code key} es el nombre del objeto ({@code <uuid>.<ext>}) y {@code url} la URL pública
 * con la que se sirve, que es la misma forma que se guarda en la base de datos. El resto es
 * lo que S3 devuelve en un listado y sirve para pintar la rejilla: tamaño, fecha y tipo.
 *
 * <p>{@code use} es el uso declarado al subir ({@code avatar}, {@code cv}...), en minúsculas
 * para que el front lo lea tal cual. Es una etiqueta, no una referencia: puede venir {@code
 * null} si el fichero es anterior a la tabla de usos y nada lo referencia. El front lo usa
 * para filtrar (el modal de avatar solo ofrece avatares), no para decidir borrados.
 */
public record StorageObjectResponse(
        String key,
        String url,
        long size,
        Instant lastModified,
        String contentType,
        String use) {
}
