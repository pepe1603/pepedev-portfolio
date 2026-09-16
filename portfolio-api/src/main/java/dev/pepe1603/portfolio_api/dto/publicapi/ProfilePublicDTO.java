package dev.pepe1603.portfolio_api.dto.publicapi;

import dev.pepe1603.portfolio_api.entity.Experience;
import dev.pepe1603.portfolio_api.entity.Skill;

import java.util.List;

public record ProfilePublicDTO(
        String fullName,
        String headline,
        String bio,
        String location,
        String githubUrl,
        String linkedinUrl,
        String emailPublic,
        String websiteUrl,
        String cvUrlEs,
        String cvUrlEn,
        String avatarUrl,
        List<Skill> skills,
        List<Experience> experiences) {
}