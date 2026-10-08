package dev.pepe1603.portfolio_api.controller;

import dev.pepe1603.portfolio_api.dto.admin.ProjectRequest;
import dev.pepe1603.portfolio_api.entity.Project;
import dev.pepe1603.portfolio_api.service.AdminProjectService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/projects")
public class AdminProjectController {

    private final AdminProjectService projectService;

    public AdminProjectController(AdminProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping
    public List<Project> list() {
        return projectService.listAll();
    }

    @GetMapping("/{id}")
    public Project get(@PathVariable UUID id) {
        return projectService.getById(id);
    }

    @PostMapping
    public ResponseEntity<Project> create(@Valid @RequestBody ProjectRequest request) {
        Project created = projectService.create(request);
        return ResponseEntity.created(URI.create("/admin/projects/" + created.getId())).body(created);
    }

    @PutMapping("/{id}")
    public Project update(@PathVariable UUID id, @Valid @RequestBody ProjectRequest request) {
        return projectService.update(id, request);
    }

    @PatchMapping("/{id}/publish")
    public Project publish(@PathVariable UUID id) {
        return projectService.publish(id);
    }

    @PatchMapping("/{id}/unpublish")
    public Project unpublish(@PathVariable UUID id) {
        return projectService.unpublish(id);
    }

    @PutMapping("/order")
    public List<Project> reorder(@RequestBody List<UUID> ids) {
        return projectService.reorder(ids);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        projectService.delete(id);
        return ResponseEntity.noContent().build();
    }
}