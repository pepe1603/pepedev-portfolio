package dev.pepe1603.api.controller;

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
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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

    private StorageUse resolveUse(String use) {
        try {
            return StorageUse.valueOf(use.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Uso no permitido. Valores válidos: avatar, thumbnail, gallery, image, cv");
        }
    }
}