package dev.pepe1603.portfolio_api.repository;

import dev.pepe1603.portfolio_api.entity.AuthSession;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuthSessionRepository extends JpaRepository<AuthSession, UUID> {

    Optional<AuthSession> findBySessionId(UUID sessionId);

    Optional<AuthSession> findByRefreshJti(String refreshJti);

    List<AuthSession> findAllByUserIdAndRevokedAtIsNullOrderByLastSeenAtDesc(UUID userId);

    /**
     * Si el usuario ya abrió sesión desde esa IP o con ese navegador. Lo usa
     * {@code SecurityNotificationService} para avisar solo de accesos nuevos, y se consulta antes de
     * guardar la sesión actual: si no, la sesión recién creada se encontraría a sí misma.
     */
    boolean existsByUserIdAndIpAddress(UUID userId, String ipAddress);

    boolean existsByUserIdAndUserAgent(UUID userId, String userAgent);

    @Modifying
    @Query("delete from AuthSession s where s.revokedAt is not null or s.expiresAt < :now")
    int prune(@Param("now") Instant now);

    @Modifying
    @Query("update AuthSession s set s.revokedAt = :now where s.userId = :userId and s.revokedAt is null")
    int revokeAllByUserId(@Param("userId") UUID userId, @Param("now") Instant now);
}