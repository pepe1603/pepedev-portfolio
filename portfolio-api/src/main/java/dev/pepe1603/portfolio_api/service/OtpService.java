package dev.pepe1603.portfolio_api.service;

import dev.pepe1603.portfolio_api.entity.User;
import dev.pepe1603.portfolio_api.repository.UserRepository;
import dev.pepe1603.portfolio_api.security.OtpChallengeStore;
import dev.pepe1603.portfolio_api.security.OtpProperties;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class OtpService {

    private static final Logger LOG = LoggerFactory.getLogger(OtpService.class);
    private static final int CODE_DIGITS = 6;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final OtpChallengeStore otpChallengeStore;
    private final OtpProperties otpProperties;
    private final UserRepository userRepository;
    private final JavaMailSender mailSender;
    private final MailTemplateRenderer templateRenderer;
    private final MessageSource messageSource;
    private final String fromEmail;

    public OtpService(OtpChallengeStore otpChallengeStore, OtpProperties otpProperties, UserRepository userRepository,
            JavaMailSender mailSender, MailTemplateRenderer templateRenderer, MessageSource messageSource,
            @Value("${APP_CONTACT_FROM_EMAIL:}") String fromEmail) {
        this.otpChallengeStore = otpChallengeStore;
        this.otpProperties = otpProperties;
        this.userRepository = userRepository;
        this.mailSender = mailSender;
        this.templateRenderer = templateRenderer;
        this.messageSource = messageSource;
        this.fromEmail = fromEmail;
    }

    public String createChallenge(String email, Locale locale) {
        String code = generateCode();
        String challengeId = otpChallengeStore.create(email, hash(code),
                Duration.ofSeconds(otpProperties.getTtlSeconds()));
        sendOtpEmail(email, code, locale);
        return challengeId;
    }

    public Optional<User> verify(String challengeId, String code) {
        if (challengeId == null || challengeId.isBlank() || code == null || code.isBlank()) {
            return Optional.empty();
        }
        Optional<OtpChallengeStore.OtpChallenge> challenge = otpChallengeStore.peek(challengeId);
        if (challenge.isEmpty()) {
            return Optional.empty();
        }
        if (!challenge.get().codeHash().equals(hash(code))) {
            if (otpChallengeStore.incrementAttempts(challengeId) >= otpProperties.getMaxAttempts()) {
                otpChallengeStore.delete(challengeId);
            }
            return Optional.empty();
        }
        otpChallengeStore.delete(challengeId);
        return userRepository.findByEmail(challenge.get().email());
    }

    private String generateCode() {
        return String.format("%0" + CODE_DIGITS + "d", RANDOM.nextInt(1_000_000));
    }

    private String hash(String code) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(code.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    private void sendOtpEmail(String email, String code, Locale locale) {
        if (fromEmail == null || fromEmail.isBlank()) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                Locale lang = normalizeLocale(locale);
                String subject = messageSource.getMessage("mail.otp.subject", null, lang);
                Map<String, Object> variables = new LinkedHashMap<>();
                variables.put("code", code);
                variables.put("minutes", otpProperties.getTtlSeconds() / 60);
                String html = templateRenderer.renderOtpHtml(variables, lang);

                MimeMessage mime = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(mime, "UTF-8");
                helper.setFrom(fromEmail);
                helper.setTo(email);
                helper.setSubject(subject);
                MimeMultipart alternative = new MimeMultipart("alternative");
                MimeBodyPart textPart = new MimeBodyPart();
                textPart.setText("Tu código de acceso es: " + code, "UTF-8");
                MimeBodyPart htmlPart = new MimeBodyPart();
                htmlPart.setContent(html, "text/html; charset=UTF-8");
                alternative.addBodyPart(textPart);
                alternative.addBodyPart(htmlPart);
                mime.setContent(alternative);
                mailSender.send(mime);
            } catch (Exception e) {
                // El fallo de transporte llega como MailSendException (unchecked), no como
                // MessagingException: sin este catch se pierde en el pool sin dejar rastro.
                LOG.error("No se pudo enviar el código OTP", e);
            }
        });
    }

    private Locale normalizeLocale(Locale locale) {
        return locale != null && "en".equals(locale.getLanguage()) ? Locale.ENGLISH : Locale.forLanguageTag("es");
    }
}