package dev.pepe1603.portfolio_api.controller;

import dev.pepe1603.portfolio_api.dto.contact.ContactRequest;
import dev.pepe1603.portfolio_api.security.ContactRateLimitedException;
import dev.pepe1603.portfolio_api.security.ContactRateLimiter;
import dev.pepe1603.portfolio_api.service.ContactService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
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
        contactService.save(request, ip, servletRequest.getHeader(HttpHeaders.USER_AGENT));
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    private boolean isHoneypotFilled(ContactRequest request) {
        return request.website() != null && !request.website().isBlank();
    }

    @ExceptionHandler(ContactRateLimitedException.class)
    public ResponseEntity<Map<String, Object>> handleRateLimited(ContactRateLimitedException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(e.getRetryAfterSeconds()))
                .body(Map.of(
                        "status", 429,
                        "error", "Too Many Requests",
                        "message", "Demasiados envíos de contacto. Inténtalo de nuevo más tarde"));
    }
}