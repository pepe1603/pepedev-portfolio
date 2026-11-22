package dev.pepe1603.api.repository;

import dev.pepe1603.api.entity.AuditEntry;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditRepository extends JpaRepository<AuditEntry, UUID> {
}
