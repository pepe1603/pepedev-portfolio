package dev.pepe1603.portfolio_api.service;

import dev.pepe1603.portfolio_api.entity.User;
import dev.pepe1603.portfolio_api.repository.AuthSessionRepository;
import dev.pepe1603.portfolio_api.security.SecurityMailProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Avisos por correo de los eventos de sesión. Los dos van al propio usuario (que es quien sabe si
 * el acceso era suyo) y los dos se pueden apagar por separado con
 * {@code APP_MAIL_SECURITY_LOGIN} y {@code APP_MAIL_SECURITY_LOGOUT}, ambos apagados por defecto.
 *
 * <p>Del login solo se avisa cuando el acceso llega desde una IP o un navegador que el usuario no
 * había usado antes, que es lo que hace útil el aviso: un correo en cada login del mismo portátil
 * solo genera ruido. La comparación se hace <em>antes</em> de guardar la sesión nueva (si no, la
 * sesión se encontraría a sí misma) y de forma síncrona, porque el envío es asíncrono.
 *
 * <p>El envío es best-effort, igual que el del formulario de contacto: nunca debe tumbar un login
 * ni un logout. Si falta el remitente o falla SMTP, se loguea y la petición responde igual.
 */
@Service
public class SecurityNotificationService {

    private static final Logger LOG = LoggerFactory.getLogger(SecurityNotificationService.class);
    private static final Locale ADMIN_LOCALE = Locale.forLanguageTag("es");
    private static final DateTimeFormatter FECHA_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final AuthSessionRepository authSessionRepository;
    private final JavaMailSender mailSender;
    private final MailTemplateRenderer templateRenderer;
    private final MessageSource messageSource;
    private final SecurityMailProperties properties;
    private final String fromEmail;

    public SecurityNotificationService(AuthSessionRepository authSessionRepository, JavaMailSender mailSender,
            MailTemplateRenderer templateRenderer, MessageSource messageSource, SecurityMailProperties properties,
            @Value("${APP_CONTACT_FROM_EMAIL:}") String fromEmail) {
        this.authSessionRepository = authSessionRepository;
        this.mailSender = mailSender;
        this.templateRenderer = templateRenderer;
        this.messageSource = messageSource;
        this.properties = properties;
        this.fromEmail = fromEmail;
    }

    public void notifyNewLogin(User user, String ip, String userAgent, Locale locale) {
        if (!properties.isLoginEnabled() || !canSend()) {
            return;
        }
        try {
            boolean knownContext = authSessionRepository.existsByUserIdAndIpAddress(user.getId(), ip)
                    || (userAgent != null && !userAgent.isBlank()
                            && authSessionRepository.existsByUserIdAndUserAgent(user.getId(), userAgent));
            if (knownContext) {
                return;
            }
            send(user.getEmail(), "mail.session.login", ip, userAgent, locale,
                    (lang, variables) -> templateRenderer.renderSessionLoginHtml(variables, lang));
        } catch (RuntimeException e) {
            // La comparación con el histórico se hace en el hilo de la petición, así que un fallo
            // de Redis/BD aquí no puede acabar en un 500 del login: se pierde el aviso, no el acceso.
            LOG.error("No se pudo comprobar si el acceso de {} es nuevo", user.getEmail(), e);
        }
    }

    public void notifyLogout(User user, String ip, String userAgent, Locale locale) {
        if (!properties.isLogoutEnabled() || !canSend()) {
            return;
        }
        try {
            send(user.getEmail(), "mail.session.logout", ip, userAgent, locale,
                    (lang, variables) -> templateRenderer.renderSessionLogoutHtml(variables, lang));
        } catch (RuntimeException e) {
            LOG.error("No se pudo preparar el aviso de cierre de sesión de {}", user.getEmail(), e);
        }
    }

    private void send(String to, String keyPrefix, String ip, String userAgent, Locale locale,
            HtmlRenderer renderer) {
        Locale lang = normalizeLocale(locale);
        String fecha = LocalDateTime.now().format(FECHA_FORMAT);
        CompletableFuture.runAsync(() -> {
            try {
                Map<String, Object> variables = new LinkedHashMap<>();
                variables.put("fecha", fecha);
                variables.put("ip", ip);
                variables.put("userAgent", userAgent);
                String subject = messageSource.getMessage(keyPrefix + ".subject", null, lang);
                sendMime(to, subject, plainText(keyPrefix, fecha, ip, userAgent, lang),
                        renderer.render(lang, variables));
            } catch (Exception e) {
                // Cubre las dos fuentes de fallo: las checked al construir el MimeMessage y la
                // MailSendException (unchecked) que lanza mailSender.send() si el SMTP falla. Sin
                // este catch la excepción se perdería en el pool sin dejar rastro.
                LOG.error("No se pudo enviar el aviso de seguridad {}", keyPrefix, e);
            }
        });
    }

    private void sendMime(String to, String subject, String plain, String html) throws MessagingException {
        MimeMessage mime = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mime, "UTF-8");
        helper.setFrom(fromEmail);
        helper.setTo(to);
        helper.setSubject(subject);
        MimeMultipart alternative = new MimeMultipart("alternative");
        MimeBodyPart textPart = new MimeBodyPart();
        textPart.setText(plain, "UTF-8");
        MimeBodyPart htmlPart = new MimeBodyPart();
        htmlPart.setContent(html, "text/html; charset=UTF-8");
        alternative.addBodyPart(textPart);
        alternative.addBodyPart(htmlPart);
        mime.setContent(alternative);
        mailSender.send(mime);
    }

    /** La parte de texto plano se compone con las mismas claves que la plantilla, también i18n. */
    private String plainText(String keyPrefix, String fecha, String ip, String userAgent, Locale lang) {
        String navegador = userAgent == null || userAgent.isBlank() ? "—" : userAgent;
        return text(keyPrefix, lang, "intro") + "\n\n"
                + text(keyPrefix, lang, "fecha") + " " + fecha + "\n"
                + text(keyPrefix, lang, "ip") + " " + ip + "\n"
                + text(keyPrefix, lang, "navegador") + " " + navegador + "\n\n"
                + text(keyPrefix, lang, "warning");
    }

    private String text(String keyPrefix, Locale lang, String key) {
        return messageSource.getMessage(keyPrefix + "." + key, null, lang);
    }

    private boolean canSend() {
        if (fromEmail != null && !fromEmail.isBlank()) {
            return true;
        }
        LOG.warn("Aviso de seguridad omitido: falta APP_CONTACT_FROM_EMAIL");
        return false;
    }

    private Locale normalizeLocale(Locale locale) {
        return locale != null && "en".equals(locale.getLanguage()) ? Locale.ENGLISH : ADMIN_LOCALE;
    }

    @FunctionalInterface
    private interface HtmlRenderer {
        String render(Locale locale, Map<String, Object> variables);
    }
}
