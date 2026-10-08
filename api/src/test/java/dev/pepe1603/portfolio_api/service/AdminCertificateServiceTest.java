package dev.pepe1603.portfolio_api.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import dev.pepe1603.portfolio_api.dto.admin.CertificateRequest;
import dev.pepe1603.portfolio_api.entity.Certificate;
import dev.pepe1603.portfolio_api.enums.AuditAction;
import dev.pepe1603.portfolio_api.enums.AuditResource;
import dev.pepe1603.portfolio_api.enums.CertificateKind;
import dev.pepe1603.portfolio_api.enums.CertificateStatus;
import dev.pepe1603.portfolio_api.repository.CertificateRepository;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AdminCertificateServiceTest {

    private final CertificateRepository certificateRepository = mock(CertificateRepository.class);
    private final PublicCacheService cacheService = mock(PublicCacheService.class);
    private final OrphanFileCleaner orphanFileCleaner = mock(OrphanFileCleaner.class);
    private final AuditService auditService = mock(AuditService.class);
    private final AdminCertificateService service =
            new AdminCertificateService(certificateRepository, cacheService, orphanFileCleaner, auditService);

    @Test
    void deleteLimpiaLaImagenDelCertificadoEliminado() {
        UUID id = UUID.randomUUID();
        Certificate certificate = new Certificate();
        certificate.setId(id);
        certificate.setImageUrl("/files/123e4567-e89b-12d3-a456-426614174000.png");
        certificate.setStatus(CertificateStatus.PUBLISHED);
        given(certificateRepository.findById(id)).willReturn(Optional.of(certificate));

        service.delete(id);

        verify(certificateRepository).delete(certificate);
        verify(orphanFileCleaner).cleanupIfUnreferenced("/files/123e4567-e89b-12d3-a456-426614174000.png");
        verify(cacheService).evictCertificatesList();
        verify(auditService).record(eq(AuditAction.DELETE), eq(AuditResource.CERTIFICATE), eq(id), anyString());
    }

    @Test
    void updateLimpiaLaImagenAntiguaAlCambiarla() {
        UUID id = UUID.randomUUID();
        Certificate certificate = new Certificate();
        certificate.setId(id);
        certificate.setImageUrl("/files/123e4567-e89b-12d3-a456-426614174000.png");
        certificate.setStatus(CertificateStatus.DRAFT);
        given(certificateRepository.findById(id)).willReturn(Optional.of(certificate));
        given(certificateRepository.save(any(Certificate.class))).willAnswer(inv -> inv.getArgument(0));

        CertificateRequest request = new CertificateRequest(
                Map.of("es", "Título"), "Issuer", CertificateKind.CERTIFICATE,
                null, null, null, "/files/123e4567-e89b-12d3-a456-426614174001.png",
                false, 0);

        service.update(id, request);

        verify(orphanFileCleaner).cleanupIfUnreferenced("/files/123e4567-e89b-12d3-a456-426614174000.png");
        verify(cacheService, never()).evictCertificatesList();
        verify(auditService).record(eq(AuditAction.UPDATE), eq(AuditResource.CERTIFICATE), eq(id), anyString());
    }
}