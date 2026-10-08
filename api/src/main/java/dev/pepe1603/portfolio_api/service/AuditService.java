package dev.pepe1603.portfolio_api.service;

import dev.pepe1603.portfolio_api.entity.AuditEntry;
import dev.pepe1603.portfolio_api.enums.AuditAction;
import dev.pepe1603.portfolio_api.enums.AuditResource;
import dev.pepe1603.portfolio_api.repository.AuditRepository;
import dev.pepe1603.portfolio_api.security.AppUserDetails;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    static final int MAX_DETAIL_LENGTH = 1000;

    private final AuditRepository auditRepository;

    public AuditService(AuditRepository auditRepository) {
        this.auditRepository = auditRepository;
    }

    @Transactional
    public void record(AuditAction action, AuditResource resource, UUID resourceId, String detail) {
        AuditEntry entry = new AuditEntry();
        applyActor(entry);
        entry.setAction(action);
        entry.setResource(resource);
        entry.setResourceId(resourceId != null ? resourceId.toString() : null);
        entry.setDetail(truncate(detail));
        auditRepository.save(entry);
    }

    private void applyActor(AuditEntry entry) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AppUserDetails userDetails) {
            entry.setActorId(userDetails.getUser().getId());
            entry.setActorEmail(userDetails.getUser().getEmail());
        } else {
            entry.setActorEmail("SISTEMA");
        }
    }

    private String truncate(String detail) {
        if (detail == null || detail.length() <= MAX_DETAIL_LENGTH) {
            return detail;
        }
        return detail.substring(0, MAX_DETAIL_LENGTH);
    }
}