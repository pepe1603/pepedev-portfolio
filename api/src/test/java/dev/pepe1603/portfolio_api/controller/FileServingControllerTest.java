package dev.pepe1603.portfolio_api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.pepe1603.portfolio_api.config.S3Properties;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import io.minio.errors.ErrorResponseException;
import io.minio.errors.InsufficientDataException;
import io.minio.messages.ErrorResponse;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import okhttp3.Headers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * El proxy de ficheros es la puerta que el navegador tiene abierta, así que lo que se
 * comprueba aquí es qué se deja pasar y qué se responde cuando el bucket no tiene el objeto.
 */
class FileServingControllerTest {

    private static final String NAME = "123e4567-e89b-12d3-a456-426614174000.png";
    private static final String BUCKET = "portfolio";
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    private MinioClient minioClient;
    private FileServingController controller;

    @BeforeEach
    void setUp() {
        minioClient = mock(MinioClient.class);
        S3Properties s3 = new S3Properties();
        ReflectionTestUtils.setField(s3, "bucket", BUCKET);
        controller = new FileServingController(minioClient, s3);
    }

    private static GetObjectResponse objeto(String name, String etag, byte[] content) {
        Headers.Builder headers = new Headers.Builder()
                .add("Content-Length", String.valueOf(content.length));
        if (etag != null) {
            headers.add("ETag", etag);
        }
        return new GetObjectResponse(headers.build(), BUCKET, "us-east-1", name,
                new ByteArrayInputStream(content));
    }

    private static byte[] bodyOf(ResponseEntity<StreamingResponseBody> response) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        response.getBody().writeTo(out);
        return out.toByteArray();
    }

    private static ErrorResponseException noSuchKey(String name) {
        ErrorResponse error = new ErrorResponse("NoSuchKey", "The specified key does not exist.",
                BUCKET, name, null, null, "/" + BUCKET + "/" + name);
        return new ErrorResponseException(error, null, "GET " + BUCKET + "/" + name);
    }

    @Test
    void sirveLosBytesDelObjetoConSuContentType() throws Exception {
        when(minioClient.getObject(any(GetObjectArgs.class))).thenReturn(objeto(NAME, "\"abc\"", PNG));

        ResponseEntity<StreamingResponseBody> response = controller.file(NAME, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType().toString()).isEqualTo("image/png");
        assertThat(response.getHeaders().getETag()).isEqualTo("\"abc\"");
        assertThat(response.getHeaders().getContentLength()).isEqualTo(PNG.length);
        assertThat(bodyOf(response)).containsExactly(PNG);
    }

    @Test
    void conIfNoneMatchIgualDevuelve304SinVolverABuscarElObjeto() throws Exception {
        when(minioClient.getObject(any(GetObjectArgs.class))).thenReturn(objeto(NAME, "\"abc\"", PNG));

        ResponseEntity<StreamingResponseBody> response = controller.file(NAME, "\"abc\"");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_MODIFIED);
        assertThat(response.getBody()).isNull();
    }

    @Test
    void unNombreQueNoEsDelStorageNiSiquieraSeBuscaEnElBucket() throws Exception {
        assertThatThrownBy(() -> controller.file("../application.yaml", null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(t -> assertThat(((ResponseStatusException) t).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> controller.file("carpeta/foto.png", null))
                .isInstanceOf(ResponseStatusException.class);

        verify(minioClient, never()).getObject(any());
    }

    @Test
    void objetoQueNoEstaEnElBucketEs404YNo500() throws Exception {
        when(minioClient.getObject(any(GetObjectArgs.class))).thenThrow(noSuchKey(NAME));

        assertThatThrownBy(() -> controller.file(NAME, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(t -> assertThat(((ResponseStatusException) t).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void siElBucketNoRespondeEs500YNoUn404Enganoso() throws Exception {
        when(minioClient.getObject(any(GetObjectArgs.class))).thenThrow(new InsufficientDataException("se cayó"));

        assertThatThrownBy(() -> controller.file(NAME, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(t -> assertThat(((ResponseStatusException) t).getStatusCode())
                        .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR));
    }

    @Test
    void elContentTypeSeSacaDeLaExtensionNoDeLoQueDigaElCliente() throws Exception {
        String pdf = "123e4567-e89b-12d3-a456-426614174000.pdf";
        when(minioClient.getObject(any(GetObjectArgs.class)))
                .thenReturn(objeto(pdf, null, "%PDF-1.4".getBytes(StandardCharsets.ISO_8859_1)));

        ResponseEntity<StreamingResponseBody> response = controller.file(pdf, null);

        assertThat(response.getHeaders().getContentType().toString()).isEqualTo("application/pdf");
        assertThat(bodyOf(response)).isNotEmpty();
    }
}