package dev.pepe1603.portfolio_api.service;

import dev.pepe1603.portfolio_api.entity.User;
import dev.pepe1603.portfolio_api.repository.UserRepository;
import dev.pepe1603.portfolio_api.security.OtpChallengeStore;
import dev.pepe1603.portfolio_api.security.OtpProperties;
import dev.pepe1603.portfolio_api.util.LocalizedText;
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
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

@Service
public class OtpService {

    private static final int CODE_DIGITS = 6;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final OtpChallengeStore otpChallengeStore;
    private final OtpProperties otpProperties;
    private final UserRepository userRepository;
    private final MailService mailService;
    private final MailTemplateRenderer templateRenderer;
    private final MessageSource messageSource;

    public OtpService(OtpChallengeStore otpChallengeStore, OtpProperties otpProperties, UserRepository userRepository,
            MailService mailService, MailTemplateRenderer templateRenderer, MessageSource messageSource) {
        this.otpChallengeStore = otpChallengeStore;
        this.otpProperties = otpProperties;
        this.userRepository = userRepository;
        this.mailService = mailService;
        this.templateRenderer = templateRenderer;
        this.messageSource = messageSource;
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
        if (!mailService.canSend()) {
            return;
        }
        Locale lang = LocalizedText.toLocale(locale);
        String subject = messageSource.getMessage("mail.otp.subject", null, lang);
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("code", code);
        variables.put("minutes", otpProperties.getTtlSeconds() / 60);
        mailService.sendAsync(email, subject, "Tu código de acceso es: " + code,
                templateRenderer.renderOtpHtml(variables, lang));
    }

}