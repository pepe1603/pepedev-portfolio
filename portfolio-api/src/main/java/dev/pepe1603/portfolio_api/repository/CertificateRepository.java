package dev.pepe1603.portfolio_api.repository;

import dev.pepe1603.portfolio_api.entity.Certificate;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CertificateRepository extends JpaRepository<Certificate, UUID> {
}