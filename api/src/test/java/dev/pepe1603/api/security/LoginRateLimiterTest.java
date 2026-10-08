package dev.pepe1603.api.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

class LoginRateLimiterTest {

    private StringRedisTemplate redis;
    private ValueOperations<String, String> ops;

    private LoginRateLimiter limiter;

    @BeforeEach
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        ops = mock(ValueOperations.class);
        given(redis.opsForValue()).willReturn(ops);

        RateLimitProperties properties = new RateLimitProperties();
        ReflectionTestUtils.setField(properties, "maxAttemptsPerIp", 5);
        ReflectionTestUtils.setField(properties, "maxAttemptsPerEmail", 10);
        ReflectionTestUtils.setField(properties, "windowSeconds", 900L);
        limiter = new LoginRateLimiter(redis, properties);
    }

    @Test
    void sinContadoresNoBloquea() {
        assertThat(limiter.isBlocked("1.2.3.4", "a@b.es")).isFalse();
    }

    @Test
    void bloqueaAlAlcanzarElMaximoPorIp() {
        given(ops.get("rl:login:ip:1.2.3.4")).willReturn("5");

        assertThat(limiter.isBlocked("1.2.3.4", "a@b.es")).isTrue();
        assertThat(limiter.isBlocked("1.2.3.5", "a@b.es")).isFalse();
    }

    @Test
    void bloqueaDirectamentePorEmailIndependienteDeIp() {
        given(ops.get("rl:login:email:a@b.es")).willReturn("10");

        assertThat(limiter.isBlocked("9.9.9.9", "a@b.es")).isTrue();
    }

    @Test
    void porDebajoDelUmbralNoBloquea() {
        given(ops.get("rl:login:ip:1.2.3.4")).willReturn("4");
        given(ops.get("rl:login:email:a@b.es")).willReturn("9");

        assertThat(limiter.isBlocked("1.2.3.4", "a@b.es")).isFalse();
    }

    @Test
    void recordFailureIncrementaIpYEmailYExpiraLaPrimeraVez() {
        given(ops.increment("rl:login:ip:1.2.3.4")).willReturn(1L);
        given(ops.increment("rl:login:email:a@b.es")).willReturn(2L);

        limiter.recordFailure("1.2.3.4", "a@b.es");

        verify(redis).expire("rl:login:ip:1.2.3.4", 900L, TimeUnit.SECONDS);
    }

    @Test
    void onSuccessBorraContadoresDeIpYEmail() {
        limiter.onSuccess("1.2.3.4", "a@b.es");

        verify(redis).delete("rl:login:ip:1.2.3.4");
        verify(redis).delete("rl:login:email:a@b.es");
        verifyNoMoreInteractions(redis);
    }
}