package dev.pepe1603.portfolio_api.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class LocalizedTextTest {

    @Test
    void nullOVacioDevuelveNull() {
        assertThat(LocalizedText.resolve(null, "es")).isNull();
        assertThat(LocalizedText.resolve(Map.of(), "en")).isNull();
    }

    @Test
    void resuelveElIdiomaPedido() {
        assertThat(LocalizedText.resolve(Map.of("es", "Hola", "en", "Hello"), "es")).isEqualTo("Hola");
        assertThat(LocalizedText.resolve(Map.of("es", "Hola", "en", "Hello"), "en")).isEqualTo("Hello");
    }

    @Test
    void caeAlOtroIdiomaCuandoNoExisteElPedido() {
        assertThat(LocalizedText.resolve(Map.of("es", "Hola"), "en")).isEqualTo("Hola");
        assertThat(LocalizedText.resolve(Map.of("en", "Hello"), "es")).isEqualTo("Hello");
    }

    @Test
    void idiomaEsInsensibleAMayusculas() {
        assertThat(LocalizedText.resolve(Map.of("es", "Hola", "en", "Hello"), "EN")).isEqualTo("Hello");
        assertThat(LocalizedText.resolve(Map.of("en", "Hello"), "En")).isEqualTo("Hello");
    }

    @Test
    void cualquierIdiomaDesconocidoTrataComoEspanol() {
        assertThat(LocalizedText.normalizeLang(null)).isEqualTo("es");
        assertThat(LocalizedText.normalizeLang("")).isEqualTo("es");
        assertThat(LocalizedText.normalizeLang("fr")).isEqualTo("es");
        assertThat(LocalizedText.normalizeLang("es")).isEqualTo("es");
    }
}