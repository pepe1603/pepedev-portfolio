package dev.pepe1603.portfolio_api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.pepe1603.portfolio_api.config.S3Properties;
import dev.pepe1603.portfolio_api.config.StorageProperties;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.errors.InsufficientDataException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * El bucket se prepara al arrancar, que es lo que sustituye al {@code Files.createDirectories}
 * de antes. Si esto falla, la aplicación ni siquiera debería levantarse: es preferible un
 * arranque parado con un mensaje claro que una API que acepta una subida y la pierde.
 */
class StorageServiceBootstrapTest {

    private static final String BUCKET = "portfolio";

    private MinioClient minioClient;
    private StorageService service;

    @BeforeEach
    void setUp() throws Exception {
        minioClient = mock(MinioClient.class);

        S3Properties s3 = new S3Properties();
        ReflectionTestUtils.setField(s3, "endpoint", "http://localhost:9000");
        ReflectionTestUtils.setField(s3, "bucket", BUCKET);
        ReflectionTestUtils.setField(s3, "region", "us-east-1");

        StorageProperties storage = new StorageProperties();
        ReflectionTestUtils.setField(storage, "publicUrl", "http://localhost:8080/files");

        service = new StorageService(minioClient, s3, storage);
    }

    @Test
    void creaElBucketConSuRegionSiNoExiste() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(false);

        service.ensureBucket();

        ArgumentCaptor<BucketExistsArgs> exists = ArgumentCaptor.forClass(BucketExistsArgs.class);
        verify(minioClient).bucketExists(exists.capture());
        assertThat(exists.getValue().bucket()).isEqualTo(BUCKET);

        ArgumentCaptor<MakeBucketArgs> make = ArgumentCaptor.forClass(MakeBucketArgs.class);
        verify(minioClient).makeBucket(make.capture());
        assertThat(make.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(make.getValue().region()).isEqualTo("us-east-1");
    }

    @Test
    void siElBucketYaExisteNoInsisteEnCrearlo() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);

        service.ensureBucket();

        verify(minioClient, never()).makeBucket(any());
    }

    @Test
    void siMinioNoRespondeFallaAlArrancarDiciendoQueMinio() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class)))
                .thenThrow(new InsufficientDataException("no hay nadie escuchando"));

        assertThatThrownBy(() -> service.ensureBucket())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(BUCKET)
                .hasMessageContaining("http://localhost:9000");
    }
}