package dev.pepe1603.portfolio_api.service;

import dev.pepe1603.portfolio_api.entity.User;
import dev.pepe1603.portfolio_api.repository.UserRepository;
import dev.pepe1603.portfolio_api.security.PasswordResetTokenStore;
import dev.pepe1603.portfolio_api.security.SecurityMailProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class PasswordResetService {

    private static final Logger LOG = LoggerFactory.getLogger(PasswordResetService.class);
    private static final Duration TOKEN_TTL = Duration.ofMinutes(30);

    private final PasswordResetTokenStore tokenStore;
    private final UserRepository userRepository;
    private final JavaMailSender mailSender;
    private final MailTemplateRenderer templateRenderer;
    private final MessageSource messageSource;
    private final SecurityMailProperties mailProperties;
    private final String fromEmail;
    private final String frontResetUrl;

    public PasswordResetService(PasswordResetTokenStore tokenStore, UserRepository userRepository,
            JavaMailSender mailSender, MailTemplateRenderer templateRenderer, MessageSource messageSource,
            SecurityMailProperties mailProperties,
            @Value("${APP_CONTACT_FROM_EMAIL:}") String fromEmail,
            @Value("${APP_FRONT_RESET_URL:}") String frontResetUrl) {
        this.tokenStore = tokenStore;
        this.userRepository = userRepository;
        this.mailSender = mailSender;
        this.templateRenderer = templateRenderer;
        this.messageSource = messageSource;
        this.mailProperties = mailProperties;
        this.fromEmail = fromEmail;
        this.frontResetUrl = frontResetUrl;
    }

    public void requestReset(String email, Locale locale) {
        // Con el interruptor apagado no se crea siquiera el token: así no quedan tokens de un solo
        // uso huérfanos en Redis esperando que alguien los consuma.
        if (!mailProperties.isResetEnabled() || fromEmail == null || fromEmail.isBlank()) {
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
        try {
            Locale lang = normalizeLocale(locale);
            String subject = messageSource.getMessage("mail.reset.subject", null, lang);
            String resetUrl = frontResetUrl + (frontResetUrl.contains("?") ? "&" : "?") + "token=" + token;
            Map<String, Object> variables = new LinkedHashMap<>();
            variables.put("resetUrl", resetUrl);
            String html = templateRenderer.renderResetHtml(variables, lang);

            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(email);
            helper.setSubject(subject);
            MimeMultipart alternative = new MimeMultipart("alternative");
            MimeBodyPart textPart = new MimeBodyPart();
            textPart.setText("Restablece tu contraseña\n\nAbre este enlace: " + resetUrl, "UTF-8");
            MimeBodyPart htmlPart = new MimeBodyPart();
            htmlPart.setContent(html, "text/html; charset=UTF-8");
            alternative.addBodyPart(textPart);
            alternative.addBodyPart(htmlPart);
            mime.setContent(alternative);
            mailSender.send(mime);
        } catch (MessagingException e) {
            LOG.error("No se pudo enviar el correo de restablecimiento de contraseña", e);
        }
    }

    private Locale normalizeLocale(Locale locale) {
        return locale != null && "en".equals(locale.getLanguage()) ? Locale.ENGLISH : Locale.forLanguageTag("es");
    }
}