package dev.pepe1603.portfolio_api.controller;

import dev.pepe1603.portfolio_api.enums.StorageUse;
import dev.pepe1603.portfolio_api.service.StorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
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

    public AdminStorageController(StorageService storageService) {
        this.storageService = storageService;
    }

    @PostMapping
    @Operation(summary = "Subir fichero", description = "Multipart: parte 'file' obligatoria + 'use' por query o form (nunca ambos).",
            requestBody = @RequestBody(required = true, content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                    schema = @Schema(type = "object",
                            properties = {
                                    @StringToClassMapItem(key = "use", value = String.class),
                                    @StringToClassMapItem(key = "file", value = MultipartFile.class)}))))
    public Map<String, String> upload(@RequestParam String use, @RequestPart MultipartFile file) {
        StorageUse resolved = resolveUse(use);
        String url = storageService.store(resolved, file);
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