package dev.pepe1603.portfolio_api.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class PasswordResetTokenStoreTest {

    private StringRedisTemplate redis;
    private ValueOperations<String, String> ops;

    private PasswordResetTokenStore store;

    @BeforeEach
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        ops = mock(ValueOperations.class);
        given(redis.opsForValue()).willReturn(ops);
        store = new PasswordResetTokenStore(redis);
    }

    @Test
    void createGuardaTokenA30MinutosVinculadoAlEmail() {
        String token = store.create("admin@pepe.dev", Duration.ofMinutes(30));

        assertThat(token).isNotBlank();
        verify(ops).set("pwreset:" + token, "admin@pepe.dev", Duration.ofMinutes(30));
    }

    @Test
    void cadaTokenEsDistinto() {
        assertThat(store.create("admin@pepe.dev", Duration.ofMinutes(30)))
                .isNotEqualTo(store.create("admin@pepe.dev", Duration.ofMinutes(30)));
    }

    @Test
    void consumeConEmailCoincidenteDevuelveEmailYBorraElToken() {
        given(ops.get("pwreset:abc123")).willReturn("admin@pepe.dev");

        Optional<String> result = store.consume("abc123", "admin@pepe.dev");

        assertThat(result).contains("admin@pepe.dev");
        verify(redis).delete("pwreset:abc123");
    }

    @Test
    void consumeConEmailDistintoNoBorraElToken() {
        given(ops.get("pwreset:abc123")).willReturn("admin@pepe.dev");

        assertThat(store.consume("abc123", "otro@pepe.dev")).isEmpty();
        verify(redis, never()).delete("pwreset:abc123");
    }

    @Test
    void consumeDeTokenInexistenteNoDevuelveNada() {
        given(ops.get("pwreset:zzz")).willReturn(null);

        assertThat(store.consume("zzz", "admin@pepe.dev")).isEmpty();
        verify(redis, never()).delete("pwreset:zzz");
    }
}