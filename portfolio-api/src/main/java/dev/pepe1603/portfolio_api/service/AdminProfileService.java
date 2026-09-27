package dev.pepe1603.portfolio_api.service;

import dev.pepe1603.portfolio_api.dto.admin.ProfileRequest;
import dev.pepe1603.portfolio_api.entity.Profile;
import dev.pepe1603.portfolio_api.enums.AuditAction;
import dev.pepe1603.portfolio_api.enums.AuditResource;
import dev.pepe1603.portfolio_api.repository.ProfileRepository;
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
        String oldAvatarUrl = profile.getAvatarUrl();
        String oldCvUrlEs = profile.getCvUrlEs();
        String oldCvUrlEn = profile.getCvUrlEn();
        applyRequest(profile, request);
        Profile saved = profileRepository.save(profile);
        cacheService.evictProfile();
        orphanFileCleaner.cleanupIfUnreferenced(oldAvatarUrl);
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