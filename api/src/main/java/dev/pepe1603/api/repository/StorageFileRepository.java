package dev.pepe1603.api.repository;

import dev.pepe1603.api.entity.StorageFile;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Acceso al uso declarado de los ficheros del bucket. Sin métodos derivados: el
 * panel usa {@code findAll()} para volcar el mapa completo al listar (el bucket
 * de un portafolio tiene pocos objetos, igual que en {@code StorageService.list()}).
 */
public interface StorageFileRepository extends JpaRepository<StorageFile, String> {
}
