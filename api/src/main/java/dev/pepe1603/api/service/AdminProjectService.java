package dev.pepe1603.api.service;

import dev.pepe1603.api.dto.admin.ProjectRequest;
import dev.pepe1603.api.entity.GalleryImage;
import dev.pepe1603.api.entity.Project;
import dev.pepe1603.api.enums.AuditAction;
import dev.pepe1603.api.enums.AuditResource;
import dev.pepe1603.api.enums.ProjectStatus;
import dev.pepe1603.api.repository.ProjectRepository;
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
    private final AuditService auditService;

    public AdminProjectService(ProjectRepository projectRepository, PublicCacheService cacheService,
                               OrphanFileCleaner orphanFileCleaner, AuditService auditService) {
        this.projectRepository = projectRepository;
        this.cacheService = cacheService;
        this.orphanFileCleaner = orphanFileCleaner;
        this.auditService = auditService;
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
        Project saved = projectRepository.save(project);
        auditService.record(AuditAction.CREATE, AuditResource.PROJECT, saved.getId(), "slug=" + saved.getSlug());
        return saved;
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
        auditService.record(AuditAction.UPDATE, AuditResource.PROJECT, saved.getId(), "slug=" + saved.getSlug());
        return saved;
    }

    @Transactional
    public Project publish(UUID id) {
        Project project = findOrThrow(id);
        project.setStatus(ProjectStatus.PUBLISHED);
        project.setPublishedAt(Instant.now());
        Project saved = projectRepository.save(project);
        evictProjectPublic(saved);
        auditService.record(AuditAction.UPDATE, AuditResource.PROJECT, saved.getId(),
                "publicado: " + saved.getSlug());
        return saved;
    }

    @Transactional
    public Project unpublish(UUID id) {
        Project project = findOrThrow(id);
        project.setStatus(ProjectStatus.DRAFT);
        project.setPublishedAt(null);
        Project saved = projectRepository.save(project);
        evictProjectPublic(saved);
        auditService.record(AuditAction.UPDATE, AuditResource.PROJECT, saved.getId(),
                "despublicado: " + saved.getSlug());
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
        auditService.record(AuditAction.UPDATE, AuditResource.PROJECT, null,
                "reorden de " + ids.size() + " proyectos");
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
        auditService.record(AuditAction.DELETE, AuditResource.PROJECT, id, "slug=" + project.getSlug());
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