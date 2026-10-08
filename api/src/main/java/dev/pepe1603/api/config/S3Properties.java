package dev.pepe1603.api.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Datos de conexión al bucket S3 que sustituye al directorio local de subidas.
 *
 * <p>Hoy el endpoint apunta a un MinIO en {@code localhost}; dentro de poco será el mismo
 * MinIO en Docker en la red del monorepo, y por eso <b>solo</b> hay un endpoint: cambiar de
 * máquina es cambiar {@code APP_S3_ENDPOINT}, no tocar código ni rehacer migraciones.
 *
 * <p>Las credenciales no tienen valor por defecto a propósito: si faltan, la aplicación debe
 * morir al arrancar con un error claro en vez de subir ficheros a un bucket con la clave de otro.
 * El resto sí lleva default porque no son secretos y los del MinIO de local son conocidos.
 */
@Getter
@Component
public class S3Properties {

    @Value("${APP_S3_ENDPOINT:http://localhost:9000}")
    private String endpoint;

    @Value("${APP_S3_ACCESS_KEY}")
    private String accessKey;

    @Value("${APP_S3_SECRET_KEY}")
    private String secretKey;

    @Value("${APP_S3_BUCKET:portfolio}")
    private String bucket;

    /**
     * S3 no tiene regiones de verdad, pero el protocolo las exige en la firma. MinIO acepta
     * cualquiera; se pone una explícita para no depender del default del SDK.
     */
    @Value("${APP_S3_REGION:us-east-1}")
    private String region;
}