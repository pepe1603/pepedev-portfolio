package dev.pepe1603.api.controller;

import dev.pepe1603.api.dto.common.ApiProblemDetail;
import dev.pepe1603.api.dto.contact.ContactRequest;
import dev.pepe1603.api.exception.ContactRateLimitedException;
import dev.pepe1603.api.security.ContactRateLimiter;
import dev.pepe1603.api.service.ContactService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/contact")
public class ContactController {

    private final ContactService contactService;
    private final ContactRateLimiter rateLimiter;

    public ContactController(ContactService contactService, ContactRateLimiter rateLimiter) {
        this.contactService = contactService;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping
    @Operation(summary = "Enviar mensaje de contacto",
            description = "Persiste el mensaje y lo envía por email. Campo oculto 'website' = honeypot "
                    + "anti-spam (si viene relleno se responde 201 sin persistir). Sujeto a rate limit "
                    + "por IP (429 con Retry-After).")
    @ApiResponse(responseCode = "201", description = "Mensaje recibido (persistido o honeypot)")
    @ApiResponse(responseCode = "400", description = "Campos obligatorios ausentes o inválidos",
            content = @Content(schema = @Schema(implementation = ApiProblemDetail.class)))
    @ApiResponse(responseCode = "429", description = "Demasiados envíos (Retry-After en segundos)",
            content = @Content(schema = @Schema(implementation = ApiProblemDetail.class)))
    public ResponseEntity<Void> submit(@Valid @RequestBody ContactRequest request,
            HttpServletRequest servletRequest) {
        if (isHoneypotFilled(request)) {
            return ResponseEntity.status(HttpStatus.CREATED).build();
        }
        String ip = servletRequest.getRemoteAddr();
        if (rateLimiter.isBlocked(ip)) {
            throw new ContactRateLimitedException(rateLimiter.getWindowSeconds());
        }
        rateLimiter.recordRequest(ip);
        contactService.save(request, ip, servletRequest.getHeader(HttpHeaders.USER_AGENT), servletRequest.getLocale());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    private boolean isHoneypotFilled(ContactRequest request) {
        return request.website() != null && !request.website().isBlank();
    }
}