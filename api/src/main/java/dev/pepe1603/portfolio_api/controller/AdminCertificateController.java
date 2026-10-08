package dev.pepe1603.portfolio_api.controller;

import dev.pepe1603.portfolio_api.dto.admin.CertificateRequest;
import dev.pepe1603.portfolio_api.entity.Certificate;
import dev.pepe1603.portfolio_api.service.AdminCertificateService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/certificates")
public class AdminCertificateController {

    private final AdminCertificateService certificateService;

    public AdminCertificateController(AdminCertificateService certificateService) {
        this.certificateService = certificateService;
    }

    @GetMapping
    public List<Certificate> list() {
        return certificateService.listAll();
    }

    @GetMapping("/{id}")
    public Certificate get(@PathVariable UUID id) {
        return certificateService.getById(id);
    }

    @PostMapping
    public ResponseEntity<Certificate> create(@Valid @RequestBody CertificateRequest request) {
        Certificate created = certificateService.create(request);
        return ResponseEntity.created(URI.create("/admin/certificates/" + created.getId())).body(created);
    }

    @PutMapping("/{id}")
    public Certificate update(@PathVariable UUID id, @Valid @RequestBody CertificateRequest request) {
        return certificateService.update(id, request);
    }

    @PatchMapping("/{id}/publish")
    public Certificate publish(@PathVariable UUID id) {
        return certificateService.publish(id);
    }

    @PatchMapping("/{id}/unpublish")
    public Certificate unpublish(@PathVariable UUID id) {
        return certificateService.unpublish(id);
    }

    @PutMapping("/order")
    public List<Certificate> reorder(@RequestBody List<UUID> ids) {
        return certificateService.reorder(ids);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        certificateService.delete(id);
        return ResponseEntity.noContent().build();
    }
}