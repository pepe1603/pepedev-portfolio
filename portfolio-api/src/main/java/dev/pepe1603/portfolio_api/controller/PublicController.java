package dev.pepe1603.portfolio_api.controller;

import java.time.Duration;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import dev.pepe1603.portfolio_api.service.PublicCacheService;
import dev.pepe1603.portfolio_api.service.PublicService;
import dev.pepe1603.portfolio_api.util.LocalizedText;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/public")
public class PublicController {

    private final PublicService publicService;
    private final PublicCacheService cacheService;
    private final ObjectMapper objectMapper;

    public PublicController(PublicService publicService, PublicCacheService cacheService, ObjectMapper objectMapper) {
        this.publicService = publicService;
        this.cacheService = cacheService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/profile")
    public ResponseEntity<String> getProfile(
            @RequestParam(defaultValue = "es") String lang,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        String resolved = LocalizedText.normalizeLang(lang);
        String body = cacheService.getProfile(resolved).orElseGet(() -> {
            String fresh = serialize(publicService.getProfile(resolved)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "profile no encontrado")));
            cacheService.putProfile(resolved, fresh);
            return fresh;
        });
        return respond(body, ifNoneMatch);
    }

    @GetMapping("/projects")
    public ResponseEntity<String> getProjects(
            @RequestParam(defaultValue = "es") String lang,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        String resolved = LocalizedText.normalizeLang(lang);
        String body = cacheService.getProjectsList(resolved).orElseGet(() -> {
            String fresh = serialize(publicService.getPublishedProjects(resolved));
            cacheService.putProjectsList(resolved, fresh);
            return fresh;
        });
        return respond(body, ifNoneMatch);
    }

    @GetMapping("/projects/{slug}")
    public ResponseEntity<String> getProject(
            @PathVariable String slug,
            @RequestParam(defaultValue = "es") String lang,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        String resolved = LocalizedText.normalizeLang(lang);
        String body = cacheService.getProject(slug, resolved).orElseGet(() -> {
            String fresh = serialize(publicService.getPublishedProjectBySlug(slug, resolved)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "projecto no encontrado")));
            cacheService.putProject(slug, resolved, fresh);
            return fresh;
        });
        return respond(body, ifNoneMatch);
    }

    private ResponseEntity<String> respond(String body, String ifNoneMatch) {
        String etag = cacheService.etagFor(body);
        CacheControl cacheControl = CacheControl.maxAge(Duration.ZERO).cachePublic().mustRevalidate();
        if (etag.equals(ifNoneMatch)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .eTag(etag)
                    .cacheControl(cacheControl)
                    .build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .eTag(etag)
                .cacheControl(cacheControl)
                .body(body);
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException e) {
            throw new IllegalStateException("No se pudo serializar la respuesta pública", e);
        }
    }
}