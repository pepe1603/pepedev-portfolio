package dev.pepe1603.portfolio_api.security;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;

class JwtAuthEntryPointTest {

    private final SecurityErrorWriter errorWriter = mock(SecurityErrorWriter.class);

    @Test
    void escribe401ConUriDelRequest() throws Exception {
        JwtAuthEntryPoint entryPoint = new JwtAuthEntryPoint(errorWriter);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/certificates");
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(request, response, new AuthenticationCredentialsNotFoundException("sin credenciales"));

        verify(errorWriter).write(response, HttpStatus.UNAUTHORIZED, "Autenticación requerida", "/admin/certificates");
    }
}