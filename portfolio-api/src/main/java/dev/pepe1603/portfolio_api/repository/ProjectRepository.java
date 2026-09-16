package dev.pepe1603.portfolio_api.repository;

import dev.pepe1603.portfolio_api.entity.Project;
import dev.pepe1603.portfolio_api.enums.ProjectStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<Project, UUID> {

    List<Project> findByStatus(ProjectStatus status, Sort sort);

    Optional<Project> findBySlugAndStatus(String slug, ProjectStatus status);
}