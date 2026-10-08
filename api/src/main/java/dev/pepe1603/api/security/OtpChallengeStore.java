package dev.pepe1603.api.security;

import java.time.Duration;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class OtpChallengeStore {

    private static final String KEY_PREFIX = "auth:otp:";
    private static final String ATTEMPTS_SUFFIX = ":attempts";
    private static final String RESEND_SUFFIX = ":resend";

    private final StringRedisTemplate redis;

    public OtpChallengeStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public String create(String email, String codeHash, Duration ttl) {
        String challengeId = UUID.randomUUID().toString();
        redis.opsForValue().set(KEY_PREFIX + challengeId, email + "|" + codeHash, ttl);
        return challengeId;
    }

    public Optional<OtpChallenge> peek(String challengeId) {
        String raw = redis.opsForValue().get(KEY_PREFIX + challengeId);
        if (raw == null) {
            return Optional.empty();
        }
        int separator = raw.indexOf('|');
        if (separator < 0) {
            return Optional.empty();
        }
        return Optional.of(new OtpChallenge(raw.substring(0, separator), raw.substring(separator + 1)));
    }

    public long incrementAttempts(String challengeId) {
        String key = KEY_PREFIX + challengeId + ATTEMPTS_SUFFIX;
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redis.expire(key, Duration.ofMinutes(10));
        }
        return count == null ? 0L : count;
    }

    /**
     * Cambia el código de un challenge vivo y devuelve el email al que hay que mandarlo.
     *
     * <p>Solo se guarda el hash del código, así que reenviar es generar un código nuevo, no repetir
     * el anterior: el que ya tenía el usuario deja de valer, igual que en cualquier servicio que
     * reenvía códigos. Lo que no puede hacer el reenvío es alargar el challenge, así que se
     * conserva el TTL que le queda en lugar de devolverle los cinco minutos. Si no lo hiciera,
     * reenviar sería la forma de estirar la vida de un código indefinidamente.
     */
    public Optional<String> recode(String challengeId, String codeHash) {
        String key = KEY_PREFIX + challengeId;
        String raw = redis.opsForValue().get(key);
        if (raw == null) {
            return Optional.empty();
        }
        int separator = raw.indexOf('|');
        if (separator < 0) {
            return Optional.empty();
        }
        Long ttlMillis = redis.getExpire(key, TimeUnit.MILLISECONDS);
        if (ttlMillis == null || ttlMillis <= 0) {
            // Caduca ahora o ya ha caducado: mandar un código que muere nada más llegar es peor que
            // pedirle al usuario que empiece el login otra vez.
            return Optional.empty();
        }
        String email = raw.substring(0, separator);
        redis.opsForValue().set(key, email + "|" + codeHash, Duration.ofMillis(ttlMillis));
        return Optional.of(email);
    }

    /**
     * Reclama el derecho a reenviar durante el enfriamiento. Vacío si este reenvío puede pasar, y
     * los segundos que quedan si hace poco que se reenvió.
     */
    public OptionalLong claimResendWindow(String challengeId, Duration cooldown) {
        String key = KEY_PREFIX + challengeId + RESEND_SUFFIX;
        if (Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, "1", cooldown))) {
            return OptionalLong.empty();
        }
        Long restante = redis.getExpire(key, TimeUnit.SECONDS);
        return OptionalLong.of(restante == null || restante < 0 ? cooldown.toSeconds() : restante);
    }

    public void delete(String challengeId) {
        redis.delete(KEY_PREFIX + challengeId);
        redis.delete(KEY_PREFIX + challengeId + ATTEMPTS_SUFFIX);
        redis.delete(KEY_PREFIX + challengeId + RESEND_SUFFIX);
    }

    public record OtpChallenge(String email, String codeHash) {
    }
}