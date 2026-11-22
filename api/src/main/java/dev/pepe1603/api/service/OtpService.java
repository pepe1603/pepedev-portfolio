package dev.pepe1603.api.service;

import dev.pepe1603.api.entity.User;
import dev.pepe1603.api.repository.UserRepository;
import dev.pepe1603.api.security.OtpChallengeStore;
import dev.pepe1603.api.security.OtpProperties;
import dev.pepe1603.api.util.LocalizedText;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
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

    /**
     * Reenvía el código de un login a medio terminar, para cuando el correo no llegó o llegó a la
     * carpeta de spam.
     *
     * <p>Se genera un código nuevo y el anterior deja de valer: el store solo guarda el hash, así
     * que repetir el mismo no es opción, y es además lo que espera cualquiera que haya pedido un
     * reenvío. El challenge tampoco se alarga, así que un login sigue teniendo una ventana de
     * cinco minutos en total y no cinco minutos por cada reenvío.
     *
     * <p>No se filtra el email de nadie: se entra con el challengeId, que es el UUID que el
     * cliente ya tiene, y no con una dirección. Lo único que podría hacer quien tuviera ese UUID
     * es escribirle a esa misma persona, con un enfriamiento de 30 segundos y hasta que el challenge
     * caduque, mientras que el propio cliente tiene el UUID en el mismo sitio que su código.
     */
    public OtpResendResult resend(String challengeId, Locale locale) {
        if (challengeId == null || challengeId.isBlank()) {
            return OtpResendResult.notFound();
        }
        OptionalLong espera = otpChallengeStore.claimResendWindow(challengeId,
                Duration.ofSeconds(otpProperties.getResendCooldownSeconds()));
        if (espera.isPresent()) {
            return OtpResendResult.tooSoon(espera.getAsLong());
        }
        String code = generateCode();
        Optional<String> email = otpChallengeStore.recode(challengeId, hash(code));
        if (email.isEmpty()) {
            return OtpResendResult.notFound();
        }
        sendOtpEmail(email.get(), code, locale);
        return OtpResendResult.sent();
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


    /** Lo que el cliente necesita para pintar el botón de reenviar. */
    public record OtpResendResult(Status status, long retryAfterSeconds) {

        public enum Status {
            /** Correo encolado. */
            RESENT,
            /** Hace poco que se reenvió: reintentar en retryAfterSeconds. */
            TOO_SOON,
            /** El challenge no existe o ya caducó: hay que empezar el login otra vez. */
            NOT_FOUND
        }

        static OtpResendResult sent() {
            return new OtpResendResult(Status.RESENT, 0);
        }

        static OtpResendResult tooSoon(long retryAfterSeconds) {
            return new OtpResendResult(Status.TOO_SOON, retryAfterSeconds);
        }

        static OtpResendResult notFound() {
            return new OtpResendResult(Status.NOT_FOUND, 0);
        }
    }
}
