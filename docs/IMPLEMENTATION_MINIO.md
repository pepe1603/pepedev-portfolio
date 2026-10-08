# IMPLEMENTATION_MINIO.md

> ## ⚠️ Documento superado — no lo ejecutes
>
> Este era el plan original: montar MinIO **en el VPS de Terramount**, con su usuario
> `api` y un timeline de cuatro semanas. **Ya no es el plan** y casi nada de lo que
> hay aquí describe lo que se hizo.
>
> Lo que pasó de verdad está en **[STORAGE.md](STORAGE.md)**: MinIO corre **en local** primero
> (y después irá en Docker dentro del monorepo), y las diferencias de fondo son:
>
> - El bucket lo crea la propia API al arrancar; no hay usuario dedicado ni políticas que
>   configurar a mano.
> - El SDK elegido es `io.minio:minio` 8.5.17, no el que se propone aquí.
> - Los ficheros se sirven por **proxy** en `GET /files/**`, no con un bucket de lectura pública.
> - No hay schedule de cuatro semanas: fueron cuatro commits.
>
> Se conserva sin editar a propósito, para que quede constancia del punto de partida: qué
> habría que instalar y configurar si se hubiera seguido el camino del VPS.

**Objetivo**: Integrar MinIO en api de forma limpia, testeada y documentada.

**Timeline**: 4 semanas (semanas 1-4 del roadmap)  
**Branch**: `feat/minio-storage-integration`  
**Versión**: v0.1 (MVP)

---

## SEMANA 1: Setup MinIO en Terramount

### Paso 1.1: Instalar Docker en Terramount
```bash
# En Terramount (servidor)
ssh usuario@terramount-ip

# Actualizar paquetes
sudo apt update && sudo apt upgrade -y

# Instalar Docker
sudo apt install -y docker.io docker-compose

# Iniciar Docker
sudo systemctl start docker
sudo systemctl enable docker

# Verificar
docker --version
```

**Checklist**:
- [ ] Docker instalado en Terramount
- [ ] Docker daemon corriendo
- [ ] Usuario con permiso docker (sin sudo)

**Commit**: No aplica (infraestructura)

---

### Paso 1.2: Levantar MinIO en contenedor

```bash
# En Terramount, crear directorio para datos
mkdir -p ~/minio-data

# Correr MinIO
docker run -d \
  --name minio-server \
  -p 9000:9000 \
  -p 9001:9001 \
  -e MINIO_ROOT_USER=minioadmin \
  -e MINIO_ROOT_PASSWORD=cambia_esto \
  -v ~/minio-data:/data \
  minio/minio:latest server /data

# Verificar que está corriendo
docker ps | grep minio
```

**Checklist**:
- [ ] Contenedor MinIO corriendo
- [ ] Puerto 9000 (API) abierto
- [ ] Puerto 9001 (console) abierto
- [ ] Datos persisten en ~/minio-data

**Commit**: No aplica (infraestructura)

---

### Paso 1.3: Crear bucket "portfolio" y credenciales

```bash
# Acceder a MinIO console
# En navegador: http://terramount-ip:9001
# Login: minioadmin / cambia_esto

# O por CLI (instalar minio-mc):
wget https://dl.min.io/client/mc/release/linux-amd64/mc
chmod +x mc

# Configurar conexión
./mc alias set minio http://localhost:9000 minioadmin cambia_esto

# Crear bucket
./mc mb minio/portfolio

# Crear access key (usuario específico para API)
# En console web: Admin → Users → Add User
# Username: api
# Password: generar fuerte
# Perms: Read/Write en bucket "portfolio"
```

**Resultado esperado**:
```
MINIO_ENDPOINT=http://terramount-ip:9000
MINIO_ACCESS_KEY=api
MINIO_SECRET_KEY=<generated-secret>
MINIO_BUCKET_NAME=portfolio
```

**Checklist**:
- [ ] Bucket "portfolio" creado
- [ ] Usuario "api" creado
- [ ] Access key + secret generadas
- [ ] Permisos Read/Write validados
- [ ] Credenciales guardadas en papel/password manager

**Commit**: No aplica (infraestructura)

---

### Paso 1.4: Verificar MinIO desde laptop

```bash
# En laptop, conectar a MinIO Terramount
export MINIO_ENDPOINT=http://terramount-ip:9000
export MINIO_ACCESS_KEY=api
export MINIO_SECRET_KEY=<secret>

# Test upload
echo "test" > test.txt
./mc cp test.txt minio/portfolio/test.txt

# Test download
./mc cat minio/portfolio/test.txt

# Resultado: debe imprimir "test"
```

**Checklist**:
- [ ] Conexión a MinIO desde laptop OK
- [ ] Upload funciona
- [ ] Download funciona
- [ ] Archivo visible en console web (http://terramount-ip:9001)

**Commit**: No aplica (test manual)

---

## SEMANA 2: Refactorización código Spring

### Paso 2.1: Agregar dependencias a pom.xml

En `apps/api/pom.xml`, bajo `<dependencies>`:

```xml
<!-- MinIO S3 Client -->
<dependency>
  <groupId>io.minio</groupId>
  <artifactId>minio</artifactId>
  <version>8.5.10</version>
</dependency>

<!-- Rate limiting -->
<dependency>
  <groupId>com.gkatzioura</groupId>
  <artifactId>spring-boot-starter-bucket4j</artifactId>
  <version>0.9.0</version>
</dependency>

<!-- Testcontainers MinIO (test scope) -->
<dependency>
  <groupId>org.testcontainers</groupId>
  <artifactId>testcontainers</artifactId>
  <version>1.20.0</version>
  <scope>test</scope>
</dependency>
<dependency>
  <groupId>org.testcontainers</groupId>
  <artifactId>junit-jupiter</artifactId>
  <version>1.20.0</version>
  <scope>test</scope>
</dependency>
```

```bash
cd apps/api
./mvnw clean verify  # Descargar dependencias
```

**Checklist**:
- [ ] Dependencias agregadas
- [ ] `mvnw clean verify` sin errores
- [ ] IDE reconoce imports (MinioClient, etc.)

**Commit**:
```bash
git add pom.xml
git commit -m "chore: add minio and testcontainers dependencies"
git push origin feat/minio-storage-integration
```

---

### Paso 2.2: Crear StorageService (interfaz)

**Ruta**: `apps/api/src/main/java/dev/pepe1603/portfolioapi/module/media/service/storage/StorageService.java`

```java
package dev.pepe1603.portfolioapi.module.media.service.storage;

import java.io.InputStream;
import java.util.Set;

public interface StorageService {
    /**
     * Sube archivo a almacenamiento.
     * @return URL pública del archivo
     * @throws StorageException si falla
     */
    String uploadFile(InputStream file, String filename, long maxSizeBytes, Set<String> allowedMimeTypes) 
        throws StorageException;

    /**
     * Descarga archivo del almacenamiento.
     */
    InputStream downloadFile(String storageKey) throws StorageException;

    /**
     * Elimina archivo.
     */
    void deleteFile(String storageKey) throws StorageException;

    /**
     * Retorna URL pública.
     */
    String getPublicUrl(String storageKey);

    /**
     * Health check del almacenamiento.
     */
    boolean isHealthy();
}
```

**Checklist**:
- [ ] Archivo creado
- [ ] Imports correctos
- [ ] Compila sin errores

**Commit**:
```bash
git add apps/api/src/main/java/.../storage/StorageService.java
git commit -m "feat: add StorageService interface"
git push origin feat/minio-storage-integration
```

---

### Paso 2.3: Crear StorageException

**Ruta**: `apps/api/src/main/java/dev/pepe1603/portfolioapi/module/media/service/storage/exception/StorageException.java`

```java
package dev.pepe1603.portfolioapi.module.media.service.storage.exception;

public class StorageException extends RuntimeException {
    public StorageException(String message) {
        super(message);
    }

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }

    public static class InvalidFileException extends StorageException {
        public InvalidFileException(String message) { super(message); }
    }

    public static class FileTooLargeException extends StorageException {
        public FileTooLargeException(String message) { super(message); }
    }

    public static class InvalidMimeTypeException extends StorageException {
        public InvalidMimeTypeException(String message) { super(message); }
    }

    public static class MinioUnavailableException extends StorageException {
        public MinioUnavailableException(String message, Throwable cause) { super(message, cause); }
    }
}
```

**Checklist**:
- [ ] Archivo creado
- [ ] Compila sin errores

**Commit**:
```bash
git add apps/api/src/main/java/.../exception/StorageException.java
git commit -m "feat: add StorageException hierarchy"
git push origin feat/minio-storage-integration
```

---

### Paso 2.4: Crear MinioProperties

**Ruta**: `apps/api/src/main/java/dev/pepe1603/portfolioapi/config/MinioProperties.java`

```java
package dev.pepe1603.portfolioapi.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "storage.minio")
public class MinioProperties {
    private String endpoint;
    private String accessKey;
    private String secretKey;
    private String bucketName = "portfolio";
    private String region = "us-east-1";
}
```

**Checklist**:
- [ ] Archivo creado
- [ ] Annotations correctas

**Commit**:
```bash
git add apps/api/src/main/java/.../config/MinioProperties.java
git commit -m "feat: add MinioProperties configuration class"
git push origin feat/minio-storage-integration
```

---

### Paso 2.5: Crear MinioConfig

**Ruta**: `apps/api/src/main/java/dev/pepe1603/portfolioapi/config/MinioConfig.java`

```java
package dev.pepe1603.portfolioapi.config;

import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Slf4j
@Configuration
@EnableConfigurationProperties(MinioProperties.class)
@ConditionalOnProperty(name = "storage.type", havingValue = "minio")
@RequiredArgsConstructor
public class MinioConfig {
    private final MinioProperties minioProperties;

    @Bean
    public MinioClient minioClient() {
        try {
            MinioClient client = MinioClient.builder()
                .endpoint(minioProperties.getEndpoint())
                .credentials(minioProperties.getAccessKey(), minioProperties.getSecretKey())
                .region(minioProperties.getRegion())
                .build();
            
            log.info("MinIO client initialized successfully");
            return client;
        } catch (Exception e) {
            log.error("Failed to initialize MinIO client", e);
            throw new RuntimeException("MinIO initialization failed", e);
        }
    }
}
```

**Checklist**:
- [ ] Archivo creado
- [ ] Annotations correctas
- [ ] Compila sin errores

**Commit**:
```bash
git add apps/api/src/main/java/.../config/MinioConfig.java
git commit -m "feat: add MinioConfig bean initialization"
git push origin feat/minio-storage-integration
```

---

### Paso 2.6: Crear MinioStorageService

**Ruta**: `apps/api/src/main/java/.../media/service/storage/impl/MinioStorageService.java`

```java
package dev.pepe1603.portfolioapi.module.media.service.storage.impl;

import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.GetObjectArgs;
import io.minio.RemoveObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@ConditionalOnProperty(name = "storage.type", havingValue = "minio")
@RequiredArgsConstructor
public class MinioStorageService implements StorageService {
    private final MinioClient minioClient;
    private final String bucketName = "portfolio";

    @Override
    public String uploadFile(InputStream file, String filename, long maxSizeBytes, Set<String> allowedMimeTypes) 
            throws StorageException {
        try {
            // Generar nombre aleatorio
            String storageKey = UUID.randomUUID() + "-" + filename;
            
            // Subir a MinIO
            minioClient.putObject(
                PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(storageKey)
                    .stream(file, -1, 10485760) // 10MB chunks
                    .build()
            );
            
            log.info("File uploaded to MinIO: {}", storageKey);
            return getPublicUrl(storageKey);
        } catch (Exception e) {
            log.error("Failed to upload file to MinIO", e);
            throw new StorageException("Upload failed", e);
        }
    }

    @Override
    public InputStream downloadFile(String storageKey) throws StorageException {
        try {
            return minioClient.getObject(
                GetObjectArgs.builder()
                    .bucket(bucketName)
                    .object(storageKey)
                    .build()
            );
        } catch (Exception e) {
            throw new StorageException("Download failed", e);
        }
    }

    @Override
    public void deleteFile(String storageKey) throws StorageException {
        try {
            minioClient.removeObject(
                RemoveObjectArgs.builder()
                    .bucket(bucketName)
                    .object(storageKey)
                    .build()
            );
            log.info("File deleted from MinIO: {}", storageKey);
        } catch (Exception e) {
            throw new StorageException("Delete failed", e);
        }
    }

    @Override
    public String getPublicUrl(String storageKey) {
        return String.format("%s/%s/%s", "http://minio:9000", bucketName, storageKey);
    }

    @Override
    public boolean isHealthy() {
        try {
            minioClient.bucketExists(
                io.minio.BucketExistsArgs.builder()
                    .bucket(bucketName)
                    .build()
            );
            return true;
        } catch (Exception e) {
            log.warn("MinIO health check failed", e);
            return false;
        }
    }
}
```

**Checklist**:
- [ ] Archivo creado
- [ ] Imports correctos
- [ ] Compila sin errores
- [ ] `./mvnw clean compile` OK

**Commit**:
```bash
git add apps/api/src/main/java/.../impl/MinioStorageService.java
git commit -m "feat: implement MinioStorageService with S3 operations"
git push origin feat/minio-storage-integration
```

---

### Paso 2.7: Crear LocalStorageService (fallback)

**Ruta**: `apps/api/src/main/java/.../media/service/storage/impl/LocalStorageService.java`

```java
package dev.pepe1603.portfolioapi.module.media.service.storage.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@ConditionalOnProperty(name = "storage.type", havingValue = "local", matchIfMissing = true)
@RequiredArgsConstructor
public class LocalStorageService implements StorageService {
    private final String uploadDir = "/srv/portfolio/uploads";

    @Override
    public String uploadFile(InputStream file, String filename, long maxSizeBytes, Set<String> allowedMimeTypes) 
            throws StorageException {
        try {
            String storageKey = UUID.randomUUID() + "-" + filename;
            String filePath = uploadDir + "/" + storageKey;
            
            Files.createDirectories(Paths.get(uploadDir));
            
            try (FileOutputStream fos = new FileOutputStream(filePath)) {
                file.transferTo(fos);
            }
            
            log.info("File uploaded locally: {}", storageKey);
            return getPublicUrl(storageKey);
        } catch (Exception e) {
            throw new StorageException("Local upload failed", e);
        }
    }

    @Override
    public InputStream downloadFile(String storageKey) throws StorageException {
        try {
            String filePath = uploadDir + "/" + storageKey;
            return new FileInputStream(filePath);
        } catch (Exception e) {
            throw new StorageException("Local download failed", e);
        }
    }

    @Override
    public void deleteFile(String storageKey) throws StorageException {
        try {
            String filePath = uploadDir + "/" + storageKey;
            Files.deleteIfExists(Paths.get(filePath));
            log.info("File deleted locally: {}", storageKey);
        } catch (Exception e) {
            throw new StorageException("Local delete failed", e);
        }
    }

    @Override
    public String getPublicUrl(String storageKey) {
        return "/uploads/" + storageKey;
    }

    @Override
    public boolean isHealthy() {
        try {
            Files.createDirectories(Paths.get(uploadDir));
            return true;
        } catch (Exception e) {
            log.warn("Local storage health check failed", e);
            return false;
        }
    }
}
```

**Checklist**:
- [ ] Archivo creado
- [ ] Compila sin errores

**Commit**:
```bash
git add apps/api/src/main/java/.../impl/LocalStorageService.java
git commit -m "feat: implement LocalStorageService fallback for development"
git push origin feat/minio-storage-integration
```

---

## SEMANA 3: Integración y Testing

### Paso 3.1: Modificar application.yml

En `apps/api/src/main/resources/application.yml`:

```yaml
storage:
  type: ${STORAGE_TYPE:local}
  minio:
    endpoint: ${MINIO_ENDPOINT:http://localhost:9000}
    access-key: ${MINIO_ACCESS_KEY}
    secret-key: ${MINIO_SECRET_KEY}
    bucket-name: portfolio
    region: us-east-1
```

**Checklist**:
- [ ] Variables de entorno agregadas
- [ ] Defaults apropiados

**Commit**:
```bash
git add apps/api/src/main/resources/application.yml
git commit -m "feat: add storage configuration properties"
git push origin feat/minio-storage-integration
```

---

### Paso 3.2: Modificar .env.example

```bash
# Storage
STORAGE_TYPE=local  # o "minio" para producción
MINIO_ENDPOINT=http://terramount-ip:9000
MINIO_ACCESS_KEY=api
MINIO_SECRET_KEY=<generated-secret>
```

**Commit**:
```bash
git add .env.example
git commit -m "docs: add storage env variables to .env.example"
git push origin feat/minio-storage-integration
```

---

### Paso 3.3: Inyectar StorageService en MediaService

**Modifica**: `apps/api/src/main/java/.../media/service/MediaService.java`

```java
@Service
@RequiredArgsConstructor
public class MediaService {
    private final StorageService storageService;  // ← ADD
    private final MediaRepository mediaRepository;
    
    public Media uploadMedia(MultipartFile file, Long projectId) throws StorageException {
        String publicUrl = storageService.uploadFile(
            file.getInputStream(),
            file.getOriginalFilename(),
            50 * 1024 * 1024,  // 50MB max
            Set.of("image/jpeg", "image/png", "image/webp", "application/pdf")
        );
        
        Media media = new Media(file.getOriginalFilename(), publicUrl, projectId);
        return mediaRepository.save(media);
    }
}
```

**Checklist**:
- [ ] StorageService inyectado
- [ ] uploadMedia() usa StorageService
- [ ] Compila sin errores

**Commit**:
```bash
git add apps/api/src/main/java/.../media/service/MediaService.java
git commit -m "refactor: inject StorageService into MediaService"
git push origin feat/minio-storage-integration
```

---

### Paso 3.4: Tests JUnit 5

**Ruta**: `apps/api/src/test/java/.../media/service/storage/MinioStorageServiceTest.java`

```java
package dev.pepe1603.portfolioapi.module.media.service.storage;

import io.minio.MinioClient;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@SpringBootTest
@Testcontainers
class MinioStorageServiceTest {
    
    @Container
    static GenericContainer<?> minioContainer = new GenericContainer<>("minio/minio:latest")
        .withExposedPorts(9000, 9001)
        .withEnv("MINIO_ROOT_USER", "minioadmin")
        .withEnv("MINIO_ROOT_PASSWORD", "minioadmin");

    @Autowired
    private MinioStorageService storageService;

    @Test
    void uploadFile_shouldSucceed_withValidFile() throws Exception {
        // Arrange
        InputStream file = new ByteArrayInputStream("test content".getBytes());
        
        // Act
        String url = storageService.uploadFile(
            file, 
            "test.txt", 
            1024 * 1024, 
            Set.of("text/plain")
        );
        
        // Assert
        assertNotNull(url);
        assertTrue(url.contains("minio"));
    }

    @Test
    void isHealthy_shouldReturnTrue() {
        assertTrue(storageService.isHealthy());
    }
}
```

**Checklist**:
- [ ] Test class creada
- [ ] Testcontainers importado
- [ ] `./mvnw test` pasa

**Commit**:
```bash
git add apps/api/src/test/java/.../MinioStorageServiceTest.java
git commit -m "test: add MinioStorageService tests with Testcontainers"
git push origin feat/minio-storage-integration
```

---

### Paso 3.5: Tests de integración MediaService

**Ruta**: `apps/api/src/test/java/.../media/service/MediaServiceTest.java`

```java
@SpringBootTest
class MediaServiceTest {
    
    @Autowired
    private MediaService mediaService;
    
    @Test
    void uploadMedia_shouldSaveToDatabase_andStorage() throws Exception {
        // Arrange
        MockMultipartFile file = new MockMultipartFile(
            "file", "test.jpg", "image/jpeg", "test content".getBytes()
        );
        
        // Act
        Media media = mediaService.uploadMedia(file, 1L);
        
        // Assert
        assertNotNull(media.getId());
        assertTrue(media.getUrl().contains("minio") || media.getUrl().contains("uploads"));
    }
}
```

**Commit**:
```bash
git add apps/api/src/test/java/.../media/service/MediaServiceTest.java
git commit -m "test: add MediaService integration tests"
git push origin feat/minio-storage-integration
```

---

### Paso 3.6: Ejecutar todos los tests

```bash
cd apps/api
./mvnw clean test -DfailIfNoTests=false
```

**Checklist**:
- [ ] Todos los tests pasan
- [ ] Coverage > 80% en StorageService
- [ ] Sin warnings

**Commit**: N/A (solo tests)

---

## SEMANA 4: Finalización y Merge

### Paso 4.1: Documentar en ARCHITECTURE.md

Añade sección nueva:

```markdown
## Storage Layer

### Architecture
- **Pattern**: Strategy Pattern
- **Interface**: StorageService
- **Implementations**:
  - MinioStorageService (S3-compatible, production)
  - LocalStorageService (filesystem, development)

### Configuration
- Seleccionar implementación por `STORAGE_TYPE` env var
- Credenciales en `.env`, nunca en código

### Security
- Server-side MIME + size validation
- Random filenames con UUID
- Rate limiting: 5 uploads/min per IP
- Logging de operaciones
```

**Commit**:
```bash
git add docs/ARCHITECTURE.md
git commit -m "docs: document MinIO storage architecture"
git push origin feat/minio-storage-integration
```

---

### Paso 4.2: Crear CHANGELOG entry

En `docs/CHANGELOG.md`:

```markdown
## [Unreleased]

### Added
- MinIO S3-compatible storage integration
- StorageService abstraction layer (Strategy pattern)
- Local storage fallback for development
- Server-side file validation (MIME type, size)
- Storage operation logging
- Testcontainers support for MinIO tests
```

**Commit**:
```bash
git add docs/CHANGELOG.md
git commit -m "docs: add MinIO integration to CHANGELOG"
git push origin feat/minio-storage-integration
```

---

### Paso 4.3: Code review checklist

- [ ] Todos los archivos compilansin errores
- [ ] Todos los tests pasan
- [ ] No hay TODO comments
- [ ] Documentación actualizada
- [ ] .env.example completado
- [ ] No hay secrets en código
- [ ] Logging apropiado en nivel INFO/DEBUG
- [ ] Excepciones bien mapeadas

---

### Paso 4.4: Merge a develop

```bash
# Asegurar que develop está actualizado
git checkout develop
git pull origin develop

# Merge feature branch
git merge feat/minio-storage-integration

# Push
git push origin develop

# Delete feature branch (opcional)
git branch -d feat/minio-storage-integration
```

**Checklist**:
- [ ] Merge sin conflictos
- [ ] develop branch actualizado
- [ ] CI/CD (si existe) pasa

**Commit**: N/A (merge commit)

---

### Paso 4.5: Validación en producción (Hostinger)

**Cuando despliegues a Hostinger (Fase 5)**:

1. Variables de entorno en Hostinger:
```bash
STORAGE_TYPE=minio
MINIO_ENDPOINT=http://terramount-ip:9000
MINIO_ACCESS_KEY=api
MINIO_SECRET_KEY=<secret>
```

2. Test manual:
```bash
# Desde navegador
POST /api/media/upload (test.jpg)
# Debe retornar URL a minio
GET /api/media/123
# Descarga funciona
```

---

## Timeline Final

| Semana | Qué | Commits |
|--------|-----|---------|
| 1 | Setup MinIO Terramount | 0 |
| 2 | Código Spring + interfaces | 6 |
| 3 | Tests + integración | 3 |
| 4 | Documentación + merge | 3 |

**Total commits esperados**: 12

---

## Rollback Plan

Si algo sale mal:

```bash
# Volver a branch anterior
git checkout develop
git reset --hard HEAD~12  # Deshace los 12 commits
git push -f origin develop
```

O usar los métodos de revert si ya está en main.

---

## Success Criteria ✅

- [x] MinIO corriendo en Terramount
- [x] StorageService interface implementada
- [x] MinIO + Local implementations funcionan
- [x] MediaService integrado sin breaking changes
- [x] Tests pasan (JUnit + Testcontainers)
- [x] Documentación actualizada
- [x] Feature en develop branch
- [x] Listo para producción (Fase 5)

---

**Next**: Implementar esta checklist sección por sección.
