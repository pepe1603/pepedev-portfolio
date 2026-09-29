package dev.pepe1603.portfolio_api.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Único punto de la aplicación que habla con el servidor SMTP.
 *
 * <p>Existe para que el resto de servicios no repitan el armado del {@code MimeMultipart}, que era
 * idéntico en los cuatro, ni el {@code try/catch} que traga el fallo. Aquí hay una sola versión de
 * las dos cosas, y por lo tanto un solo sitio donde arreglar un fallo de correo.
 *
 * <p>Todos los correos de la API son <em>best-effort</em>: nunca deben tumbar la petición que los
 * disparó. El fallo de transporte llega como {@code MailSendException}, que es unchecked (el
 * {@code MessagingException} de la firma solo cubre el armado del mensaje), así que se captura
 * {@code Exception} y no solo la checked.
 *
 * <p>Por qué hay dos métodos de envío y no uno: casi todos los correos se mandan fuera del hilo de
 * la petición, pero el de restablecimiento de contraseña va en línea, que es el único que hoy puede
 * alargar la respuesta. Igualar eso es un paso aparte, para que cada commit se pueda revisar y
 * revertir por su cuenta.
 */
@Service
public class MailService {

    private static final Logger LOG = LoggerFactory.getLogger(MailService.class);

    private final JavaMailSender mailSender;
    private final String fromEmail;

    public MailService(JavaMailSender mailSender, @Value("${APP_CONTACT_FROM_EMAIL:}") String fromEmail) {
        this.mailSender = mailSender;
        this.fromEmail = fromEmail;
    }

    /**
     * Sin remitente configurado no se puede enviar nada. Quien llama lo consulta antes de crear
     * tokens o challenges, para no generar credenciales que nadie va a recibir por correo.
     */
    public boolean canSend() {
        return fromEmail != null && !fromEmail.isBlank();
    }

    /** Envía en el hilo de la petición. El fallo se registra y no se propaga. */
    public void send(String to, String subject, String plain, String html) {
        send(to, null, subject, plain, html);
    }

    public void send(String to, String replyTo, String subject, String plain, String html) {
        if (!canSend()) {
            return;
        }
        try {
            deliver(to, replyTo, subject, plain, html);
        } catch (Exception e) {
            logFailure(subject, to, e);
        }
    }

    /** Envía fuera del hilo de la petición. El fallo se registra y no se propaga. */
    public void sendAsync(String to, String subject, String plain, String html) {
        sendAsync(to, null, subject, plain, html);
    }

    public void sendAsync(String to, String replyTo, String subject, String plain, String html) {
        if (!canSend()) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                deliver(to, replyTo, subject, plain, html);
            } catch (Exception e) {
                logFailure(subject, to, e);
            }
        });
    }

    /**
     * La alternativa texto + HTML. El texto plano no es opcional: es lo que ven los clientes de
     * correo que no renderizan HTML, y evita que un aviso de acceso nuevo acabe en la carpeta de
     * spam por ser una imagen con texto dentro.
     */
    private void deliver(String to, String replyTo, String subject, String plain, String html)
            throws MessagingException {
        MimeMessage mime = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mime, "UTF-8");
        helper.setFrom(fromEmail);
        helper.setTo(to);
        if (replyTo != null) {
            helper.setReplyTo(replyTo);
        }
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

    private void logFailure(String subject, String to, Exception e) {
        LOG.error("No se pudo enviar el correo '{}' a {}", subject, to, e);
    }
}
