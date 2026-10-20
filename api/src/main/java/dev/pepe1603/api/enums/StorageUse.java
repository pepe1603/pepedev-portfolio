package dev.pepe1603.api.enums;

import java.util.List;

/**
 * Para qué se sube un fichero. Cada uso lleva sus formatos admitidos y una recomendación en
 * lenguaje de persona, que es lo que consume GET /admin/storage/uses para guiar la subida
 * desde el panel.
 *
 * <p>La recomendación es orientativa: la API no puede saber por los bytes si una imagen es
 * "de galería" o "de avatar". Solo se valida lo que sí es seguro —el formato— y el panel
 * avisa si las dimensiones no encajan con lo recomendado. Ver docs/STORAGE.md §4.
 */
public enum StorageUse {

    AVATAR("Avatar", List.of("jpg", "png", "webp"),
            "Foto de perfil. Cuadrada y de al menos 400×400 px; se recorta en círculo."),
    THUMBNAIL("Miniatura de proyecto", List.of("jpg", "png", "webp"),
            "Portada de un proyecto. Horizontal, proporción 16:9, al menos 1200×675 px."),
    GALLERY("Galería de proyecto", List.of("jpg", "png", "webp"),
            "Imagen de la galería de un proyecto. Horizontal o cuadrada, al menos 1200 px de ancho."),
    IMAGE("Imagen de certificado", List.of("jpg", "png", "webp"),
            "Imagen de un certificado. Horizontal, al menos 1000 px de ancho."),
    CV("Currículum (PDF)", List.of("pdf"),
            "Currículum en PDF. Solo PDF; tamaño máximo 10 MB.");

    private final String label;
    private final List<String> formats;
    private final String recommendation;

    StorageUse(String label, List<String> formats, String recommendation) {
        this.label = label;
        this.formats = formats;
        this.recommendation = recommendation;
    }

    public String label() {
        return label;
    }

    public List<String> formats() {
        return formats;
    }

    public String recommendation() {
        return recommendation;
    }
}
