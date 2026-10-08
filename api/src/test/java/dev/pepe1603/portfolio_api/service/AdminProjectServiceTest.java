package dev.pepe1603.portfolio_api.service;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import dev.pepe1603.portfolio_api.entity.GalleryImage;
import dev.pepe1603.portfolio_api.entity.Project;
import dev.pepe1603.portfolio_api.enums.AuditAction;
import dev.pepe1603.portfolio_api.enums.AuditResource;
import dev.pepe1603.portfolio_api.enums.ProjectStatus;
import dev.pepe1603.portfolio_api.repository.ProjectRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AdminProjectServiceTest {

    private final ProjectRepository projectRepository = mock(ProjectRepository.class);
    private final PublicCacheService cacheService = mock(PublicCacheService.class);
    private final OrphanFileCleaner orphanFileCleaner = mock(OrphanFileCleaner.class);
    private final AuditService auditService = mock(AuditService.class);
    private final AdminProjectService service =
            new AdminProjectService(projectRepository, cacheService, orphanFileCleaner, auditService);

    @Test
    void deleteLimpiaLaMiniaturaYLasImagenesDeLaGaleria() {
        UUID id = UUID.randomUUID();
        Project project = new Project();
        project.setId(id);
        project.setSlug("mi-proyecto");
        project.setThumbnailUrl("/files/123e4567-e89b-12d3-a456-426614174000.png");
        project.setGallery(List.of(
                new GalleryImage("/files/123e4567-e89b-12d3-a456-426614174001.png", "alt", null),
                new GalleryImage(null, "sin-url", null)));
        project.setStatus(ProjectStatus.PUBLISHED);
        given(projectRepository.findById(id)).willReturn(Optional.of(project));

        service.delete(id);

        verify(orphanFileCleaner).cleanupIfUnreferenced("/files/123e4567-e89b-12d3-a456-426614174000.png");
        verify(orphanFileCleaner).cleanupIfUnreferenced("/files/123e4567-e89b-12d3-a456-426614174001.png");
        verify(cacheService).evictProjectsList();
        verify(cacheService).evictProject("mi-proyecto");
        verify(auditService).record(AuditAction.DELETE, AuditResource.PROJECT, id, "slug=mi-proyecto");
    }
}