package dev.pepe1603.portfolio_api.security;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;

class JwtAccessDeniedHandlerTest {

    private final SecurityErrorWriter errorWriter = mock(SecurityErrorWriter.class);

    @Test
    void escribe403ConUriDelRequest() throws Exception {
        JwtAccessDeniedHandler handler = new JwtAccessDeniedHandler(errorWriter);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/projects");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(request, response, new AccessDeniedException("sin rol"));

        verify(errorWriter).write(response, HttpStatus.FORBIDDEN,
                "No tienes permiso para acceder a este recurso", "/admin/projects");
    }
}