package dev.pepe1603.portfolio_api.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.pepe1603.portfolio_api.entity.User;
import dev.pepe1603.portfolio_api.enums.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class JwtTokenServiceTest {

    private static final String SECRET_64_BYTES = Base64.getEncoder().encodeToString(new byte[64]);
    private static final String OTHER_64_BYTES =
            Base64.getEncoder().encodeToString(java.util.Arrays.copyOf(new byte[] {0x01}, 64));

    private JwtProperties properties;
    private JwtTokenService service;

    @BeforeEach
    void setUp() {
        properties = new JwtProperties();
        ReflectionTestUtils.setField(properties, "accessSecret", SECRET_64_BYTES);
        ReflectionTestUtils.setField(properties, "refreshSecret", SECRET_64_BYTES);
        ReflectionTestUtils.setField(properties, "accessTtlSeconds", 900L);
        ReflectionTestUtils.setField(properties, "refreshTtlSeconds", 604800L);
        service = new JwtTokenService(properties);
    }

    private User adminUser() {
        User user = new User();
        user.setEmail("admin@pepe.dev");
        user.setRole(UserRole.ADMIN);
        return user;
    }

    private static JwtProperties buildProperties(long accessTtlSeconds) {
        JwtProperties props = new JwtProperties();
        ReflectionTestUtils.setField(props, "accessSecret", OTHER_64_BYTES);
        ReflectionTestUtils.setField(props, "refreshSecret", OTHER_64_BYTES);
        ReflectionTestUtils.setField(props, "accessTtlSeconds", accessTtlSeconds);
        ReflectionTestUtils.setField(props, "refreshTtlSeconds", 604800L);
        return props;
    }

    @Test
    void emiteParAccessRefreshConClaimsCorrectos() {
        JwtTokenService.TokenPair pair = service.issueTokenPair(adminUser());

        Claims access = service.parseAccessToken(pair.accessToken());
        assertThat(access.getSubject()).isEqualTo("admin@pepe.dev");
        assertThat(access.get("role", String.class)).isEqualTo("ADMIN");
        assertThat(access.get("typ", String.class)).isEqualTo("access");
        assertThat(access.getId()).isNotBlank();
        assertThat(access.getExpiration().getTime() - access.getIssuedAt().getTime()).isEqualTo(900_000L);

        Claims refresh = service.parseRefreshToken(pair.refreshToken());
        assertThat(refresh.getSubject()).isEqualTo("admin@pepe.dev");
        assertThat(refresh.get("typ", String.class)).isEqualTo("refresh");
        assertThat(refresh.getExpiration().getTime() - refresh.getIssuedAt().getTime()).isEqualTo(604_800_000L);
    }

    @Test
    void accessNoPasaComoRefreshNiViceversa() {
        JwtTokenService.TokenPair pair = service.issueTokenPair(adminUser());

        assertThatThrownBy(() -> service.parseRefreshToken(pair.accessToken()))
                .isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> service.parseAccessToken(pair.refreshToken()))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void tokenFirmadoConOtraClaveEsRechazado() {
        JwtTokenService other = new JwtTokenService(buildProperties(900L));
        JwtTokenService.TokenPair pair = other.issueTokenPair(adminUser());

        assertThatThrownBy(() -> service.parseAccessToken(pair.accessToken()))
                .isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> service.parseRefreshToken(pair.refreshToken()))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void tokenManipuladoSeRechazaPorFirma() {
        JwtTokenService.TokenPair pair = service.issueTokenPair(adminUser());
        String[] parts = pair.accessToken().split("\\.");
        char flipped = parts[2].charAt(0) == 'A' ? 'B' : 'A';
        String tampered = parts[0] + "." + parts[1] + "." + flipped + parts[2].substring(1);

        assertThatThrownBy(() -> service.parseAccessToken(tampered))
                .isInstanceOf(JwtException.class)
                .isInstanceOf(io.jsonwebtoken.security.SignatureException.class);
    }

    @Test
    void tokenCaducadoSeRechaza() {
        JwtTokenService expired = new JwtTokenService(buildProperties(-1L));
        JwtTokenService.TokenPair pair = expired.issueTokenPair(adminUser());

        assertThatThrownBy(() -> expired.parseAccessToken(pair.accessToken()))
                .isInstanceOf(ExpiredJwtException.class)
                .isInstanceOf(JwtException.class);
    }

    @Test
    void secretoAusenteLanzaErrorDeConfiguracion() {
        ReflectionTestUtils.setField(properties, "accessSecret", "");

        assertThatThrownBy(() -> new JwtTokenService(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_JWT_SECRET");
    }

    @Test
    void secretoDemasiadoCortoLanzaErrorDeConfiguracion() {
        ReflectionTestUtils.setField(properties, "accessSecret",
                Base64.getEncoder().encodeToString(new byte[16]));

        assertThatThrownBy(() -> new JwtTokenService(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("al menos 32 bytes");
    }

    @Test
    void secretoNoBase64LanzaErrorDeConfiguracion() {
        ReflectionTestUtils.setField(properties, "accessSecret", "!!!no-base64!!!");

        assertThatThrownBy(() -> new JwtTokenService(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Base64");
    }
}