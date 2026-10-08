package dev.pepe1603.api.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class ContactRateLimiterTest {

    private StringRedisTemplate redis;
    private ValueOperations<String, String> ops;

    private ContactRateLimiter limiter;

    @BeforeEach
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        ops = mock(ValueOperations.class);
        given(redis.opsForValue()).willReturn(ops);
        limiter = new ContactRateLimiter(redis, 5, 900);
    }

    @Test
    void sinContadorNoBloquea() {
        assertThat(limiter.isBlocked("1.2.3.4")).isFalse();
    }

    @Test
    void bloqueaAlAlcanzarElMaximo() {
        given(ops.get("rl:contact:ip:1.2.3.4")).willReturn("5");

        assertThat(limiter.isBlocked("1.2.3.4")).isTrue();
        assertThat(limiter.isBlocked("1.2.3.5")).isFalse();
    }

    @Test
    void porDebajoDelUmbralNoBloquea() {
        given(ops.get("rl:contact:ip:1.2.3.4")).willReturn("4");

        assertThat(limiter.isBlocked("1.2.3.4")).isFalse();
    }

    @Test
    void recordRequestIncrementaYExpiraSoloLaPrimeraVez() {
        String key = "rl:contact:ip:1.2.3.4";
        given(ops.increment(key)).willReturn(1L);

        limiter.recordRequest("1.2.3.4");
        given(ops.increment(key)).willReturn(2L);
        limiter.recordRequest("1.2.3.4");

        verify(redis, org.mockito.Mockito.times(1)).expire(key, 900L, TimeUnit.SECONDS);
    }

    @Test
    void exponeLaVentanaEnSegundos() {
        assertThat(limiter.getWindowSeconds()).isEqualTo(900L);
    }
}