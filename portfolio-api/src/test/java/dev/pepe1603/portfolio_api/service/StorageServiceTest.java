package dev.pepe1603.portfolio_api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doAnswer;

import dev.pepe1603.portfolio_api.config.S3Properties;
import dev.pepe1603.portfolio_api.config.StorageProperties;
import dev.pepe1603.portfolio_api.enums.StorageUse;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

class StorageServiceTest {

    private static final byte[] PNG = new byte[] {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D};
    private static final byte[] JPG = new byte[] {
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
    private static final byte[] WEBP = new byte[] {
            0x52, 0x49, 0x46, 0x46, 0x00, 0x00, 0x00, 0x00, 0x57, 0x45, 0x42, 0x50};
    private static final byte[] PDF = "%PDF-1.4".getBytes(StandardCharsets.ISO_8859_1);
    private static final Pattern UUID_EXT =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.\\w+");

    private static final String BUCKET = "portfolio";

    private MinioClient minioClient;
    private StorageService service;

    /** Bytes que el SDK recibió de verdad, para no conformarse solo con la URL devuelta. */
    private final AtomicReference<byte[]> uploaded = new AtomicReference<>();
    private final List<PutObjectArgs> putArgs = new ArrayList<>();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        minioClient = mock(MinioClient.class);
        putArgs.clear();
        doAnswer(invocacion -> {
            PutObjectArgs args = invocacion.getArgument(0);
            putArgs.add(args);
            try (InputStream in = args.stream()) {
                uploaded.set(in.readAllBytes());
            }
            return null;
        }).when(minioClient).putObject(any(PutObjectArgs.class));

        StorageProperties storage = new StorageProperties();
        ReflectionTestUtils.setField(storage, "publicUrl", "http://localhost:8080/files/");

        S3Properties s3 = new S3Properties();
        ReflectionTestUtils.setField(s3, "endpoint", "http://localhost:9000");
        ReflectionTestUtils.setField(s3, "accessKey", "minioadmin");
        ReflectionTestUtils.setField(s3, "secretKey", "minioadmin");
        ReflectionTestUtils.setField(s3, "bucket", BUCKET);
        ReflectionTestUtils.setField(s3, "region", "us-east-1");

        service = new StorageService(minioClient, s3, storage);
    }

    private MultipartFile file(String name, String contentType, byte[] content) {
        return new MockMultipartFile("file", name, contentType, content);
    }

    private static ResponseStatusException status400of(Throwable t) {
        return (ResponseStatusException) t;
    }

    @Test
    void pngAvatarSeGuardaConUrlPublicaYNombreUUID() throws Exception {
        String url = service.store(StorageUse.AVATAR, file("foto.png", "image/png", PNG));

        assertThat(url).startsWith("http://localhost:8080/files/");
        assertThat(url).doesNotContain("//files//");
        String name = url.substring(url.lastIndexOf('/') + 1);
        assertThat(name).matches(UUID_EXT.pattern());

        assertThat(putArgs).hasSize(1);
        PutObjectArgs args = putArgs.get(0);
        assertThat(args.bucket()).isEqualTo(BUCKET);
        assertThat(args.object()).isEqualTo(name).doesNotContain("/");
        assertThat(args.contentType()).isEqualTo("image/png");
        assertThat(uploaded.get()).containsExactly(PNG);
    }

    @Test
    void jpgYWebpTambienSonAceptadosComoAvatar() {
        assertThat(service.store(StorageUse.AVATAR, file("a.jpg", "image/jpeg", JPG)))
                .endsWith(".jpg");
        assertThat(service.store(StorageUse.AVATAR, file("a.webp", "image/webp", WEBP)))
                .endsWith(".webp");
    }

    @Test
    void pdfSoloSeAceptaParaCv() {
        assertThat(service.store(StorageUse.CV, file("cv.pdf", "application/pdf", PDF)))
                .endsWith(".pdf");

        assertThatThrownBy(() -> service.store(StorageUse.AVATAR, file("cv.pdf", "application/pdf", PDF)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(t -> assertThat(status400of(t).getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE));
    }

    @Test
    void txtRechazadoCon415() {
        assertThatThrownBy(() -> service.store(StorageUse.IMAGE, file("doc.txt", "text/plain", "hola".getBytes())))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(t -> {
                    ResponseStatusException ex = status400of(t);
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
                    assertThat(ex.getReason()).isEqualTo("Tipo de fichero no permitido para IMAGE");
                });
    }

    @Test
    void elTipoSeDetectaPorMagicBytesNoPorElNombre() {
        assertThat(service.store(StorageUse.AVATAR, file("m.txt", "text/plain", PNG)))
                .endsWith(".png");
    }

    @Test
    void ficheroVacioYNullRechazadosCon400() {
        MockMultipartFile vacio = new MockMultipartFile("file", "e.png", "image/png", new byte[0]);

        assertThat(vacio.isEmpty()).isTrue();
        assertThatThrownBy(() -> service.store(StorageUse.AVATAR, vacio))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(t -> {
                    ResponseStatusException ex = status400of(t);
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getReason()).isEqualTo("Se requiere un fichero no vacío");
                });
        assertThatThrownBy(() -> service.store(StorageUse.AVATAR, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(t -> assertThat(status400of(t).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void imagenPorEncimaDe5MbRechazadaCon413() {
        byte[] bigPng = new byte[5 * 1024 * 1024 + 1];
        System.arraycopy(PNG, 0, bigPng, 0, PNG.length);

        assertThatThrownBy(() -> service.store(StorageUse.GALLERY, file("big.png", "image/png", bigPng)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(t -> {
                    ResponseStatusException ex = status400of(t);
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
                    assertThat(ex.getReason()).isEqualTo("El fichero supera el tamaño máximo de 5 MB");
                });
    }

    @Test
    void cvPorEncimaDe10MbRechazadaCon413() {
        byte[] bigPdf = new byte[10 * 1024 * 1024 + 1];
        System.arraycopy(PDF, 0, bigPdf, 0, PDF.length);

        assertThatThrownBy(() -> service.store(StorageUse.CV, file("big.pdf", "application/pdf", bigPdf)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(t -> {
                    ResponseStatusException ex = status400of(t);
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
                    assertThat(ex.getReason()).isEqualTo("El fichero supera el tamaño máximo de 10 MB");
                });
    }

    @Test
    void deleteByUrlBorraElObjetoDelBucket() throws Exception {
        String url = service.store(StorageUse.AVATAR, file("foto.png", "image/png", PNG));
        String name = url.substring(url.lastIndexOf('/') + 1);

        service.deleteByUrl(url);

        ArgumentCaptor<RemoveObjectArgs> captor = ArgumentCaptor.forClass(RemoveObjectArgs.class);
        verify(minioClient).removeObject(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(captor.getValue().object()).isEqualTo(name);
    }

    @Test
    void deleteByUrlIgnoraUrlsAjenasYNoRompe() throws Exception {
        service.deleteByUrl("http://localhost:8080/files/avatar.png");
        service.deleteByUrl("https://cdn.example.com/logo.svg");
        service.deleteByUrl(null);

        verify(minioClient, never()).removeObject(any());
    }

    @Test
    void deleteByUrlNoMandaAlBucketUnaClaveQueNoEsDelStorage() throws Exception {
        // Antes esto era defensa contra traversales de directorio. En S3 la clave se valida
        // con el mismo patrón, así que un "../" ni siquiera llega a formulizarse.
        service.deleteByUrl("http://localhost:8080/files/../fuera.txt");
        service.deleteByUrl("http://localhost:8080/files/carpeta/foto.png");

        verify(minioClient, never()).removeObject(any());
    }

    @Test
    void baseNameExtraeElNombreSoloSiCumpleElPatron() {
        String name = "123e4567-e89b-12d3-a456-426614174000.png";
        assertThat(StorageService.baseName("http://host/files/" + name)).isEqualTo(name);
        assertThat(StorageService.baseName(name)).isEqualTo(name);
        assertThat(StorageService.baseName("http://host/files/avatar.png")).isNull();
        assertThat(StorageService.baseName("http://host/files/123e4567-e89b-12d3-a456-426614174000.xyz")).isNull();
        assertThat(StorageService.baseName(null)).isNull();
        assertThat(StorageService.baseName("  ")).isNull();
    }

    @Test
    void deleteByUrlDeUnFicheroInexistenteNoLanza() {
        service.deleteByUrl("http://localhost:8080/files/123e4567-e89b-12d3-a456-426614174000.png");
    }

    @Test
    void isManagedNameEsLaFronteraDelBucket() {
        assertThat(StorageService.isManagedName("123e4567-e89b-12d3-a456-426614174000.png")).isTrue();
        assertThat(StorageService.isManagedName("123e4567-e89b-12d3-a456-426614174000.pdf")).isTrue();

        // Nada de esto es una clave de objeto: ni existe en el bucket y no se acepta ni
        // para leer ni para borrar.
        assertThat(StorageService.isManagedName("../application.yaml")).isFalse();
        assertThat(StorageService.isManagedName("carpeta/foto.png")).isFalse();
        assertThat(StorageService.isManagedName("carpeta")).isFalse();
        assertThat(StorageService.isManagedName("123E4567-E89B-12D3-A456-426614174000.png")).isFalse();
        assertThat(StorageService.isManagedName("123e4567-e89b-12d3-a456-426614174000.exe")).isFalse();
        assertThat(StorageService.isManagedName("")).isFalse();
        assertThat(StorageService.isManagedName(null)).isFalse();
    }

    @Test
    void elContentTypeSaltaDeLaExtensionYNoDelNombreOriginal() {
        assertThat(StorageService.contentTypeFor("a.png")).isEqualTo("image/png");
        assertThat(StorageService.contentTypeFor("a.jpg")).isEqualTo("image/jpeg");
        assertThat(StorageService.contentTypeFor("a.webp")).isEqualTo("image/webp");
        assertThat(StorageService.contentTypeFor("a.pdf")).isEqualTo("application/pdf");
        assertThat(StorageService.contentTypeFor("a.exe")).isNull();
        assertThat(StorageService.contentTypeFor("sin-extension")).isNull();
    }
}