-- Uso declarado de cada fichero del bucket. Hasta ahora el "uso" solo existía
-- en el momento de la subida (el enum StorageUse) y se perdía: al listar el
-- bucket no había forma de saber si un <uuid>.jpg era un avatar, una miniatura,
-- una imagen de galería o la imagen de un certificado. Esta tabla lo recuerda.
--
-- Es SOLO una etiqueta: NO participa en el borrado. Si un fichero deja de estar
-- referenciado por el perfil/proyecto/certificado, se sigue pudiendo borrar
-- (StorageReferenceChecker decide, como antes). Así no hay dos verdades que se
-- contradigan: la etiqueta no bloquea ni desbloquea un borrado.
--
-- La clave es el nombre del objeto tal cual (<uuid>.<ext>), sin FK a nada: un
-- fichero puede subirse antes de asignarse a un proyecto. Los ficheros que ya
-- estaban en el bucket no tienen fila; su uso se infiere de quién los
-- referencia (StorageReferenceChecker.inferUse).
CREATE TABLE storage_files (
    file_name  VARCHAR(64) PRIMARY KEY,
    use        VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_storage_files_use CHECK (use IN ('AVATAR', 'THUMBNAIL', 'GALLERY', 'IMAGE', 'CV'))
);
