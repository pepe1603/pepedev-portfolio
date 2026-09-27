package dev.pepe1603.portfolio_api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import dev.pepe1603.portfolio_api.entity.User;
import dev.pepe1603.portfolio_api.enums.UserRole;
import dev.pepe1603.portfolio_api.repository.UserRepository;
import dev.pepe1603.portfolio_api.security.OtpChallengeStore;
import dev.pepe1603.portfolio_api.security.OtpProperties;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.MessageSource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

class OtpServiceTest {

    private final OtpChallengeStore otpChallengeStore = mock(OtpChallengeStore.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private final MailTemplateRenderer templateRenderer = mock(MailTemplateRenderer.class);
    private final MessageSource messageSource = mock(MessageSource.class);
    private final OtpProperties otpProperties = new OtpProperties();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(otpProperties, "enabled", true);
        ReflectionTestUtils.setField(otpProperties, "ttlSeconds", 300L);
        ReflectionTestUtils.setField(otpProperties, "maxAttempts", 5);
        given(messageSource.getMessage(anyString(), isNull(), any())).willReturn("Tu código de acceso");
        given(templateRenderer.renderOtpHtml(any(), any())).willReturn("<html>OTP</html>");
    }

    private OtpService service() {
        return new OtpService(otpChallengeStore, otpProperties, userRepository, mailSender, templateRenderer,
                messageSource, "from@pepe.dev");
    }

    private User admin() {
        User user = new User();
        user.setEmail("admin@pepe.dev");
        user.setRole(UserRole.ADMIN);
        return user;
    }

    private String hash(String code) throws Exception {
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        return java.util.HexFormat.of().formatHex(digest.digest(code.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    @Test
    void createChallengeGuardaHashYEnviaCorreoConCodigoDe6Digitos() throws Exception {
        given(otpChallengeStore.create(anyString(), anyString(), any())).willReturn("challenge-1");
        given(mailSender.createMimeMessage())
                .willAnswer(invocation -> new MimeMessage(Session.getInstance(new Properties())));

        String challengeId = service().createChallenge("admin@pepe.dev", Locale.forLanguageTag("es"));

        assertThat(challengeId).isEqualTo("challenge-1");

        ArgumentCaptor<String> codeHash = ArgumentCaptor.forClass(String.class);
        verify(otpChallengeStore).create(startsWith("admin@pepe.dev"), codeHash.capture(),
                org.mockito.ArgumentMatchers.eq(java.time.Duration.ofSeconds(300)));
        assertThat(codeHash.getValue()).hasSize(64).isNotEqualTo("000000");

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, timeout(2000).times(1)).send(captor.capture());
        MimeMessage sent = captor.getValue();
        sent.saveChanges();
        assertThat(sent.getSubject()).isEqualTo("Tu código de acceso");
        assertThat(sent.getAllRecipients()[0]).hasToString("admin@pepe.dev");
    }

    @Test
    void verifyConCodigoCorrectoConsumeElChallengeYDevuelveElUsuario() throws Exception {
        given(otpChallengeStore.peek("challenge-1"))
                .willReturn(Optional.of(new OtpChallengeStore.OtpChallenge("admin@pepe.dev", hash("123456"))));
        given(userRepository.findByEmail("admin@pepe.dev")).willReturn(Optional.of(admin()));

        assertThat(service().verify("challenge-1", "123456")).isPresent();

        verify(otpChallengeStore).delete("challenge-1");
        verify(otpChallengeStore, never()).incrementAttempts(anyString());
    }

    @Test
    void verifyConCodigoIncorrectoSumaIntentoYNoConsume() throws Exception {
        given(otpChallengeStore.peek("challenge-1"))
                .willReturn(Optional.of(new OtpChallengeStore.OtpChallenge("admin@pepe.dev", hash("123456"))));
        given(otpChallengeStore.incrementAttempts("challenge-1")).willReturn(1L);

        assertThat(service().verify("challenge-1", "000000")).isEmpty();

        verify(otpChallengeStore).incrementAttempts("challenge-1");
        verify(otpChallengeStore, never()).delete(anyString());
    }

    @Test
    void verifyAlAgotarIntentosEliminaElChallenge() throws Exception {
        given(otpChallengeStore.peek("challenge-1"))
                .willReturn(Optional.of(new OtpChallengeStore.OtpChallenge("admin@pepe.dev", hash("123456"))));
        given(otpChallengeStore.incrementAttempts("challenge-1")).willReturn(5L);

        assertThat(service().verify("challenge-1", "000000")).isEmpty();

        verify(otpChallengeStore).delete("challenge-1");
    }

    @Test
    void verifyConChallengeCaducadoDevuelveVacio() {
        given(otpChallengeStore.peek("challenge-1")).willReturn(Optional.empty());

        assertThat(service().verify("challenge-1", "123456")).isEmpty();

        verify(otpChallengeStore, never()).delete(anyString());
    }

    @Test
    void verifyConEntradasVaciasNoTocaRedis() {
        assertThat(service().verify(" ", "123456")).isEmpty();
        assertThat(service().verify("challenge-1", " ")).isEmpty();

        verify(otpChallengeStore, never()).peek(anyString());
    }

    @Test
    void sinFromEmailNoEnviaCorreoPeroGuardaElChallenge() {
        given(otpChallengeStore.create(anyString(), anyString(), any())).willReturn("challenge-1");
        OtpService service = new OtpService(otpChallengeStore, otpProperties, userRepository, mailSender,
                templateRenderer, messageSource, "");

        service.createChallenge("admin@pepe.dev", Locale.forLanguageTag("es"));

        verify(otpChallengeStore).create(anyString(), anyString(), any());
        verify(mailSender, never()).send(any(MimeMessage.class));
        verify(mailSender, times(0)).createMimeMessage();
    }
}