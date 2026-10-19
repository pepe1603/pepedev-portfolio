package dev.pepe1603.api.controller;

import dev.pepe1603.api.dto.admin.PageResponse;
import dev.pepe1603.api.dto.admin.StorageObjectResponse;
import dev.pepe1603.api.dto.common.ApiProblemDetail;
import dev.pepe1603.api.enums.AuditAction;
import dev.pepe1603.api.enums.AuditResource;
import dev.pepe1603.api.enums.StorageUse;
import dev.pepe1603.api.service.AuditService;
import dev.pepe1603.api.service.StorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.StringToClassMapItem;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/admin/storage")
public class AdminStorageController {

    private static final int MAX_PAGE_SIZE = 100;

    private final StorageService storageService;
    private final AuditService auditService;

    public AdminStorageController(StorageService storageService, AuditService auditService) {
        this.storageService = storageService;
        this.auditService = auditService;
    }

    @PostMapping
    @Operation(summary = "Subir fichero", description = "Multipart: parte 'file' obligatoria + 'use' por query o form (nunca ambos). "
            + "Validación por magic bytes: jpeg/png/webp (imágenes) y pdf (solo para 'cv'). Límites 5 MB imágenes / 10 MB CV.",
            requestBody = @RequestBody(required = true, content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                    schema = @Schema(type = "object",
                            properties = {
                                    @StringToClassMapItem(key = "use", value = String.class),
                                    @StringToClassMapItem(key = "file", value = MultipartFile.class)}))))
    @ApiResponse(responseCode = "200", description = "{ \"url\": \"https://.../files/<uuid>.<ext>\" }")
    @ApiResponse(responseCode = "400", description = "use inválido/duplicado, fichero vacío o parte file ausente",
            content = @Content(schema = @Schema(implementation = ApiProblemDetail.class)))
    @ApiResponse(responseCode = "413", description = "Fichero mayor que el límite (5/10 MB negocio o 15/20 MB multipart del contenedor)",
            content = @Content(schema = @Schema(implementation = ApiProblemDetail.class)))
    @ApiResponse(responseCode = "415", description = "Tipo de fichero no permitido (p. ej. pdf para un uso que no es 'cv')",
            content = @Content(schema = @Schema(implementation = ApiProblemDetail.class)))
    public Map<String, String> upload(@RequestParam String use, @RequestPart MultipartFile file) {
        StorageUse resolved = resolveUse(use);
        String url = storageService.store(resolved, file);
        auditService.record(AuditAction.UPLOAD, AuditResource.STORAGE, null, url);
        return Map.of("url", url);
    }

    @GetMapping
    @Operation(summary = "Listar ficheros del bucket",
            description = "Lista paginada de los objetos del bucket, con su URL pública, tamaño, fecha y tipo. "
                    + "Se ordena por fecha de modificación descendente (el más reciente primero).")
    @ApiResponse(responseCode = "200", description = "Página de objetos: { items, page, size, totalElements, totalPages, last }")
    @ApiResponse(responseCode = "400", description = "'page' o 'size' fuera de rango",
            content = @Content(schema = @Schema(implementation = ApiProblemDetail.class)))
    public PageResponse<StorageObjectResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "24") int size) {
        if (page < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "parámetro 'page' debe ser >= 0");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "parámetro 'size' debe estar entre 1 y " + MAX_PAGE_SIZE);
        }
        // El bucket se lista entero y se pagina en memoria: S3 no tiene offset, solo marker,
        // y para el tamaño de este bucket (docs/STORAGE.md §4) no compensa exponerlo.
        List<StorageObjectResponse> all = storageService.list().stream()
                .sorted(Comparator.comparing(StorageObjectResponse::lastModified).reversed())
                .toList();
        Pageable pageable = PageRequest.of(page, size);
        int from = (int) Math.min((long) page * size, all.size());
        int to = Math.min(from + size, all.size());
        return PageResponse.of(new PageImpl<>(all.subList(from, to), pageable, all.size()));
    }

    @DeleteMapping("/{name}")
    @Operation(summary = "Borrar un fichero del bucket",
            description = "Borra un objeto por su nombre (<uuid>.<ext>). Un nombre que no encaje con ese patrón "
                    + "se rechaza con 400: solo se puede tocar lo que la propia API subió. Responde 204 aunque el "
                    + "objeto no exista (S3 borra de forma idempotente).")
    @ApiResponse(responseCode = "204", description = "Objeto borrado (o ya no estaba)")
    @ApiResponse(responseCode = "400", description = "Nombre con forma inválida",
            content = @Content(schema = @Schema(implementation = ApiProblemDetail.class)))
    public ResponseEntity<Void> delete(@PathVariable String name) {
        if (!StorageService.isManagedName(name)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Nombre de fichero no válido. Se espera <uuid>.<jpg|png|webp|pdf>");
        }
        storageService.deleteByName(name);
        auditService.record(AuditAction.DELETE, AuditResource.STORAGE, null, name);
        return ResponseEntity.noContent().build();
    }

    private StorageUse resolveUse(String use) {
        try {
            return StorageUse.valueOf(use.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Uso no permitido. Valores válidos: avatar, thumbnail, gallery, image, cv");
        }
    }
}