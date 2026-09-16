package dev.pepe1603.portfolio_api.service;

import dev.pepe1603.portfolio_api.dto.publicapi.ProfilePublicDTO;
import dev.pepe1603.portfolio_api.dto.publicapi.ProjectDetailDTO;
import dev.pepe1603.portfolio_api.dto.publicapi.ProjectSummaryDTO;
import dev.pepe1603.portfolio_api.entity.Profile;
import dev.pepe1603.portfolio_api.entity.Project;
import dev.pepe1603.portfolio_api.enums.ProjectStatus;
import dev.pepe1603.portfolio_api.repository.ProfileRepository;
import dev.pepe1603.portfolio_api.repository.ProjectRepository;
import dev.pepe1603.portfolio_api.util.LocalizedText;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;



@Service
public class PublicService {

    private final ProfileRepository profileRepository;
    private final ProjectRepository projectRepository;

    public PublicService(ProfileRepository profileRepository, ProjectRepository projectRepository) {
        this.profileRepository = profileRepository;
        this.projectRepository = projectRepository;
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
}