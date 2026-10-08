package dev.pepe1603.api.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import dev.pepe1603.api.dto.admin.ProfileRequest;
import dev.pepe1603.api.entity.Profile;
import dev.pepe1603.api.enums.AuditAction;
import dev.pepe1603.api.enums.AuditResource;
import dev.pepe1603.api.repository.ProfileRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AdminProfileServiceTest {

    private final ProfileRepository profileRepository = mock(ProfileRepository.class);
    private final PublicCacheService cacheService = mock(PublicCacheService.class);
    private final OrphanFileCleaner orphanFileCleaner = mock(OrphanFileCleaner.class);
    private final AuditService auditService = mock(AuditService.class);
    private final AdminProfileService service =
            new AdminProfileService(profileRepository, cacheService, orphanFileCleaner, auditService);

    @Test
    void updateLimpiaLosFicherosAntiguosQueDejanDeUsarse() {
        Profile profile = new Profile();
        profile.setAvatarUrl("/files/123e4567-e89b-12d3-a456-426614174000.png");
        profile.setCvUrlEs("/files/123e4567-e89b-12d3-a456-426614174001.pdf");
        profile.setCvUrlEn("/files/123e4567-e89b-12d3-a456-426614174002.pdf");
        given(profileRepository.findById((short) 1)).willReturn(Optional.of(profile));
        given(profileRepository.save(any(Profile.class))).willAnswer(inv -> inv.getArgument(0));

        ProfileRequest request = new ProfileRequest(
                "Pepe Dev",
                Map.of("es", "Desarrollador"),
                Map.of("es", "Bio"),
                null, null, null, null, null, null, null,
                "/files/123e4567-e89b-12d3-a456-426614174003.png",
                List.of(), List.of());

        service.update(request);

        verify(orphanFileCleaner).cleanupIfUnreferenced("/files/123e4567-e89b-12d3-a456-426614174000.png");
        verify(orphanFileCleaner).cleanupIfUnreferenced("/files/123e4567-e89b-12d3-a456-426614174001.pdf");
        verify(orphanFileCleaner).cleanupIfUnreferenced("/files/123e4567-e89b-12d3-a456-426614174002.pdf");
        verify(cacheService).evictProfile();
        verify(auditService).record(AuditAction.UPDATE, AuditResource.PROFILE, null, "perfil actualizado");
    }
}