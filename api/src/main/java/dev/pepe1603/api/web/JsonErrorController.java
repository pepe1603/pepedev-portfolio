package dev.pepe1603.api.web;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class JsonErrorController implements ErrorController {

    @RequestMapping("/error")
    @ResponseBody
    public ResponseEntity<ProblemDetail> handleError(HttpServletRequest request, HttpServletResponse response) {
        if (response.isCommitted()) {
            return null;
        }
        HttpStatus status = resolveStatus(request);
        String detail = switch (status.value()) {
            case 400 -> "Petición inválida";
            case 404 -> "Recurso no encontrado";
            case 405 -> "Método no permitido";
            case 413 -> "El fichero supera el tamaño máximo permitido";
            case 429 -> "Demasiadas peticiones. Inténtalo de nuevo más tarde";
            default -> "Error interno del servidor";
        };
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setInstance(URI.create(resolveRequestUri(request)));
        return ResponseEntity.status(status).body(problem);
    }

    private static HttpStatus resolveStatus(HttpServletRequest request) {
        Integer statusCode = (Integer) request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        HttpStatus status = statusCode == null ? null : HttpStatus.resolve(statusCode);
        return status != null ? status : HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private static String resolveRequestUri(HttpServletRequest request) {
        String requestUri = (String) request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        return requestUri != null && !requestUri.isBlank() ? requestUri : request.getRequestURI();
    }
}