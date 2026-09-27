package dev.pepe1603.portfolio_api.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class OtpChallengeStoreTest {

    private StringRedisTemplate redis;
    private ValueOperations<String, String> ops;

    private OtpChallengeStore store;

    @BeforeEach
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        ops = mock(ValueOperations.class);
        given(redis.opsForValue()).willReturn(ops);
        store = new OtpChallengeStore(redis);
    }

    @Test
    void createGuardaEmailYHashConElTtlIndicado() {
        String challengeId = store.create("admin@pepe.dev", "hash123", Duration.ofMinutes(5));

        verify(ops).set("auth:otp:" + challengeId, "admin@pepe.dev|hash123", Duration.ofMinutes(5));
    }

    @Test
    void peekDevuelveEmailYHashAlSepararElPipe() {
        given(ops.get("auth:otp:c1")).willReturn("admin@pepe.dev|hash123");

        assertThat(store.peek("c1")).contains(new OtpChallengeStore.OtpChallenge("admin@pepe.dev", "hash123"));
    }

    @Test
    void peekDeChallengeCaducadoDevuelveVacio() {
        given(ops.get("auth:otp:c1")).willReturn(null);

        assertThat(store.peek("c1")).isEmpty();
    }

    @Test
    void peekDeValorSinSeparadorDevuelveVacio() {
        given(ops.get("auth:otp:c1")).willReturn("sin-separador");

        assertThat(store.peek("c1")).isEmpty();
    }

    @Test
    void incrementAttemptsSumaYExpiraLaPrimeraVez() {
        given(ops.increment("auth:otp:c1:attempts")).willReturn(1L);

        assertThat(store.incrementAttempts("c1")).isEqualTo(1L);

        verify(redis).expire("auth:otp:c1:attempts", Duration.ofMinutes(10));
    }

    @Test
    void deleteBorraElChallengeYElContadorDeIntentos() {
        store.delete("c1");

        verify(redis).delete("auth:otp:c1");
        verify(redis).delete("auth:otp:c1:attempts");
    }
}