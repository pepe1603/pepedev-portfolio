package dev.pepe1603.portfolio_api.service;

import dev.pepe1603.portfolio_api.dto.contact.ContactRequest;
import dev.pepe1603.portfolio_api.entity.Message;
import dev.pepe1603.portfolio_api.repository.MessageRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class ContactService {

    private static final Logger LOG = LoggerFactory.getLogger(ContactService.class);
    private static final String FALLBACK_IP = "0.0.0.0";

    private final MessageRepository messageRepository;
    private final JavaMailSender mailSender;
    private final String destEmail;
    private final String fromEmail;

    public ContactService(MessageRepository messageRepository, JavaMailSender mailSender,
            @Value("${APP_CONTACT_DEST_EMAIL:}") String destEmail,
            @Value("${APP_CONTACT_FROM_EMAIL:}") String fromEmail) {
        this.messageRepository = messageRepository;
        this.mailSender = mailSender;
        this.destEmail = destEmail;
        this.fromEmail = fromEmail;
    }

    public void save(ContactRequest request, String rawIp, String userAgent) {
        Message message = new Message();
        message.setName(request.name().strip());
        message.setEmail(request.email().strip());
        message.setSubject(request.subject().strip());
        message.setBody(request.body().strip());
        message.setIp(anonymizeIp(rawIp));
        message.setUserAgent(truncate(userAgent, 255));
        messageRepository.save(message);
        sendNotificationAsync(message);
    }

    private void sendNotificationAsync(Message message) {
        if (fromEmail == null || fromEmail.isBlank() || destEmail == null || destEmail.isBlank()) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                MimeMessage mime = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(mime, "UTF-8");
                helper.setFrom(fromEmail);
                helper.setTo(destEmail);
                helper.setReplyTo(message.getEmail());
                helper.setSubject("[Contacto] " + message.getSubject());
                helper.setText("De: " + message.getName() + " <" + message.getEmail() + ">\n"
                        + "IP: " + message.getIp() + "\n\n"
                        + message.getBody());
                mailSender.send(mime);
            } catch (MessagingException e) {
                LOG.error("No se pudo enviar la notificación de contacto", e);
            }
        });
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