package dev.pepe1603.api.entity;

import dev.pepe1603.api.enums.CertificateKind;
import dev.pepe1603.api.enums.CertificateStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "certificates")
@Getter
@Setter
@NoArgsConstructor
public class Certificate {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, String> title;

    @Column(nullable = false, length = 120)
    private String issuer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CertificateKind kind;

    @Column(name = "issue_date")
    private LocalDate issueDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "credential_url", length = 255)
    private String credentialUrl;

    @Column(name = "image_url", length = 255)
    private String imageUrl;

    @Column(name = "is_featured", nullable = false)
    private boolean featured;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CertificateStatus status = CertificateStatus.DRAFT;

    @Column(name = "published_at")
    private Instant publishedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}