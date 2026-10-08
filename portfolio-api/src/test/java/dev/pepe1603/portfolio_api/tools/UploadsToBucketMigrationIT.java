package dev.pepe1603.portfolio_api.tools;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Sube a MinIO los ficheros que quedaron en el directorio local {@code uploads/}.
 *
 * <p>No lo ejecuta la suite: el nombre {@code IT} queda fuera de los patrones de Surefire, así
 * que {@code ./mvnw -o clean test} sigue siendo 244 tests sin tocar el servidor. Se lanza a
 * mano, y solo cuando MinIO está levantado:
 *
 * <pre>
 *   cd portfolio-api
 *   ./mvnw -o test -Dtest=UploadsToBucketMigrationIT
 * </pre>
 *
 * <p>Los valores vienen de propiedades del sistema y sus default son los de MinIO en local, que
 * es donde se va a correr. Para otro destino: {@code -Ds3.endpoint=... -Ds3.bucket=...}.
 *
 * <p><b>No borra nada.</b> Copiar es reversible; borrar el directorio no lo es, y hasta que no
 * se haya comprobado que la web sirve las mismas imágenes no se toca {@code uploads/}. Ver
 * docs/STORAGE.md §5 para el orden de la comprobación.
 */
class UploadsToBucketMigrationIT {

    private static final String ENDPOINT = System.getProperty("s3.endpoint", "http://localhost:9000");
    private static final String ACCESS_KEY = System.getProperty("s3.accessKey", "minioadmin");
    private static final String SECRET_KEY = System.getProperty("s3.secretKey", "minioadmin");
    private static final String BUCKET = System.getProperty("s3.bucket", "portfolio");
    private static final String REGION = System.getProperty("s3.region", "us-east-1");
    private static final Path UPLOADS = Path.of(System.getProperty("uploads.dir", "../uploads"));

    private static final Map<String, String> CONTENT_TYPES = Map.of(
            "jpg", "image/jpeg",
            "png", "image/png",
            "webp", "image/webp",
            "pdf", "application/pdf");

    private final MinioClient minioClient = MinioClient.builder()
            .endpoint(ENDPOINT)
            .credentials(ACCESS_KEY, SECRET_KEY)
            .region(REGION)
            .build();

    @Test
    void subeTodoLoQueHayEnUploadsYLoReleeDelBucket() throws Exception {
        Assumptions.assumeTrue(Files.isDirectory(UPLOADS),
                "No hay directorio " + UPLOADS.toAbsolutePath() + ": nada que migrar");

        ensureBucket();

        List<Path> files = archivos();
        Assumptions.assumeFalse(files.isEmpty(), "uploads/ está vacío: nada que migrar");

        int subidos = 0;
        int yaEstaban = 0;
        for (Path file : files) {
            String name = file.getFileName().toString();
            long bytes = Files.size(file);
            if (existe(name)) {
                yaEstaban++;
                System.out.println("= ya estaba " + name + " (" + bytes + " bytes)");
            } else {
                subir(file, name, bytes);
                subidos++;
                System.out.println("+ subido   " + name + " (" + bytes + " bytes)");
            }
            comprobarQueSeLeeIgual(name, bytes);
        }

        System.out.println("Migración a " + ENDPOINT + "/" + BUCKET + ": " + subidos + " subidos, "
                + yaEstaban + " ya estaban, " + files.size() + " comprobados.");
        System.out.println("uploads/ se conserva a propósito. Bórralo solo cuando hayas comprobado"
                + " que la web sirve las mismas ficheros (docs/STORAGE.md §5).");
    }

    private void ensureBucket() throws Exception {
        if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(BUCKET).build())) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(BUCKET).region(REGION).build());
            System.out.println("Bucket " + BUCKET + " creado");
        }
    }

    private static List<Path> archivos() throws IOException {
        try (Stream<Path> entries = Files.list(UPLOADS)) {
            return entries.filter(Files::isRegularFile)
                    .filter(p -> CONTENT_TYPES.containsKey(extension(p.getFileName().toString())))
                    .sorted()
                    .toList();
        }
    }

    private void subir(Path file, String name, long bytes) throws Exception {
        try (InputStream in = Files.newInputStream(file)) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(BUCKET)
                    .object(name)
                    .headers(Map.of("Content-Type", CONTENT_TYPES.get(extension(name))))
                    .stream(in, bytes, -1L)
                    .build());
        }
    }

    /**
     * Copiar sin releer es confiar en el código de retorno. Relee el objeto del bucket y
     * comprueba que trae los mismos bytes: es lo que distingue "subido" de "subido bien".
     */
    private void comprobarQueSeLeeIgual(String name, long bytes) throws Exception {
        try (GetObjectResponse in = minioClient.getObject(GetObjectArgs.builder()
                .bucket(BUCKET)
                .object(name)
                .build())) {
            if (in.readAllBytes().length != bytes) {
                throw new IllegalStateException(name + " no coincide con el original");
            }
        }
    }

    private boolean existe(String name) throws Exception {
        try (GetObjectResponse ignored = minioClient.getObject(GetObjectArgs.builder()
                .bucket(BUCKET)
                .object(name)
                .build())) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1);
    }
}