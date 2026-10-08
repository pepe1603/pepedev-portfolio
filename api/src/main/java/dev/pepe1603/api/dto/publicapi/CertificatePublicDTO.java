package dev.pepe1603.api.dto.publicapi;

import java.time.LocalDate;

public record CertificatePublicDTO(
        String title,
        String issuer,
        String kind,
        LocalDate issueDate,
        LocalDate expiryDate,
        String credentialUrl,
        String imageUrl,
        boolean featured) {
}