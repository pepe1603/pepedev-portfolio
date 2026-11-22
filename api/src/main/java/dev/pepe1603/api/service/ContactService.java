package dev.pepe1603.api.service;

import dev.pepe1603.api.dto.contact.ContactRequest;
import dev.pepe1603.api.entity.Message;
import dev.pepe1603.api.repository.MessageRepository;
import dev.pepe1603.api.util.LocalizedText;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

@Service
public class ContactService {

    private static final String FALLBACK_IP = "0.0.0.0";
    private static final Locale ADMIN_LOCALE = Locale.forLanguageTag("es");
    private static final DateTimeFormatter FECHA_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final MessageRepository messageRepository;
    private final MailService mailService;
    private final MailTemplateRenderer templateRenderer;
    private final MessageSource messageSource;
    private final String destEmail;
    private final boolean sendAck;

    public ContactService(MessageRepository messageRepository, MailService mailService,
            MailTemplateRenderer templateRenderer, MessageSource messageSource,
            @Value("${APP_CONTACT_DEST_EMAIL:}") String destEmail,
            @Value("${APP_CONTACT_SEND_ACK:true}") boolean sendAck) {
        this.messageRepository = messageRepository;
        this.mailService = mailService;
        this.templateRenderer = templateRenderer;
        this.messageSource = messageSource;
        this.destEmail = destEmail;
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
        sendNotification(message);
        sendAck(message, locale);
    }

    private void sendNotification(Message message) {
        if (destEmail == null || destEmail.isBlank() || !mailService.canSend()) {
            return;
        }
        String fecha = LocalDateTime.now().format(FECHA_FORMAT);
        String html = templateRenderer.renderContactHtml(notificationVariables(message, fecha), ADMIN_LOCALE);
        // Con replyTo para poder contestar al visitante desde el propio cliente de correo.
        mailService.sendAsync(destEmail, message.getEmail(), "[Contacto] " + message.getSubject(),
                plainText(message, fecha), html);
    }

    private void sendAck(Message message, Locale locale) {
        if (!sendAck || !mailService.canSend()) {
            return;
        }
        Locale lang = LocalizedText.toLocale(locale);
        String subject = messageSource.getMessage("mail.ack.subject", null, lang);
        String confirm = messageSource.getMessage("mail.ack.confirm", new Object[] { message.getSubject() }, lang);
        String plain = "Hola, " + message.getName() + "\n\n" + confirm;
        String html = templateRenderer.renderAckHtml(ackVariables(message), lang);
        mailService.sendAsync(message.getEmail(), subject, plain, html);
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