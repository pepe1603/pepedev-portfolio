package dev.pepe1603.portfolio_api.service;

import dev.pepe1603.portfolio_api.dto.admin.ProjectRequest;
import dev.pepe1603.portfolio_api.entity.GalleryImage;
import dev.pepe1603.portfolio_api.entity.Project;
import dev.pepe1603.portfolio_api.enums.ProjectStatus;
import dev.pepe1603.portfolio_api.repository.ProjectRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminProjectService {

    private static final Sort ADMIN_SORT = Sort.by(
            Sort.Order.asc("sortOrder"),
            Sort.Order.asc("createdAt"));

    private final ProjectRepository projectRepository;
    private final PublicCacheService cacheService;
    private final OrphanFileCleaner orphanFileCleaner;

    public AdminProjectService(ProjectRepository projectRepository, PublicCacheService cacheService,
                               OrphanFileCleaner orphanFileCleaner) {
        this.projectRepository = projectRepository;
        this.cacheService = cacheService;
        this.orphanFileCleaner = orphanFileCleaner;
    }

    @Transactional(readOnly = true)
    public List<Project> listAll() {
        return projectRepository.findAll(ADMIN_SORT);
    }

    @Transactional(readOnly = true)
    public Project getById(UUID id) {
        return findOrThrow(id);
    }

    @Transactional
    public Project create(ProjectRequest request) {
        ensureSlugFree(request.slug());
        Project project = new Project();
        applyRequest(project, request);
        project.setStatus(ProjectStatus.DRAFT);
        return projectRepository.save(project);
    }

    @Transactional
    public Project update(UUID id, ProjectRequest request) {
        Project project = findOrThrow(id);
        ensureSlugFreeExcludingSelf(request.slug(), id);
        String oldSlug = project.getSlug();
        List<String> replacedFileUrls = fileUrls(project);
        applyRequest(project, request);
        Project saved = projectRepository.save(project);
        if (saved.getStatus() == ProjectStatus.PUBLISHED) {
            cacheService.evictProjectsList();
            cacheService.evictProject(oldSlug);
            if (!oldSlug.equals(saved.getSlug())) {
                cacheService.evictProject(saved.getSlug());
            }
        }
        for (String url : replacedFileUrls) {
            orphanFileCleaner.cleanupIfUnreferenced(url);
        }
        return saved;
    }

    @Transactional
    public Project publish(UUID id) {
        Project project = findOrThrow(id);
        project.setStatus(ProjectStatus.PUBLISHED);
        project.setPublishedAt(Instant.now());
        Project saved = projectRepository.save(project);
        evictProjectPublic(saved);
        return saved;
    }

    @Transactional
    public Project unpublish(UUID id) {
        Project project = findOrThrow(id);
        project.setStatus(ProjectStatus.DRAFT);
        project.setPublishedAt(null);
        Project saved = projectRepository.save(project);
        evictProjectPublic(saved);
        return saved;
    }

    @Transactional
    public List<Project> reorder(List<UUID> ids) {
        for (int i = 0; i < ids.size(); i++) {
            Project project = findOrThrow(ids.get(i));
            project.setSortOrder(i);
            projectRepository.save(project);
        }
        cacheService.evictProjectsList();
        return listAll();
    }

    @Transactional
    public void delete(UUID id) {
        Project project = findOrThrow(id);
        List<String> fileUrls = fileUrls(project);
        projectRepository.delete(project);
        if (project.getStatus() == ProjectStatus.PUBLISHED) {
            cacheService.evictProjectsList();
            cacheService.evictProject(project.getSlug());
        }
        for (String url : fileUrls) {
            orphanFileCleaner.cleanupIfUnreferenced(url);
        }
    }

    private static List<String> fileUrls(Project project) {
        List<String> urls = new ArrayList<>();
        if (project.getThumbnailUrl() != null) {
            urls.add(project.getThumbnailUrl());
        }
        for (GalleryImage image : project.getGallery()) {
            if (image.url() != null) {
                urls.add(image.url());
            }
        }
        return urls;
    }

    private void applyRequest(Project project, ProjectRequest request) {
        project.setSlug(request.slug());
        project.setTitle(request.title());
        project.setSubtitle(request.subtitle());
        project.setSummary(request.summary());
        project.setDescriptionMd(request.descriptionMd());
        project.setThumbnailUrl(request.thumbnailUrl());
        project.setGallery(request.gallery() == null ? new ArrayList<>() : request.gallery());
        project.setRepoUrl(request.repoUrl());
        project.setDemoUrl(request.demoUrl());
        project.setStack(request.stack() == null ? new ArrayList<>() : request.stack());
        project.setPeriodStart(request.periodStart());
        project.setPeriodEnd(request.periodEnd());
        project.setFeatured(request.featured());
        project.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
    }

    private void ensureSlugFree(String slug) {
        if (projectRepository.existsBySlug(slug)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe un proyecto con el slug '" + slug + "'");
        }
    }

    private void ensureSlugFreeExcludingSelf(String slug, UUID id) {
        if (projectRepository.existsBySlugAndIdNot(slug, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe un proyecto con el slug '" + slug + "'");
        }
    }

    private Project findOrThrow(UUID id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Proyecto no encontrado"));
    }

    private void evictProjectPublic(Project project) {
        cacheService.evictProjectsList();
        cacheService.evictProject(project.getSlug());
    }
}