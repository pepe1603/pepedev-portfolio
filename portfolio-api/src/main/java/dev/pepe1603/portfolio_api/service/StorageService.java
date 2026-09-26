package dev.pepe1603.portfolio_api.service;

import dev.pepe1603.portfolio_api.config.StorageProperties;
import dev.pepe1603.portfolio_api.enums.StorageUse;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class StorageService {

    private static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;
    private static final long MAX_CV_BYTES = 10L * 1024 * 1024;

    private static final byte[] MAGIC_PNG = {0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    private static final Pattern STORAGE_FILE_NAME = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png|webp|pdf)$");

    private final StorageProperties properties;

    public StorageService(StorageProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void ensureDirectory() {
        try {
            Files.createDirectories(Path.of(properties.getDir()));
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo crear el directorio de almacenamiento " + properties.getDir(), e);
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
        Path destination = Path.of(properties.getDir(), name);
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, destination, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
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

    public void deleteByUrl(String url) {
        deleteByName(baseName(url));
    }

    public void deleteByName(String fileName) {
        if (fileName == null || !STORAGE_FILE_NAME.matcher(fileName).matches()) {
            return;
        }
        Path dir = Path.of(properties.getDir()).toAbsolutePath().normalize();
        Path target = dir.resolve(fileName).normalize();
        if (!target.startsWith(dir)) {
            return;
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException ignored) {
            // borrado best-effort: no debe romper la transacción dominante
        }
    }
}