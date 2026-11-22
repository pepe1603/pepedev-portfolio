package dev.pepe1603.api.dto.publicapi;

import java.time.LocalDate;
import java.util.List;

public record ProjectSummaryDTO(
        String slug,
        String title,
        String subtitle,
        String summary,
        List<String> stack,
        String thumbnailUrl,
        LocalDate periodStart,
        LocalDate periodEnd,
        boolean featured) {
}