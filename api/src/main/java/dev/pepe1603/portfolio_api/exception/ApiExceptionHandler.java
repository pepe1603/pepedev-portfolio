package dev.pepe1603.portfolio_api.exception;

import dev.pepe1603.portfolio_api.exception.ContactRateLimitedException;
import dev.pepe1603.portfolio_api.exception.LoginRateLimitedException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    protected ResponseEntity<Object> handleResponseStatus(ResponseStatusException ex, WebRequest request) {
        return handleExceptionInternal(ex, null, ex.getHeaders(), ex.getStatusCode(), request);
    }

    @ExceptionHandler(MultipartException.class)
    protected ResponseEntity<Object> handleMultipart(MultipartException ex, WebRequest request) {
        ProblemDetail body = createProblemDetail(ex, HttpStatus.BAD_REQUEST,
                "Petición no codificada como multipart/form-data", null, null, request);
        return handleExceptionInternal(ex, body, new HttpHeaders(), HttpStatus.BAD_REQUEST, request);
    }

    @Override
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail body = createProblemDetail(ex, status,
                "El fichero supera el tamaño máximo permitido", null, null, request);
        return handleExceptionInternal(ex, body, headers, status, request);
    }

    @ExceptionHandler(BadCredentialsException.class)
    protected ResponseEntity<Object> handleBadCredentials(BadCredentialsException ex, WebRequest request) {
        ProblemDetail body = createProblemDetail(ex, HttpStatus.UNAUTHORIZED,
                "Credenciales inválidas", null, null, request);
        return handleExceptionInternal(ex, body, new HttpHeaders(), HttpStatus.UNAUTHORIZED, request);
    }

    @ExceptionHandler({LoginRateLimitedException.class, ContactRateLimitedException.class,
            OtpResendTooSoonException.class})
    protected ResponseEntity<Object> handleRateLimited(RuntimeException ex, WebRequest request) {
        RateLimitedException limited = (RateLimitedException) ex;
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, String.valueOf(limited.getRetryAfterSeconds()));
        ProblemDetail body = createProblemDetail(ex, HttpStatus.TOO_MANY_REQUESTS, limited.getUserMessage(), null,
                null, request);
        return handleExceptionInternal(ex, body, headers, HttpStatus.TOO_MANY_REQUESTS, request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<ErrorField> errors = new ArrayList<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.add(new ErrorField(fieldError.getField(), fieldError.getDefaultMessage()));
        }
        for (ObjectError objectError : ex.getBindingResult().getGlobalErrors()) {
            errors.add(new ErrorField(null, objectError.getDefaultMessage()));
        }
        ProblemDetail body = createProblemDetail(ex, status,
                "Validación fallida: se encontraron " + errors.size() + " errores", null, null, request);
        body.setProperty("errors", errors);
        return handleExceptionInternal(ex, body, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> createResponseEntity(@Nullable Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        if (body instanceof ProblemDetail problemDetail && problemDetail.getInstance() == null
                && request instanceof ServletWebRequest servletWebRequest) {
            problemDetail.setInstance(URI.create(servletWebRequest.getRequest().getRequestURI()));
        }
        return super.createResponseEntity(body, headers, statusCode, request);
    }

    private record ErrorField(String field, String message) {
    }
}