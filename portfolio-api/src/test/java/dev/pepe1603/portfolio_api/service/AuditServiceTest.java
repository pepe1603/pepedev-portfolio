package dev.pepe1603.portfolio_api.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import dev.pepe1603.portfolio_api.entity.AuditEntry;
import dev.pepe1603.portfolio_api.entity.User;
import dev.pepe1603.portfolio_api.enums.AuditAction;
import dev.pepe1603.portfolio_api.enums.AuditResource;
import dev.pepe1603.portfolio_api.enums.UserRole;
import dev.pepe1603.portfolio_api.repository.AuditRepository;
import dev.pepe1603.portfolio_api.security.AppUserDetails;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class AuditServiceTest {

    private final AuditRepository auditRepository = mock(AuditRepository.class);
    private final AuditService service = new AuditService(auditRepository);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void registraConActorAutenticado() {
        User admin = new User();
        admin.setId(UUID.fromString("11111111-1111-4111-8111-111111111111"));
        admin.setEmail("admin@pepe.dev");
        admin.setRole(UserRole.ADMIN);
        SecurityContextHolder.getContext()
                .setAuthentication(new TestingAuthenticationToken(new AppUserDetails(admin), null));

        service.record(AuditAction.UPDATE, AuditResource.PROJECT,
                UUID.fromString("22222222-2222-4222-8222-222222222222"), "slug=post-uno");

        verify(auditRepository).save(any(AuditEntry.class));
        AuditEntry saved = captureSaved();
        org.assertj.core.api.Assertions.assertThat(saved.getActorId())
                .isEqualTo(UUID.fromString("11111111-1111-4111-8111-111111111111"));
        org.assertj.core.api.Assertions.assertThat(saved.getActorEmail()).isEqualTo("admin@pepe.dev");
        org.assertj.core.api.Assertions.assertThat(saved.getAction()).isEqualTo(AuditAction.UPDATE);
        org.assertj.core.api.Assertions.assertThat(saved.getResource()).isEqualTo(AuditResource.PROJECT);
        org.assertj.core.api.Assertions.assertThat(saved.getResourceId())
                .isEqualTo("22222222-2222-4222-8222-222222222222");
        org.assertj.core.api.Assertions.assertThat(saved.getDetail()).isEqualTo("slug=post-uno");
        org.assertj.core.api.Assertions.assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void sinAutenticacionUsaSistema() {
        service.record(AuditAction.DELETE, AuditResource.MESSAGE, null, "sin actor");

        AuditEntry saved = captureSaved();
        org.assertj.core.api.Assertions.assertThat(saved.getActorEmail()).isEqualTo("SISTEMA");
        org.assertj.core.api.Assertions.assertThat(saved.getActorId()).isNull();
        org.assertj.core.api.Assertions.assertThat(saved.getResourceId()).isNull();
    }

    @Test
    void detailMuyLargoSeTrunca() {
        String detail = "x".repeat(1500);
        service.record(AuditAction.UPLOAD, AuditResource.STORAGE, null, detail);

        AuditEntry saved = captureSaved();
        org.assertj.core.api.Assertions.assertThat(saved.getDetail()).hasSize(1000);
    }

    @Test
    void detailNullNoRompe() {
        service.record(AuditAction.ARCHIVE, AuditResource.MESSAGE, UUID.randomUUID(), null);

        org.assertj.core.api.Assertions.assertThat(captureSaved().getDetail()).isNull();
    }

    private AuditEntry captureSaved() {
        org.mockito.ArgumentCaptor<AuditEntry> captor = org.mockito.ArgumentCaptor.forClass(AuditEntry.class);
        verify(auditRepository).save(captor.capture());
        return captor.getValue();
    }
}