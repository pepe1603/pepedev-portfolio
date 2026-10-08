package dev.pepe1603.portfolio_api.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.pepe1603.portfolio_api.config.StorageProperties;
import dev.pepe1603.portfolio_api.security.JwtTokenService;
import dev.pepe1603.portfolio_api.security.TokenBlacklist;
import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;

@WebMvcTest(controllers = JsonErrorController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(StorageProperties.class)
@TestPropertySource(properties = {
        "APP_STORAGE_PUBLIC_URL=http://localhost:8080/files"})
class JsonErrorControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtTokenService jwtTokenService;
    @MockitoBean
    private TokenBlacklist tokenBlacklist;

    private static final ResultMatcher NO_TYPE_FIELD = result ->
            jsonPath("$.type").doesNotExist().match(result);

    @Test
    void error404_SirveProblemDetailJson() throws Exception {
        mockMvc.perform(get("/error")
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 404)
                        .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/public/nope"))
                .andExpect(status().isNotFound())
                .andExpectAll(
                        jsonPath("$.title").value("Not Found"),
                        jsonPath("$.status").value(404),
                        jsonPath("$.detail").value("Recurso no encontrado"),
                        jsonPath("$.instance").value("/public/nope"),
                        NO_TYPE_FIELD);
    }

    @Test
    void error413_SirveProblemDetailJson() throws Exception {
        mockMvc.perform(get("/error")
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 413)
                        .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/admin/storage"))
                .andExpect(status().isPayloadTooLarge())
                .andExpectAll(
                        jsonPath("$.title").value("Content Too Large"),
                        jsonPath("$.status").value(413),
                        jsonPath("$.detail").value("El fichero supera el tamaño máximo permitido"),
                        jsonPath("$.instance").value("/admin/storage"),
                        NO_TYPE_FIELD);
    }

    @Test
    void errorInterno_SirveProblemDetailJson() throws Exception {
        mockMvc.perform(get("/error")
                        .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/algo"))
                .andExpect(status().isInternalServerError())
                .andExpectAll(
                        jsonPath("$.title").value("Internal Server Error"),
                        jsonPath("$.status").value(500),
                        jsonPath("$.detail").value("Error interno del servidor"),
                        jsonPath("$.instance").value("/algo"),
                        NO_TYPE_FIELD);
    }

    @Test
    void error404DeRutaInexistente_TituloNotFoundsinLegacy() throws Exception {
        mockMvc.perform(get("/ruta/que/no/existe"))
                .andExpect(status().isNotFound())
                .andExpectAll(
                        jsonPath("$.title").value("Not Found"),
                        jsonPath("$.status").value(404),
                        jsonPath("$.instance").value("/ruta/que/no/existe"),
                        NO_TYPE_FIELD);
    }
}