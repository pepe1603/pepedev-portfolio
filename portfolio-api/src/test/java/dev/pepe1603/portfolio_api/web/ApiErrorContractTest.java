package dev.pepe1603.portfolio_api.web;

import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.pepe1603.portfolio_api.exception.ApiExceptionHandler;
import dev.pepe1603.portfolio_api.config.StorageProperties;
import dev.pepe1603.portfolio_api.controller.AdminCertificateController;
import dev.pepe1603.portfolio_api.controller.AdminStorageController;
import dev.pepe1603.portfolio_api.controller.AuthController;
import dev.pepe1603.portfolio_api.controller.ContactController;
import dev.pepe1603.portfolio_api.service.AdminCertificateService;
import dev.pepe1603.portfolio_api.service.AuditService;
import dev.pepe1603.portfolio_api.service.ContactService;
import dev.pepe1603.portfolio_api.service.StorageService;
import dev.pepe1603.portfolio_api.security.ContactRateLimiter;
import dev.pepe1603.portfolio_api.security.JwtProperties;
import dev.pepe1603.portfolio_api.security.JwtTokenService;
import dev.pepe1603.portfolio_api.security.LoginRateLimiter;
import dev.pepe1603.portfolio_api.security.RateLimitProperties;
import dev.pepe1603.portfolio_api.security.TokenBlacklist;
import dev.pepe1603.portfolio_api.repository.AuthSessionRepository;
import dev.pepe1603.portfolio_api.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

@WebMvcTest(controllers = {AuthController.class, ContactController.class, AdminStorageController.class,
        AdminCertificateController.class})
@AutoConfigureMockMvc(addFilters = false)
@Import({ApiExceptionHandler.class, StorageProperties.class})
@TestPropertySource(properties = {
        "APP_STORAGE_DIR=target/test-uploads",
        "APP_STORAGE_PUBLIC_URL=http://localhost:8080/files"})
class ApiErrorContractTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthenticationManager authenticationManager;
    @MockitoBean
    private UserRepository userRepository;
    @MockitoBean
    private AuthSessionRepository authSessionRepository;
    @MockitoBean
    private JwtTokenService jwtTokenService;
    @MockitoBean
    private JwtProperties jwtProperties;
    @MockitoBean
    private RateLimitProperties rateLimitProperties;
    @MockitoBean
    private LoginRateLimiter loginRateLimiter;
    @MockitoBean
    private TokenBlacklist tokenBlacklist;
    @MockitoBean
    private ContactService contactService;
    @MockitoBean
    private ContactRateLimiter contactRateLimiter;
    @MockitoBean
    private StorageService storageService;
    @MockitoBean
    private AdminCertificateService adminCertificateService;
    @MockitoBean
    private AuditService auditService;

    private static final ResultMatcher NO_LEGACY_FIELDS = result -> {
        for (String legacy : new String[] {"trace", "timestamp", "path", "type"}) {
            jsonPath("$." + legacy).doesNotExist().match(result);
        }
    };

    @Test
    void loginSinCampos_400ConErrors() throws Exception {
        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpectAll(
                        jsonPath("$.title").value("Bad Request"),
                        jsonPath("$.status").value(400),
                        jsonPath("$.detail").value("Validación fallida: se encontraron 2 errores"),
                        jsonPath("$.instance").value("/auth/login"),
                        jsonPath("$.errors.length()").value(2),
                        jsonPath("$..errors[?(@.field=='email')].message").value(hasItem("El email es obligatorio")),
                        jsonPath("$..errors[?(@.field=='password')].message")
                                .value(hasItem("La contraseña es obligatoria")),
                        NO_LEGACY_FIELDS);
    }

    @Test
    void loginCredencialesInvalidas_401() throws Exception {
        given(authenticationManager.authenticate(any())).willThrow(new BadCredentialsException("bad"));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@b.es\",\"password\":\"secreta\"}"))
                .andExpect(status().isUnauthorized())
                .andExpectAll(
                        jsonPath("$.title").value("Unauthorized"),
                        jsonPath("$.status").value(401),
                        jsonPath("$.detail").value("Credenciales inválidas"),
                        jsonPath("$.instance").value("/auth/login"),
                        NO_LEGACY_FIELDS);
        verify(loginRateLimiter).recordFailure(anyString(), anyString());
    }

    @Test
    void loginRateLimit_429ConRetryAfter() throws Exception {
        given(loginRateLimiter.isBlocked(anyString(), anyString())).willReturn(true);
        given(rateLimitProperties.getWindowSeconds()).willReturn(900L);

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@b.es\",\"password\":\"secreta\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpectAll(
                        header().string("Retry-After", "900"),
                        jsonPath("$.status").value(429),
                        jsonPath("$.detail").value("Demasiados intentos de login. Inténtalo de nuevo más tarde"),
                        jsonPath("$.instance").value("/auth/login"),
                        NO_LEGACY_FIELDS);
    }

    @Test
    void refreshSinCookie_401ResponseStatusException() throws Exception {
        mockMvc.perform(post("/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpectAll(
                        jsonPath("$.title").value("Unauthorized"),
                        jsonPath("$.status").value(401),
                        jsonPath("$.detail").value("Falta la cookie de refresh"),
                        jsonPath("$.instance").value("/auth/refresh"),
                        NO_LEGACY_FIELDS);
    }

    @Test
    void contactoVacio_400ConErrors() throws Exception {
        mockMvc.perform(post("/contact").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpectAll(
                        jsonPath("$.title").value("Bad Request"),
                        jsonPath("$.status").value(400),
                        jsonPath("$.instance").value("/contact"),
                        jsonPath("$.errors.length()").value(4),
                        jsonPath("$..errors[?(@.field=='body')].message").value(hasItem("El mensaje es obligatorio")),
                        NO_LEGACY_FIELDS);
    }

    @Test
    void contactoRateLimit_429ConRetryAfter() throws Exception {
        given(contactRateLimiter.isBlocked(anyString())).willReturn(true);
        given(contactRateLimiter.getWindowSeconds()).willReturn(900L);

        mockMvc.perform(post("/contact")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Pepe\",\"email\":\"a@b.es\",\"subject\":\"Hola\"," +
                                "\"body\":\"Mensaje de prueba\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpectAll(
                        header().string("Retry-After", "900"),
                        jsonPath("$.status").value(429),
                        jsonPath("$.detail").value("Demasiados envíos de contacto. Inténtalo de nuevo más tarde"),
                        jsonPath("$.instance").value("/contact"),
                        NO_LEGACY_FIELDS);
    }

    @Test
    void contactoHoneypotNoPersiste() throws Exception {
        mockMvc.perform(post("/contact")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Pepe\",\"email\":\"a@b.es\",\"subject\":\"Hola\"," +
                                "\"body\":\"Mensaje de prueba\",\"website\":\"http://spam.com\"}"))
                .andExpect(status().isCreated());

        verify(contactRateLimiter, never()).isBlocked(anyString());
        verify(contactService, never()).save(any(), anyString(), anyString(), any());
    }

    @Test
    void storageNoMultipart_400() throws Exception {
        mockMvc.perform(post("/admin/storage").param("use", "avatar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"use\":\"avatar\"}"))
                .andExpect(status().isBadRequest())
                .andExpectAll(
                        jsonPath("$.title").value("Bad Request"),
                        jsonPath("$.status").value(400),
                        jsonPath("$.detail").value("Petición no codificada como multipart/form-data"),
                        jsonPath("$.instance").value("/admin/storage"),
                        NO_LEGACY_FIELDS);
    }

    @Test
    void storageSinParteFile_400() throws Exception {
        mockMvc.perform(multipart("/admin/storage").param("use", "avatar"))
                .andExpect(status().isBadRequest())
                .andExpectAll(
                        jsonPath("$.status").value(400),
                        jsonPath("$.detail").value("Required part 'file' is not present."),
                        jsonPath("$.instance").value("/admin/storage"),
                        NO_LEGACY_FIELDS);
    }

    @Test
    void storageUseInvalido_400ConDetalle() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "f.png", MediaType.IMAGE_PNG_VALUE, new byte[8]);

        mockMvc.perform(multipart("/admin/storage").file(file).param("use", "oof"))
                .andExpect(status().isBadRequest())
                .andExpectAll(
                        jsonPath("$.status").value(400),
                        jsonPath("$.detail").value("Uso no permitido. Valores válidos: avatar, thumbnail, gallery, image, cv"),
                        jsonPath("$.instance").value("/admin/storage"),
                        NO_LEGACY_FIELDS);
        verify(storageService, never()).store(any(), any());
    }

    @Test
    void storageTipoFicheroNoPermitido_415() throws Exception {
        given(storageService.store(any(), any())).willThrow(
                new ResponseStatusException(org.springframework.http.HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                        "Tipo de fichero no permitido para AVATAR"));
        MockMultipartFile file = new MockMultipartFile("file", "f.png", MediaType.IMAGE_PNG_VALUE, new byte[8]);

        mockMvc.perform(multipart("/admin/storage").file(file).param("use", "avatar"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpectAll(
                        jsonPath("$.status").value(415),
                        jsonPath("$.detail").value("Tipo de fichero no permitido para AVATAR"),
                        jsonPath("$.instance").value("/admin/storage"),
                        NO_LEGACY_FIELDS);
    }

    @Test
    void storageUseDuplicado_400() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "f.png", MediaType.IMAGE_PNG_VALUE, new byte[8]);

        mockMvc.perform(multipart("/admin/storage")
                        .file(file)
                        .param("use", "avatar")
                        .queryParam("use", "avatar"))
                .andExpect(status().isBadRequest())
                .andExpectAll(
                        jsonPath("$.status").value(400),
                        jsonPath("$.detail").value("Uso no permitido. Valores válidos: avatar, thumbnail, gallery, image, cv"),
                        jsonPath("$.instance").value("/admin/storage"),
                        NO_LEGACY_FIELDS);
        verify(storageService, never()).store(any(), any());
    }

    @Test
    void storageFicheroDemasiadoGrande_413() throws Exception {
        given(storageService.store(any(), any())).willThrow(
                new MaxUploadSizeExceededException(15L * 1024 * 1024));
        MockMultipartFile file = new MockMultipartFile("file", "big.png", MediaType.IMAGE_PNG_VALUE, new byte[64]);

        mockMvc.perform(multipart("/admin/storage").file(file).param("use", "avatar"))
                .andExpect(status().isPayloadTooLarge())
                .andExpectAll(
                        jsonPath("$.title").value("Content Too Large"),
                        jsonPath("$.status").value(413),
                        jsonPath("$.detail").value("El fichero supera el tamaño máximo permitido"),
                        jsonPath("$.instance").value("/admin/storage"),
                        NO_LEGACY_FIELDS);
    }

    @Test
    void recursoInexistente_404NoResource() throws Exception {
        mockMvc.perform(get("/ruta/inexistente"))
                .andExpect(status().isNotFound())
                .andExpectAll(
                        jsonPath("$.title").value("Not Found"),
                        jsonPath("$.status").value(404),
                        jsonPath("$.instance").value("/ruta/inexistente"),
                        NO_LEGACY_FIELDS);
    }

    @Test
    void negocioInexistente_404Detalle() throws Exception {
        given(adminCertificateService.getById(any())).willThrow(
                new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Certificado no encontrado"));

        mockMvc.perform(get("/admin/certificates/00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound())
                .andExpectAll(
                        jsonPath("$.title").value("Not Found"),
                        jsonPath("$.status").value(404),
                        jsonPath("$.detail").value("Certificado no encontrado"),
                        jsonPath("$.instance").value("/admin/certificates/00000000-0000-0000-0000-000000000000"),
                        NO_LEGACY_FIELDS);
    }
}