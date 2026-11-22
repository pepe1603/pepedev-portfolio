package dev.pepe1603.api.service;

import dev.pepe1603.api.dto.publicapi.CertificatePublicDTO;
import dev.pepe1603.api.dto.publicapi.ProfilePublicDTO;
import dev.pepe1603.api.dto.publicapi.ProjectDetailDTO;
import dev.pepe1603.api.dto.publicapi.ProjectSummaryDTO;
import dev.pepe1603.api.entity.Certificate;
import dev.pepe1603.api.entity.Profile;
import dev.pepe1603.api.entity.Project;
import dev.pepe1603.api.enums.CertificateStatus;
import dev.pepe1603.api.enums.ProjectStatus;
import dev.pepe1603.api.repository.CertificateRepository;
import dev.pepe1603.api.repository.ProfileRepository;
import dev.pepe1603.api.repository.ProjectRepository;
import dev.pepe1603.api.util.LocalizedText;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;



@Service
public class PublicService {

    private final ProfileRepository profileRepository;
    private final ProjectRepository projectRepository;
    private final CertificateRepository certificateRepository;

    public PublicService(ProfileRepository profileRepository,
            ProjectRepository projectRepository,
            CertificateRepository certificateRepository) {
        this.profileRepository = profileRepository;
        this.projectRepository = projectRepository;
        this.certificateRepository = certificateRepository;
    }

    public Optional<ProfilePublicDTO> getProfile(String lang) {
        String resolved = LocalizedText.normalizeLang(lang);
        return profileRepository.findById((short) 1)
                .map(profile -> toProfileDTO(profile, resolved));
    }

    public List<ProjectSummaryDTO> getPublishedProjects(String lang) {
        String resolved = LocalizedText.normalizeLang(lang);
        Sort sort = Sort.by(
                Sort.Order.asc("sortOrder"),
                Sort.Order.desc("publishedAt"),
                Sort.Order.asc("slug"));
        return projectRepository.findByStatus(ProjectStatus.PUBLISHED, sort)
                .stream()
                .map(project -> toProjectSummary(project, resolved))
                .toList();
    }

    public Optional<ProjectDetailDTO> getPublishedProjectBySlug(String slug, String lang) {
        String resolved = LocalizedText.normalizeLang(lang);
        return projectRepository.findBySlugAndStatus(slug, ProjectStatus.PUBLISHED)
                .map(project -> toProjectDetail(project, resolved));
    }

    public List<CertificatePublicDTO> getPublishedCertificates(String lang) {
        String resolved = LocalizedText.normalizeLang(lang);
        Sort sort = Sort.by(
                Sort.Order.asc("sortOrder"),
                Sort.Order.desc("issueDate").nullsLast(),
                Sort.Order.asc("createdAt"));
        return certificateRepository.findByStatus(CertificateStatus.PUBLISHED, sort)
                .stream()
                .map(certificate -> toCertificateDTO(certificate, resolved))
                .toList();
    }

    private ProfilePublicDTO toProfileDTO(Profile p, String lang) {
        return new ProfilePublicDTO(
                p.getFullName(),
                LocalizedText.resolve(p.getHeadline(), lang),
                LocalizedText.resolve(p.getBio(), lang),
                p.getLocation(),
                p.getGithubUrl(),
                p.getLinkedinUrl(),
                p.getEmailPublic(),
                p.getWebsiteUrl(),
                p.getCvUrlEs(),
                p.getCvUrlEn(),
                p.getAvatarUrl(),
                p.getSkills(),
                p.getExperiences());
    }

    private ProjectSummaryDTO toProjectSummary(Project p, String lang) {
        return new ProjectSummaryDTO(
                p.getSlug(),
                LocalizedText.resolve(p.getTitle(), lang),
                LocalizedText.resolve(p.getSubtitle(), lang),
                LocalizedText.resolve(p.getSummary(), lang),
                p.getStack(),
                p.getThumbnailUrl(),
                p.getPeriodStart(),
                p.getPeriodEnd(),
                p.isFeatured());
    }

    private ProjectDetailDTO toProjectDetail(Project p, String lang) {
        return new ProjectDetailDTO(
                p.getSlug(),
                LocalizedText.resolve(p.getTitle(), lang),
                LocalizedText.resolve(p.getSubtitle(), lang),
                LocalizedText.resolve(p.getSummary(), lang),
                LocalizedText.resolve(p.getDescriptionMd(), lang),
                p.getStack(),
                p.getThumbnailUrl(),
                p.getGallery(),
                p.getRepoUrl(),
                p.getDemoUrl(),
                p.getPeriodStart(),
                p.getPeriodEnd(),
                p.isFeatured());
    }

    private CertificatePublicDTO toCertificateDTO(Certificate c, String lang) {
        return new CertificatePublicDTO(
                LocalizedText.resolve(c.getTitle(), lang),
                c.getIssuer(),
                c.getKind().name().toLowerCase(Locale.ROOT),
                c.getIssueDate(),
                c.getExpiryDate(),
                c.getCredentialUrl(),
                c.getImageUrl(),
                c.isFeatured());
    }
}