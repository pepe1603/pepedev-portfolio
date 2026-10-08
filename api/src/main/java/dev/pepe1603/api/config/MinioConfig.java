package dev.pepe1603.api.config;

import io.minio.MinioClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cliente S3 único para toda la aplicación.
 *
 * <p>El SDK mantiene un pool de conexiones por cliente, así que se crea uno solo y se comparte:
 * uno por servicio abriría un pool HTTP cada uno. Además, todas las llamadas salen por aquí,
 * que es el único punto donde hay que cambiar algo si el almacenamiento deja de ser MinIO.
 */
@Configuration
public class MinioConfig {

    @Bean
    MinioClient minioClient(S3Properties properties) {
        return MinioClient.builder()
                .endpoint(properties.getEndpoint())
                .credentials(properties.getAccessKey(), properties.getSecretKey())
                .region(properties.getRegion())
                .build();
    }
}