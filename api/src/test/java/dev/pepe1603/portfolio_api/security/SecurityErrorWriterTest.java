package dev.pepe1603.portfolio_api.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class SecurityErrorWriterTest {

    private static ObjectMapper objectMapper;

    @BeforeAll
    static void setUp() {
        objectMapper = JsonMapper.builder()
                .changeDefaultPropertyInclusion(v -> v.withValueInclusion(JsonInclude.Include.NON_NULL))
                .build();
    }

    private final SecurityErrorWriter writer = new SecurityErrorWriter(objectMapper);

    @Test
    void escribeProblemDetailComoJson() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        writer.write(response, HttpStatus.UNAUTHORIZED, "Autenticación requerida", "/auth/me");

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).isEqualTo("application/json");
        assertThat(response.getContentLength()).isEqualTo(response.getContentAsByteArray().length);

        JsonNode body = objectMapper.readTree(response.getContentAsByteArray());
        assertThat(body.path("status").asInt()).isEqualTo(401);
        assertThat(body.path("title").asText()).isEqualTo("Unauthorized");
        assertThat(body.path("detail").asText()).isEqualTo("Autenticación requerida");
        assertThat(body.path("instance").asText()).isEqualTo("/auth/me");
    }

    @Test
    void soportaUTF8EnElDetalle() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        writer.write(response, HttpStatus.FORBIDDEN, "No tienes permiso para acceder a este recurso", "/admin/x");

        JsonNode body = objectMapper.readTree(response.getContentAsByteArray());
        assertThat(body.path("detail").asText()).isEqualTo("No tienes permiso para acceder a este recurso");
        String raw = new String(response.getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(raw).contains("No tienes permiso para acceder a este recurso");
    }

    @Test
    void omiteTypeCuandoEsAboutBlank() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        writer.write(response, HttpStatus.NOT_FOUND, "No existe", "/public/x");

        JsonNode body = objectMapper.readTree(response.getContentAsByteArray());
        assertThat(body.has("type")).isFalse();
        assertThat(body.has("trace")).isFalse();
        assertThat(body.has("timestamp")).isFalse();
        assertThat(body.has("path")).isFalse();
    }
}