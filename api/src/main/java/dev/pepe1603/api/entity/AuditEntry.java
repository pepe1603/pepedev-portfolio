package dev.pepe1603.api.entity;

import dev.pepe1603.api.enums.AuditAction;
import dev.pepe1603.api.enums.AuditResource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "audit_log")
@Getter
@Setter
@NoArgsConstructor
public class AuditEntry {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "actor_email", length = 255)
    private String actorEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AuditAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false, length = 32)
    private AuditResource resource;

    @Column(name = "resource_id", length = 64)
    private String resourceId;

    @Column(length = 1000)
    private String detail;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}