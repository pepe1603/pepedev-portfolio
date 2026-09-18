package dev.pepe1603.portfolio_api.controller;

import dev.pepe1603.portfolio_api.dto.publicapi.ProfilePublicDTO;
import dev.pepe1603.portfolio_api.service.PublicCacheService;
import dev.pepe1603.portfolio_api.service.PublicService;
import dev.pepe1603.portfolio_api.util.LocalizedText;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/public/cv")
public class CvController {

    private final PublicService publicService;
    private final PublicCacheService cacheService;
    private final ObjectMapper objectMapper;

    public CvController(PublicService publicService, PublicCacheService cacheService, ObjectMapper objectMapper) {
        this.publicService = publicService;
        this.cacheService = cacheService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/{lang}")
    public ResponseEntity<Void> redirectCv(@PathVariable String lang) {
        String resolved = LocalizedText.normalizeLang(lang);
        String body = cacheService.getProfile(resolved).orElseGet(() -> {
            String fresh = serialize(publicService.getProfile(resolved)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "profile no encontrado")));
            cacheService.putProfile(resolved, fresh);
            return fresh;
        });
        ProfilePublicDTO profile = readProfile(body);
        String cvUrl = pickCvUrl(profile, resolved);
        if (cvUrl == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "CV no disponible");
        }
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(cvUrl)).build();
    }

    private String pickCvUrl(ProfilePublicDTO profile, String lang) {
        if ("en".equals(lang)) {
            return profile.cvUrlEn() != null ? profile.cvUrlEn() : profile.cvUrlEs();
        }
        return profile.cvUrlEs() != null ? profile.cvUrlEs() : profile.cvUrlEn();
    }

    private ProfilePublicDTO readProfile(String body) {
        try {
            return objectMapper.readValue(body, ProfilePublicDTO.class);
        } catch (JacksonException e) {
            throw new IllegalStateException("No se pudo deserializar la caché de profile", e);
        }
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException e) {
            throw new IllegalStateException("No se pudo serializar la respuesta pública", e);
        }
    }
}