package dev.pepe1603.portfolio_api.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Base pública bajo la que se sirven los ficheros del bucket.
 *
 * <p>Solo queda esto del almacenamiento local: el directorio ({@code APP_STORAGE_DIR}) ya no
 * existe porque no hay disco. El nombre de la variable se conserva a propósito, porque es
 * contrato público —esa URL es la que está guardada en perfiles, proyectos y certificados— y
 * renombrarla rompería despliegues sin ganar nada. Apunta a la API, no al MinIO: ver
 * docs/STORAGE.md §3.
 */
@Getter
@Component
public class StorageProperties {

    @Value("${APP_STORAGE_PUBLIC_URL:http://localhost:8080/files}")
    private String publicUrl;
}