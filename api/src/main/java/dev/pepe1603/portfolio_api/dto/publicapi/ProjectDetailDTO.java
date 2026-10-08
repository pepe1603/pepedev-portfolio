package dev.pepe1603.portfolio_api.dto.publicapi;

import dev.pepe1603.portfolio_api.entity.GalleryImage;

import java.time.LocalDate;
import java.util.List;

public record ProjectDetailDTO(
        String slug,
        String title,
        String subtitle,
        String summary,
        String descriptionMd,
        List<String> stack,
        String thumbnailUrl,
        List<GalleryImage> gallery,
        String repoUrl,
        String demoUrl,
        LocalDate periodStart,
        LocalDate periodEnd,
        boolean featured) {
}