package dev.pepe1603.portfolio_api.service;

import dev.pepe1603.portfolio_api.config.S3Properties;
import dev.pepe1603.portfolio_api.config.StorageProperties;
import dev.pepe1603.portfolio_api.enums.StorageUse;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * Guarda y borra ficheros en el bucket S3 de MinIO.
 *
 * <p>Antes escribía en un directorio del disco y Spring lo servía como recurso estático. El
 * contrato con el resto de la aplicación no cambia: se sube con {@code use}, se devuelve una
 * URL pública y el borrado de huérfanos sigue hablando con {@link #baseName(String)}. Solo
 * cambia dónde caen los bytes. Ver docs/STORAGE.md para el porqué de cada decisión.
 */
@Service
public class StorageService {

    private static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;
    private static final long MAX_CV_BYTES = 10L * 1024 * 1024;

    private static final byte[] MAGIC_PNG = {0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    private static final Pattern STORAGE_FILE_NAME = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png|webp|pdf)$");

    /**
     * El content type se deduce de la extensión y no de lo que declare el cliente. La
     * extensión sale de los magic bytes, así que el tipo que se anuncia y el que se comprueba
     * no pueden separarse.
     */
    private static final Map<String, String> CONTENT_TYPES = Map.of(
            "jpg", "image/jpeg",
            "png", "image/png",
            "webp", "image/webp",
            "pdf", "application/pdf");

    private final MinioClient minioClient;
    private final S3Properties s3Properties;
    private final StorageProperties properties;

    public StorageService(MinioClient minioClient, S3Properties s3Properties, StorageProperties properties) {
        this.minioClient = minioClient;
        this.s3Properties = s3Properties;
        this.properties = properties;
    }

    /**
     * Equivalente al {@code Files.createDirectories} de antes: el bucket tiene que existir
     * antes de la primera subida. Crearlo aquí quita un paso de configuración al que se
     * puede olvidar uno mismo, y falla ruidosamente si MinIO no está levantado en vez de
     * dejar un 500 en cada subida.
     */
    @PostConstruct
    void ensureBucket() {
        String bucket = s3Properties.getBucket();
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder()
                        .bucket(bucket)
                        .region(s3Properties.getRegion())
                        .build());
            }
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo preparar el bucket " + bucket
                    + " en " + s3Properties.getEndpoint() + ". ¿Está MinIO levantado?", e);
        }
    }

    public String store(StorageUse use, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Se requiere un fichero no vacío");
        }
        String extension = detectExtension(readHeader(file));
        if (extension == null || !allows(use, extension)) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Tipo de fichero no permitido para " + use);
        }
        long maxBytes = use == StorageUse.CV ? MAX_CV_BYTES : MAX_IMAGE_BYTES;
        if (file.getSize() > maxBytes) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "El fichero supera el tamaño máximo de " + maxBytes / 1024 / 1024 + " MB");
        }
        String name = UUID.randomUUID() + "." + extension;
        try (InputStream in = file.getInputStream()) {
            // -1 como tamaño de parte deja que el SDK elija el troceado: los ficheros van de
            // unos pocos KB a 10 MB y no merece la pena decidir aquí el umbral.
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(s3Properties.getBucket())
                    .object(name)
                    .headers(Map.of("Content-Type", CONTENT_TYPES.get(extension)))
                    .stream(in, file.getSize(), -1L)
                    .build());
        } catch (Exception e) {
            // El SDK lanza MinioException (checked) y sus propias RuntimeException de streaming;
            // un fallo del bucket no distingue entre "no se pudo leer" y "no se pudo subir"
            // para quien está subiendo, así que todo acaba en el mismo 500.
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo guardar el fichero", e);
        }
        return publicUrl(properties.getPublicUrl()) + "/" + name;
    }

    private static boolean allows(StorageUse use, String extension) {
        if (use == StorageUse.CV) {
            return "pdf".equals(extension);
        }
        return "jpg".equals(extension) || "png".equals(extension) || "webp".equals(extension);
    }

    private static byte[] readHeader(MultipartFile file) {
        byte[] header = new byte[12];
        try (InputStream in = file.getInputStream()) {
            in.readNBytes(header, 0, header.length);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo leer el fichero subido", e);
        }
        return header;
    }

    private static String detectExtension(byte[] h) {
        if (h.length >= 3 && (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        if (h.length >= 8 && (h[0] & 0xFF) == 0x89 && startsWith(h, 1, MAGIC_PNG)) {
            return "png";
        }
        if (h.length >= 12 && startsWith(h, 0, "RIFF".getBytes()) && startsWith(h, 8, "WEBP".getBytes())) {
            return "webp";
        }
        if (h.length >= 4 && startsWith(h, 0, "%PDF".getBytes())) {
            return "pdf";
        }
        return null;
    }

    private static boolean startsWith(byte[] haystack, int offset, byte[] needle) {
        if (haystack.length < offset + needle.length) {
            return false;
        }
        for (int i = 0; i < needle.length; i++) {
            if (haystack[offset + i] != needle[i]) {
                return false;
            }
        }
        return true;
    }

    private static String publicUrl(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    public static String baseName(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String candidate = url.contains("/") ? url.substring(url.lastIndexOf('/') + 1) : url;
        return STORAGE_FILE_NAME.matcher(candidate).matches() ? candidate : null;
    }

    /**
     * Si el nombre es del patrón {@code UUID.ext}, entonces es una clave de objeto ours y solo
     * se puede tocar ese objeto. Antes, con el disco, este mismo patrón era lo que impedía que
     * un {@code ../} se colara; en S3 la defensa es estructural: una clave con barras o puntos
     * suspendidos no existe en el bucket, y una que se parezca a otro nombre no se acepta ni
     * para leer ni para borrar.
     */
    public static boolean isManagedName(String name) {
        return name != null && STORAGE_FILE_NAME.matcher(name).matches();
    }

    public static String contentTypeFor(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? null : CONTENT_TYPES.get(name.substring(dot + 1));
    }

    public void deleteByUrl(String url) {
        deleteByName(baseName(url));
    }

    public void deleteByName(String fileName) {
        if (!isManagedName(fileName)) {
            return;
        }
        try {
            // Borrar un objeto que no existe no falla en S3, igual que antes deleteIfExists
            // no fallaba sobre un fichero ausente.
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(s3Properties.getBucket())
                    .object(fileName)
                    .build());
        } catch (Exception ignored) {
            // borrado best-effort: no debe romper la transacción dominante
        }
    }
}