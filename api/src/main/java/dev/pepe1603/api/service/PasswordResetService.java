package dev.pepe1603.api.service;

import dev.pepe1603.api.entity.User;
import dev.pepe1603.api.repository.UserRepository;
import dev.pepe1603.api.security.PasswordResetTokenStore;
import dev.pepe1603.api.security.SecurityMailProperties;
import dev.pepe1603.api.util.LocalizedText;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

@Service
public class PasswordResetService {

    private static final Duration TOKEN_TTL = Duration.ofMinutes(30);

    private final PasswordResetTokenStore tokenStore;
    private final UserRepository userRepository;
    private final MailService mailService;
    private final MailTemplateRenderer templateRenderer;
    private final MessageSource messageSource;
    private final SecurityMailProperties mailProperties;
    private final String frontResetUrl;

    public PasswordResetService(PasswordResetTokenStore tokenStore, UserRepository userRepository,
            MailService mailService, MailTemplateRenderer templateRenderer, MessageSource messageSource,
            SecurityMailProperties mailProperties,
            @Value("${APP_FRONT_RESET_URL:}") String frontResetUrl) {
        this.tokenStore = tokenStore;
        this.userRepository = userRepository;
        this.mailService = mailService;
        this.templateRenderer = templateRenderer;
        this.messageSource = messageSource;
        this.mailProperties = mailProperties;
        this.frontResetUrl = frontResetUrl;
    }

    public void requestReset(String email, Locale locale) {
        // Con el interruptor apagado no se crea siquiera el token: así no quedan tokens de un solo
        // uso huérfanos en Redis esperando que alguien los consuma.
        if (!mailProperties.isResetEnabled() || !mailService.canSend()) {
            return;
        }
        if (userRepository.findByEmail(email).isEmpty()) {
            return;
        }
        String token = tokenStore.create(email, TOKEN_TTL);
        sendResetEmail(email, token, locale);
    }

    public Optional<User> consumeUser(String token, String email) {
        if (token == null || token.isBlank() || email == null || email.isBlank()) {
            return Optional.empty();
        }
        Optional<String> consumed = tokenStore.consume(token.strip(), email.strip());
        return consumed.flatMap(userRepository::findByEmail);
    }

    private void sendResetEmail(String email, String token, Locale locale) {
        Locale lang = LocalizedText.toLocale(locale);
        String subject = messageSource.getMessage("mail.reset.subject", null, lang);
        String resetUrl = frontResetUrl + (frontResetUrl.contains("?") ? "&" : "?") + "token=" + token;
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("resetUrl", resetUrl);
        mailService.sendAsync(email, subject, "Restablece tu contraseña\n\nAbre este enlace: " + resetUrl,
                templateRenderer.renderResetHtml(variables, lang));
    }
}
