package dev.pepe1603.api.repository;

import dev.pepe1603.api.entity.AuthSession;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

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

    /**
     * Borra las sesiones cerradas o caducadas. Es un {@code delete}, así que necesita su propia
     * transacción: quien la llama (el controlador) no está dentro de ninguna, y una consulta
     * {@code @Modifying} sin transacción lanza {@code TransactionRequiredException} (500).
     */
    @Modifying
    @Transactional
    @Query("delete from AuthSession s where s.revokedAt is not null or s.expiresAt < :now")
    int prune(@Param("now") Instant now);

    /** Igual que {@link #prune(Instant)}: {@code update} {@code @Modifying} exige transacción. */
    @Modifying
    @Transactional
    @Query("update AuthSession s set s.revokedAt = :now where s.userId = :userId and s.revokedAt is null")
    int revokeAllByUserId(@Param("userId") UUID userId, @Param("now") Instant now);
}