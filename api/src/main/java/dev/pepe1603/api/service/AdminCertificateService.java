package dev.pepe1603.api.service;

import dev.pepe1603.api.dto.admin.CertificateRequest;
import dev.pepe1603.api.entity.Certificate;
import dev.pepe1603.api.enums.AuditAction;
import dev.pepe1603.api.enums.AuditResource;
import dev.pepe1603.api.enums.CertificateStatus;
import dev.pepe1603.api.repository.CertificateRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminCertificateService {

    private static final Sort ADMIN_SORT = Sort.by(
            Sort.Order.asc("sortOrder"),
            Sort.Order.asc("createdAt"));

    private final CertificateRepository certificateRepository;
    private final PublicCacheService cacheService;
    private final OrphanFileCleaner orphanFileCleaner;
    private final AuditService auditService;

    public AdminCertificateService(CertificateRepository certificateRepository, PublicCacheService cacheService,
                                   OrphanFileCleaner orphanFileCleaner, AuditService auditService) {
        this.certificateRepository = certificateRepository;
        this.cacheService = cacheService;
        this.orphanFileCleaner = orphanFileCleaner;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<Certificate> listAll() {
        return certificateRepository.findAll(ADMIN_SORT);
    }

    @Transactional(readOnly = true)
    public Certificate getById(UUID id) {
        return findOrThrow(id);
    }

    @Transactional
    public Certificate create(CertificateRequest request) {
        Certificate certificate = new Certificate();
        applyRequest(certificate, request);
        certificate.setStatus(CertificateStatus.DRAFT);
        Certificate saved = certificateRepository.save(certificate);
        auditService.record(AuditAction.CREATE, AuditResource.CERTIFICATE, saved.getId(),
                "issuer=" + saved.getIssuer());
        return saved;
    }

    @Transactional
    public Certificate update(UUID id, CertificateRequest request) {
        Certificate certificate = findOrThrow(id);
        String oldImageUrl = certificate.getImageUrl();
        applyRequest(certificate, request);
        Certificate saved = certificateRepository.save(certificate);
        if (saved.getStatus() == CertificateStatus.PUBLISHED) {
            cacheService.evictCertificatesList();
        }
        orphanFileCleaner.cleanupIfUnreferenced(oldImageUrl);
        auditService.record(AuditAction.UPDATE, AuditResource.CERTIFICATE, saved.getId(),
                "issuer=" + saved.getIssuer());
        return saved;
    }

    @Transactional
    public Certificate publish(UUID id) {
        Certificate certificate = findOrThrow(id);
        certificate.setStatus(CertificateStatus.PUBLISHED);
        certificate.setPublishedAt(Instant.now());
        Certificate saved = certificateRepository.save(certificate);
        cacheService.evictCertificatesList();
        auditService.record(AuditAction.UPDATE, AuditResource.CERTIFICATE, saved.getId(),
                "publicado: " + saved.getTitle().getOrDefault("es", saved.getTitle().getOrDefault("en", "?")));
        return saved;
    }

    @Transactional
    public Certificate unpublish(UUID id) {
        Certificate certificate = findOrThrow(id);
        certificate.setStatus(CertificateStatus.DRAFT);
        certificate.setPublishedAt(null);
        Certificate saved = certificateRepository.save(certificate);
        cacheService.evictCertificatesList();
        auditService.record(AuditAction.UPDATE, AuditResource.CERTIFICATE, saved.getId(),
                "despublicado: " + saved.getTitle().getOrDefault("es", saved.getTitle().getOrDefault("en", "?")));
        return saved;
    }

    @Transactional
    public List<Certificate> reorder(List<UUID> ids) {
        for (int i = 0; i < ids.size(); i++) {
            Certificate certificate = findOrThrow(ids.get(i));
            certificate.setSortOrder(i);
            certificateRepository.save(certificate);
        }
        cacheService.evictCertificatesList();
        auditService.record(AuditAction.UPDATE, AuditResource.CERTIFICATE, null,
                "reorden de " + ids.size() + " certificados");
        return listAll();
    }

    @Transactional
    public void delete(UUID id) {
        Certificate certificate = findOrThrow(id);
        String imageUrl = certificate.getImageUrl();
        certificateRepository.delete(certificate);
        if (certificate.getStatus() == CertificateStatus.PUBLISHED) {
            cacheService.evictCertificatesList();
        }
        auditService.record(AuditAction.DELETE, AuditResource.CERTIFICATE, id,
                "issuer=" + certificate.getIssuer());
        orphanFileCleaner.cleanupIfUnreferenced(imageUrl);
    }

    private void applyRequest(Certificate certificate, CertificateRequest request) {
        certificate.setTitle(request.title());
        certificate.setIssuer(request.issuer());
        certificate.setKind(request.kind());
        certificate.setIssueDate(request.issueDate());
        certificate.setExpiryDate(request.expiryDate());
        certificate.setCredentialUrl(request.credentialUrl());
        certificate.setImageUrl(request.imageUrl());
        certificate.setFeatured(request.featured());
        certificate.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
    }

    private Certificate findOrThrow(UUID id) {
        return certificateRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Certificado no encontrado"));
    }
}