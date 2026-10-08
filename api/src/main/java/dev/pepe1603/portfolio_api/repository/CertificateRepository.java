package dev.pepe1603.portfolio_api.repository;

import dev.pepe1603.portfolio_api.entity.Certificate;
import dev.pepe1603.portfolio_api.enums.CertificateStatus;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CertificateRepository extends JpaRepository<Certificate, UUID> {

    List<Certificate> findByStatus(CertificateStatus status, Sort sort);
}