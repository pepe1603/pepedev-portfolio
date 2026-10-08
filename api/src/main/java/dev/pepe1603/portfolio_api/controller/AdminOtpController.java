package dev.pepe1603.portfolio_api.controller;

import dev.pepe1603.portfolio_api.dto.admin.OtpEnabledRequest;
import dev.pepe1603.portfolio_api.dto.common.ApiProblemDetail;
import dev.pepe1603.portfolio_api.entity.User;
import dev.pepe1603.portfolio_api.enums.AuditAction;
import dev.pepe1603.portfolio_api.enums.AuditResource;
import dev.pepe1603.portfolio_api.repository.UserRepository;
import dev.pepe1603.portfolio_api.security.AccessTokenReader;
import dev.pepe1603.portfolio_api.service.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/admin/otp")
public class AdminOtpController {

    private final UserRepository userRepository;
    private final AuditService auditService;
    private final AccessTokenReader accessTokenReader;

    public AdminOtpController(UserRepository userRepository, AuditService auditService,
            AccessTokenReader accessTokenReader) {
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.accessTokenReader = accessTokenReader;
    }

    @PatchMapping
    @Operation(summary = "Activar o desactivar el segundo factor por email (OTP)",
            description = "Solo aplica si el feature flag global APP_AUTH_OTP_ENABLED está activo. "
                    + "Con el OTP activo, /auth/login responde 202 y exige /auth/login/verify.")
    @ApiResponse(responseCode = "204", description = "Preferencia actualizada")
    @ApiResponse(responseCode = "400", description = "Cuerpo inválido",
            content = @Content(schema = @Schema(implementation = ApiProblemDetail.class)))
    public ResponseEntity<Void> setEnabled(@Valid @RequestBody OtpEnabledRequest request,
            HttpServletRequest servletRequest) {
        User user = accessTokenReader.read(servletRequest)
                .map(claims -> claims.getSubject())
                .flatMap(userRepository::findByEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no encontrado"));
        user.setOtpEnabled(request.enabled());
        userRepository.save(user);
        auditService.record(AuditAction.UPDATE, AuditResource.USER, user.getId(), "otp-enabled:" + request.enabled());
        return ResponseEntity.noContent().build();
    }
}