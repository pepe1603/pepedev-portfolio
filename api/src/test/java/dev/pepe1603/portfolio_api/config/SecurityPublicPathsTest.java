package dev.pepe1603.portfolio_api.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * La cadena de seguridad no se ejercita en los tests de web (van con
 * {@code addFilters = false}), así que un endpoint nuevo que se olvide de añadir a
 * {@code PUBLIC_PATHS} se compila, pasa todos los tests y en producción devuelve 401 a un usuario
 * que ya ha metido bien su contraseña. Esta es la red que lo caza.
 */
class SecurityPublicPathsTest {

    private static final List<String> RUTAS_PUBLICAS_ESPERADAS = List.of(
            "/auth/login",
            "/auth/login/verify",
            "/auth/login/resend",
            "/auth/refresh",
            "/auth/logout",
            "/auth/reset/request",
            "/auth/reset/confirm",
            "/contact");

    @Test
    void lasRutasPublicasDelLoginYDelResetEstanDeclaradas() {
        String[] declaradas = (String[]) ReflectionTestUtils.getField(SecurityConfig.class, "PUBLIC_PATHS");

        assertThat(declaradas).isNotNull();
        assertThat(declaradas).containsAll(RUTAS_PUBLICAS_ESPERADAS);
    }

    @Test
    void lasRutasQueRequierenTokenNoSeColanPorError() {
        String[] declaradas = (String[]) ReflectionTestUtils.getField(SecurityConfig.class, "PUBLIC_PATHS");

        // Ni /auth/me ni /admin/** pueden ser públicas: /auth/** que no esté en la lista de arriba
        // es un endpoint de sesión y necesita el token.
        assertThat(declaradas).noneMatch(path -> path.equals("/auth/me") || path.startsWith("/admin"));
    }

    @Test
    void noHayRutasPublicasDuplicadas() {
        String[] declaradas = (String[]) ReflectionTestUtils.getField(SecurityConfig.class, "PUBLIC_PATHS");

        assertThat(Arrays.stream(declaradas).distinct().count()).isEqualTo(declaradas.length);
    }
}
