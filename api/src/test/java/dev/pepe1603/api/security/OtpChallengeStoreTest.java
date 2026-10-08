package dev.pepe1603.api.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.TimeUnit;
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

    @Test
    void recodeCambiaElHashYConservaElTtlQueQueda() {
        given(ops.get("auth:otp:c1")).willReturn("admin@pepe.dev|hashViejo");
        given(redis.getExpire("auth:otp:c1", TimeUnit.MILLISECONDS)).willReturn(120_000L);

        Optional<String> email = store.recode("c1", "hashNuevo");

        assertThat(email).contains("admin@pepe.dev");
        // 2 minutos, no los 5 de siempre: si el reenvío devolviera el TTL completo, reenviar sería
        // una forma de estirar la vida del código indefinidamente.
        verify(ops).set("auth:otp:c1", "admin@pepe.dev|hashNuevo", Duration.ofMillis(120_000));
    }

    @Test
    void recodeDeUnChallengeQueCaducaAhoraNoMandaNada() {
        given(ops.get("auth:otp:c1")).willReturn("admin@pepe.dev|hashViejo");
        given(redis.getExpire("auth:otp:c1", TimeUnit.MILLISECONDS)).willReturn(0L);

        // Un código que muere nada más llegar es peor que no mandar nada.
        assertThat(store.recode("c1", "hashNuevo")).isEmpty();
        verify(ops, never()).set(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(Duration.class));
    }

    @Test
    void claimResendWindowDejaPasarElPrimeroYBloqueaElSiguiente() {
        given(ops.setIfAbsent("auth:otp:c1:resend", "1", Duration.ofSeconds(30))).willReturn(true, false);
        given(redis.getExpire("auth:otp:c1:resend", TimeUnit.SECONDS)).willReturn(17L);

        assertThat(store.claimResendWindow("c1", Duration.ofSeconds(30))).isEmpty();
        assertThat(store.claimResendWindow("c1", Duration.ofSeconds(30))).hasValue(17L);
    }

    @Test
    void deleteTambienLimpiaElEnfriamientoDelReenvio() {
        store.delete("c1");

        verify(redis).delete("auth:otp:c1:resend");
    }
}
