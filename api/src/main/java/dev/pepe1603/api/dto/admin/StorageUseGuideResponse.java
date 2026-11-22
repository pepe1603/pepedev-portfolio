package dev.pepe1603.api.dto.admin;

import dev.pepe1603.api.enums.StorageUse;
import java.util.List;

/**
 * Guía de un uso de subida, para que el panel no repita en el front lo que ya sabe la API.
 * Es la vista pública de {@link StorageUse}: nombre, formatos admitidos y recomendación.
 */
public record StorageUseGuideResponse(
        String use,
        String label,
        List<String> formats,
        String recommendation) {

    public static StorageUseGuideResponse of(StorageUse use) {
        return new StorageUseGuideResponse(
                use.name().toLowerCase(java.util.Locale.ROOT),
                use.label(),
                use.formats(),
                use.recommendation());
    }
}
