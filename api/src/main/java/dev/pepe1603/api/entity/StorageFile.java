package dev.pepe1603.api.entity;

import dev.pepe1603.api.enums.StorageUse;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Uso declarado de un fichero del bucket: qué se eligió al subirlo.
 *
 * <p>Es solo una etiqueta, no una referencia. Que un fichero tenga fila aquí no
 * impide borrarlo: el borrado lo sigue decidiendo {@link
 * dev.pepe1603.api.service.StorageReferenceChecker} a partir de quién lo
 * referencia de verdad (perfil, proyectos, certificados). La etiqueta sirve para
 * que el panel filtre (el modal de avatar solo ofrece avatares) y para mostrar el
 * uso en Medios.
 *
 * <p>La clave primaria es el propio nombre del objeto ({@code <uuid>.<ext>}), no
 * un id propio: la identidad del fichero ya es su clave en el bucket.
 */
@Entity
@Table(name = "storage_files")
@Getter
@Setter
@NoArgsConstructor
public class StorageFile {

    @Id
    @Column(name = "file_name", length = 64)
    private String fileName;

    @Enumerated(EnumType.STRING)
    @Column(name = "use", nullable = false, length = 20)
    private StorageUse use;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public StorageFile(String fileName, StorageUse use) {
        this.fileName = fileName;
        this.use = use;
    }
}
