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
    private final OrphanFileCleaner orphanFileCleaner;
    private final AuditService auditService;

    public AdminProfileService(ProfileRepository profileRepository, PublicCacheService cacheService,
                               OrphanFileCleaner orphanFileCleaner, AuditService auditService) {
        this.profileRepository = profileRepository;
        this.cacheService = cacheService;
        this.orphanFileCleaner = orphanFileCleaner;
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
        // Los CV anteriores se leen ANTES de aplicar el request: si el request
        // los cambia, después ya no habría forma de saber cuáles eran.
        String oldCvUrlEs = profile.getCvUrlEs();
        String oldCvUrlEn = profile.getCvUrlEn();
        applyRequest(profile, request);
        Profile saved = profileRepository.save(profile);
        cacheService.evictProfile();
        // Un CV por idioma: al sustituirlo, el PDF anterior se borra —pero solo
        // si ya no lo usa NADIE. La comprobación es lo que evita el caso malo:
        // si el CV ES viejo resultaba ser también el CV EN (el mismo PDF en los
        // dos), o lo usa algo más, no se toca. El borrado de huérfanos ya lleva
        // esa salvaguarda; aquí no se duplica.
        //
        // El AVATAR, en cambio, no se limpia nunca: el bucket puede tener varios
        // y el perfil solo apunta a uno, así que cambiar de avatar no debe
        // borrar el anterior (se podría querer volver a él). Para deshacerse de
        // una imagen sobrante está Medios.
        orphanFileCleaner.cleanupIfUnreferenced(oldCvUrlEs);
        orphanFileCleaner.cleanupIfUnreferenced(oldCvUrlEn);
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