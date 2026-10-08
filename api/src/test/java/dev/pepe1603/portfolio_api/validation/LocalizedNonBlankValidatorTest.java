package dev.pepe1603.portfolio_api.validation;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LocalizedNonBlankValidatorTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    static class Bean {
        @LocalizedNonBlank
        public Map<String, String> title;
    }

    private boolean isValid(Map<String, String> title) {
        Bean bean = new Bean();
        bean.title = title;
        return validator.validate(bean).isEmpty();
    }

    @Test
    void nullOVacioSeRechaza() {
        assertThat(isValid(null)).isFalse();
        assertThat(isValid(Map.of())).isFalse();
    }

    @Test
    void ambosIdiomasEnBlancoSeRechazan() {
        assertThat(isValid(Map.of("es", "", "en", "   "))).isFalse();
    }

    @Test
    void unIdiomaConContenidoEsValido() {
        assertThat(isValid(Map.of("es", "Hola"))).isTrue();
        assertThat(isValid(Map.of("en", "Hello"))).isTrue();
        assertThat(isValid(Map.of("es", "Hola", "en", "Hello"))).isTrue();
        assertThat(isValid(Map.of("es", "", "en", "Hello"))).isTrue();
        assertThat(isValid(Map.of("es", "Hola", "en", ""))).isTrue();
    }
}