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

class ResetRateLimiterTest {

    private StringRedisTemplate redis;
    private ValueOperations<String, String> ops;

    private ResetRateLimiter limiter;

    @BeforeEach
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        ops = mock(ValueOperations.class);
        given(redis.opsForValue()).willReturn(ops);

        RateLimitProperties properties = new RateLimitProperties();
        ReflectionTestUtils.setField(properties, "resetMaxAttemptsPerIp", 5);
        ReflectionTestUtils.setField(properties, "resetWindowSeconds", 900L);
        limiter = new ResetRateLimiter(redis, properties);
    }

    @Test
    void sinContadoresNoBloquea() {
        assertThat(limiter.isBlocked("1.2.3.4")).isFalse();
    }

    @Test
    void bloqueaAlAlcanzarElMaximoPorIp() {
        given(ops.get("rl:reset:ip:1.2.3.4")).willReturn("5");

        assertThat(limiter.isBlocked("1.2.3.4")).isTrue();
        assertThat(limiter.isBlocked("1.2.3.5")).isFalse();
    }

    @Test
    void porDebajoDelUmbralNoBloquea() {
        given(ops.get("rl:reset:ip:1.2.3.4")).willReturn("4");

        assertThat(limiter.isBlocked("1.2.3.4")).isFalse();
    }

    @Test
    void recordIncrementaYExpiraLaPrimeraVez() {
        given(ops.increment("rl:reset:ip:1.2.3.4")).willReturn(1L);

        limiter.record("1.2.3.4");

        verify(redis).expire("rl:reset:ip:1.2.3.4", 900L, TimeUnit.SECONDS);
    }

    @Test
    void onSuccessBorraElContador() {
        limiter.onSuccess("1.2.3.4");

        verify(redis).delete("rl:reset:ip:1.2.3.4");
        verifyNoMoreInteractions(redis);
    }
}