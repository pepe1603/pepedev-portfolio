package dev.pepe1603.portfolio_api.service;

import dev.pepe1603.portfolio_api.dto.contact.ContactRequest;
import dev.pepe1603.portfolio_api.entity.Message;
import dev.pepe1603.portfolio_api.repository.MessageRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import java.net.InetAddress;
import java.net.UnknownHostException;
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

@Service
public class ContactService {

    private static final Logger LOG = LoggerFactory.getLogger(ContactService.class);
    private static final String FALLBACK_IP = "0.0.0.0";
    private static final Locale ADMIN_LOCALE = Locale.forLanguageTag("es");
    private static final DateTimeFormatter FECHA_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final MessageRepository messageRepository;
    private final JavaMailSender mailSender;
    private final MailTemplateRenderer templateRenderer;
    private final MessageSource messageSource;
    private final String destEmail;
    private final String fromEmail;
    private final boolean sendAck;

    public ContactService(MessageRepository messageRepository, JavaMailSender mailSender,
            MailTemplateRenderer templateRenderer, MessageSource messageSource,
            @Value("${APP_CONTACT_DEST_EMAIL:}") String destEmail,
            @Value("${APP_CONTACT_FROM_EMAIL:}") String fromEmail,
            @Value("${APP_CONTACT_SEND_ACK:true}") boolean sendAck) {
        this.messageRepository = messageRepository;
        this.mailSender = mailSender;
        this.templateRenderer = templateRenderer;
        this.messageSource = messageSource;
        this.destEmail = destEmail;
        this.fromEmail = fromEmail;
        this.sendAck = sendAck;
    }

    public void save(ContactRequest request, String rawIp, String userAgent, Locale locale) {
        Message message = new Message();
        message.setName(request.name().strip());
        message.setEmail(request.email().strip());
        message.setSubject(request.subject().strip());
        message.setBody(request.body().strip());
        message.setIp(anonymizeIp(rawIp));
        message.setUserAgent(truncate(userAgent, 255));
        messageRepository.save(message);
        sendNotificationAsync(message);
        sendAckAsync(message, locale);
    }

    private void sendNotificationAsync(Message message) {
        if (fromEmail == null || fromEmail.isBlank() || destEmail == null || destEmail.isBlank()) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                String fecha = LocalDateTime.now().format(FECHA_FORMAT);
                String html = templateRenderer.renderContactHtml(notificationVariables(message, fecha), ADMIN_LOCALE);
                sendMime(destEmail, message.getEmail(), "[Contacto] " + message.getSubject(),
                        plainText(message, fecha), html);
            } catch (Exception e) {
                // El fallo de transporte llega como MailSendException (unchecked), no como
                // MessagingException: sin este catch se pierde en el pool sin dejar rastro.
                LOG.error("No se pudo enviar la notificación de contacto", e);
            }
        });
    }

    private void sendAckAsync(Message message, Locale locale) {
        if (!sendAck || fromEmail == null || fromEmail.isBlank()) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                Locale lang = normalizeLocale(locale);
                String subject = messageSource.getMessage("mail.ack.subject", null, lang);
                String confirm = messageSource.getMessage("mail.ack.confirm", new Object[] { message.getSubject() },
                        lang);
                String plain = "Hola, " + message.getName() + "\n\n" + confirm;
                String html = templateRenderer.renderAckHtml(ackVariables(message), lang);
                sendMime(message.getEmail(), null, subject, plain, html);
            } catch (Exception e) {
                LOG.error("No se pudo enviar el acuse de contacto", e);
            }
        });
    }

    private void sendMime(String to, String replyTo, String subject, String plain, String html)
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

    private Map<String, Object> notificationVariables(Message message, String fecha) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("name", message.getName());
        variables.put("email", message.getEmail());
        variables.put("ip", message.getIp());
        variables.put("fecha", fecha);
        variables.put("subject", message.getSubject());
        variables.put("body", message.getBody());
        return variables;
    }

    private Map<String, Object> ackVariables(Message message) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("name", message.getName());
        variables.put("subject", message.getSubject());
        variables.put("fecha", LocalDateTime.now().format(FECHA_FORMAT));
        return variables;
    }

    private Locale normalizeLocale(Locale locale) {
        return locale != null && "en".equals(locale.getLanguage()) ? Locale.ENGLISH : ADMIN_LOCALE;
    }

    private String plainText(Message message, String fecha) {
        return "De: " + message.getName() + " <" + message.getEmail() + ">\n"
                + "IP: " + message.getIp() + "\n"
                + "Fecha: " + fecha + "\n"
                + "Asunto: " + message.getSubject() + "\n\n"
                + message.getBody();
    }

    private String anonymizeIp(String raw) {
        if (raw == null || raw.isBlank()) {
            return FALLBACK_IP;
        }
        String ip = raw.strip();
        if (ip.startsWith("::ffff:")) {
            ip = ip.substring("::ffff:".length());
        }
        try {
            byte[] bytes = InetAddress.getByName(ip).getAddress();
            int firstMaskedByte = bytes.length == 4 ? 3 : 8;
            for (int i = firstMaskedByte; i < bytes.length; i++) {
                bytes[i] = 0;
            }
            return InetAddress.getByAddress(bytes).getHostAddress();
        } catch (UnknownHostException e) {
            return FALLBACK_IP;
        }
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}