package dev.pepe1603.portfolio_api.controller;

import dev.pepe1603.portfolio_api.config.S3Properties;
import dev.pepe1603.portfolio_api.service.StorageService;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import io.minio.errors.ErrorResponseException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * Sirve los ficheros del bucket en {@code GET /files/{uuid}.{ext}}.
 *
 * <p>Antes esto era un {@code addResourceHandler} de Spring sobre el directorio de subidas.
 * Ahora la API hace de proxy: MinIO se queda sin exponer y las URLs que ya están guardadas en
 * la base de datos (avatar, CV, miniaturas, galería) siguen valiendo sin tocar ni una fila.
 * El porqué de no mandar al navegador directamente a MinIO está en docs/STORAGE.md §3.
 */
@RestController
public class FileServingController {

    private final MinioClient minioClient;
    private final S3Properties s3Properties;

    public FileServingController(MinioClient minioClient, S3Properties s3Properties) {
        this.minioClient = minioClient;
        this.s3Properties = s3Properties;
    }

    @GetMapping("/files/{name}")
    @Operation(summary = "Descargar un fichero del almacenamiento",
            description = "Público. Proxy en streaming del objeto del bucket; el nombre debe ser "
                    + "un UUID con extensión (jpg|png|webp|pdf). No expone el MinIO al navegador.")
    @ApiResponse(responseCode = "200", description = "Los bytes del fichero, con su content type")
    @ApiResponse(responseCode = "404", description = "Nombre fuera del patrón o objeto que no existe en el bucket")
    public ResponseEntity<StreamingResponseBody> file(@PathVariable String name,
                                                      @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        if (!StorageService.isManagedName(name)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Fichero no encontrado");
        }
        GetObjectResponse object = open(name);
        String etag = object.headers().get(HttpHeaders.ETAG);
        if (etag != null && etag.equals(ifNoneMatch)) {
            closeQuietly(object);
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).build();
        }
        long contentLength = contentLengthOf(object);
        MediaType contentType = MediaType.parseMediaType(
                StorageService.contentTypeFor(name) == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE
                        : StorageService.contentTypeFor(name));
        // El cuerpo se escribe después de que este método devuelva, así que el cierre del
        // stream va dentro de la lambda: si se cerrara aquí, MinIO no habría terminado de
        // enviar los bytes cuando Spring intente copiarlos.
        StreamingResponseBody body = out -> {
            try (GetObjectResponse stream = object) {
                stream.transferTo(out);
            }
        };
        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .contentType(contentType)
                .contentLength(contentLength);
        return etag == null ? response.body(body) : response.eTag(etag).body(body);
    }

    private GetObjectResponse open(String name) {
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(s3Properties.getBucket())
                    .object(name)
                    .build());
        } catch (ErrorResponseException e) {
            // NoSuchKey / NoSuchObject: el bucket no tiene ese objeto. Es un 404 honesto, no un 500.
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Fichero no encontrado", e);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo leer el fichero", e);
        }
    }

    private static long contentLengthOf(GetObjectResponse object) {
        String value = object.headers().get(HttpHeaders.CONTENT_LENGTH);
        if (value == null) {
            return -1;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static void closeQuietly(GetObjectResponse object) {
        try {
            object.close();
        } catch (IOException ignored) {
            // en la respuesta 304 el stream no se va a leer: solo interesa no dejar la conexión abierta
        }
    }
}