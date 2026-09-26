package dev.pepe1603.portfolio_api.service;

import dev.pepe1603.portfolio_api.entity.Certificate;
import dev.pepe1603.portfolio_api.entity.Profile;
import dev.pepe1603.portfolio_api.entity.Project;
import dev.pepe1603.portfolio_api.repository.CertificateRepository;
import dev.pepe1603.portfolio_api.repository.ProfileRepository;
import dev.pepe1603.portfolio_api.repository.ProjectRepository;
import org.springframework.stereotype.Component;

@Component
public class StorageReferenceChecker {

    private final ProfileRepository profileRepository;
    private final ProjectRepository projectRepository;
    private final CertificateRepository certificateRepository;

    public StorageReferenceChecker(ProfileRepository profileRepository, ProjectRepository projectRepository,
                                   CertificateRepository certificateRepository) {
        this.profileRepository = profileRepository;
        this.projectRepository = projectRepository;
        this.certificateRepository = certificateRepository;
    }

    public boolean isReferenced(String fileName) {
        if (fileName == null) {
            return false;
        }
        return profileReferences(fileName)
                || projectReferences(fileName)
                || certificateReferences(fileName);
    }

    private boolean profileReferences(String fileName) {
        return profileRepository.findById((short) 1)
                .map(profile -> matches(profile.getAvatarUrl(), fileName)
                        || matches(profile.getCvUrlEs(), fileName)
                        || matches(profile.getCvUrlEn(), fileName))
                .orElse(false);
    }

    private boolean projectReferences(String fileName) {
        for (Project project : projectRepository.findAll()) {
            if (matches(project.getThumbnailUrl(), fileName)) {
                return true;
            }
            if (project.getGallery().stream().anyMatch(image -> matches(image.url(), fileName))) {
                return true;
            }
        }
        return false;
    }

    private boolean certificateReferences(String fileName) {
        for (Certificate certificate : certificateRepository.findAll()) {
            if (matches(certificate.getImageUrl(), fileName)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matches(String url, String fileName) {
        return fileName.equals(StorageService.baseName(url));
    }
}