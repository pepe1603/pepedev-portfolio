package dev.pepe1603.api.service;

import dev.pepe1603.api.dto.admin.ProfileRequest;
import dev.pepe1603.api.entity.Profile;
import dev.pepe1603.api.enums.AuditAction;
import dev.pepe1603.api.enums.AuditResource;
import dev.pepe1603.api.repository.ProfileRepository;
import java.util.ArrayList;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminProfileService {

    private final ProfileRepository profileRepository;
    private final PublicCacheService cacheService;
    private final AuditService auditService;

    public AdminProfileService(ProfileRepository profileRepository, PublicCacheService cacheService,
                               AuditService auditService) {
        this.profileRepository = profileRepository;
        this.cacheService = cacheService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public Profile get() {
        return profileRepository.findById((short) 1)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Perfil no encontrado"));
    }

    @Transactional
    public Profile update(ProfileRequest request) {
        Profile profile = get();
        applyRequest(profile, request);
        Profile saved = profileRepository.save(profile);
        cacheService.evictProfile();
        // Cambiar o quitar el avatar/CV NO borra el fichero anterior del bucket.
        // Antes se llamaba a OrphanFileCleaner aquí, y eso hacía que poner un
        // avatar nuevo borrara el viejo para siempre: el bucket no tiene
        // versionado, así que era irreversible. Un avatar sustituido suele
        // querer reutilizarse, y borrar es una decisión del admin, no un efecto
        // secundario de guardar. El fichero se queda en el bucket; desde Medios
        // se borra a mano cuando de verdad sobra.
        auditService.record(AuditAction.UPDATE, AuditResource.PROFILE, null, "perfil actualizado");
        return saved;
    }

    private void applyRequest(Profile profile, ProfileRequest request) {
        profile.setFullName(request.fullName());
        profile.setHeadline(request.headline());
        profile.setBio(request.bio());
        profile.setLocation(request.location());
        profile.setGithubUrl(request.githubUrl());
        profile.setLinkedinUrl(request.linkedinUrl());
        profile.setEmailPublic(request.emailPublic());
        profile.setWebsiteUrl(request.websiteUrl());
        profile.setCvUrlEs(request.cvUrlEs());
        profile.setCvUrlEn(request.cvUrlEn());
        profile.setAvatarUrl(request.avatarUrl());
        profile.setSkills(request.skills() == null ? new ArrayList<>() : request.skills());
        profile.setExperiences(request.experiences() == null ? new ArrayList<>() : request.experiences());
    }
}