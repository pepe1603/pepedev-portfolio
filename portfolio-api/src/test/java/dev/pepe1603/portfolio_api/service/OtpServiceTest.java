package dev.pepe1603.portfolio_api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
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
import jakarta.mail.BodyPart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.MessageSource;
import org.springframework.mail.MailSendException;
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
        ReflectionTestUtils.setField(otpProperties, "resendCooldownSeconds", 30L);
        given(messageSource.getMessage(anyString(), isNull(), any())).willReturn("Tu código de acceso");
        given(templateRenderer.renderOtpHtml(any(), any())).willReturn("<html>OTP</html>");
    }

    private OtpService service() {
        return new OtpService(otpChallengeStore, otpProperties, userRepository, mailService("from@pepe.dev"),
                templateRenderer, messageSource);
    }

    private MailService mailService(String fromEmail) {
        return new MailService(mailSender, fromEmail);
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
        OtpService service = new OtpService(otpChallengeStore, otpProperties, userRepository, mailService(""),
                templateRenderer, messageSource);

        service.createChallenge("admin@pepe.dev", Locale.forLanguageTag("es"));

        verify(otpChallengeStore).create(anyString(), anyString(), any());
        verify(mailSender, never()).send(any(MimeMessage.class));
        verify(mailSender, times(0)).createMimeMessage();
    }

    @Test
    void smtpCaidoNoTiraElChallengeNiSePierdeEnSilencio() throws Exception {
        given(otpChallengeStore.create(anyString(), anyString(), any())).willReturn("challenge-1");
        given(mailSender.createMimeMessage()).willReturn(new MimeMessage(Session.getInstance(new Properties())));
        doThrow(new MailSendException("Mail server connection failed"))
                .when(mailSender).send(any(MimeMessage.class));

        // El envio va en un CompletableFuture: con el catch viejo la excepcion se perdia en el pool
        // sin dejar ni una linea de log, y el usuario recibia un 202 con un challenge que jamas le
        // llego por correo. Ahora el fallo queda registrado.
        try (LogCapture log = LogCapture.de(MailService.class)) {
            String challengeId = service().createChallenge("admin@pepe.dev", Locale.forLanguageTag("es"));
            assertThat(challengeId).isEqualTo("challenge-1");
            assertThat(log.eventuallyErrorWith(new MailSendException("Mail server connection failed"))).isTrue();
        }
    }

    @Test
    void elReenvioMandaUnCodigoNuevoYElAnteriorDejaDeValer() throws Exception {
        dadoQueSePuedenCrearMensajes();
        String emailNuevo = "admin@pepe.dev";
        CaptorHash captorDelHash = new CaptorHash();
        given(otpChallengeStore.claimResendWindow("c1", Duration.ofSeconds(30))).willReturn(OptionalLong.empty());
        // El store solo guarda el hash, así que el reenvío genera un código distinto.
        given(otpChallengeStore.recode(anyString(), anyString())).willAnswer(invocation -> {
            captorDelHash.value = invocation.getArgument(1);
            return Optional.of(emailNuevo);
        });
        given(userRepository.findByEmail("admin@pepe.dev")).willReturn(Optional.of(admin()));

        OtpService.OtpResendResult result = service().resend("c1", Locale.forLanguageTag("es"));

        assertThat(result.status()).isEqualTo(OtpService.OtpResendResult.Status.RESENT);
        String codigoNuevo = codigoDelCorreoEnviado();
        // Lo que el store guardó es el hash del código que se mandó, no otro: eso es lo que
        // verificará el cliente al introducir el código.
        assertThat(captorDelHash.value).isEqualTo(hash(codigoNuevo));

        given(otpChallengeStore.peek("c1"))
                .willReturn(Optional.of(new OtpChallengeStore.OtpChallenge(emailNuevo, hash(codigoNuevo))));
        assertThat(service().verify("c1", codigoNuevo)).isPresent();
        // El código del primer correo, el que el usuario sigue teniendo abierto, ya no vale.
        assertThat(service().verify("c1", "000000")).isEmpty();
    }

    @Test
    void elReenvioDemasiadoProntoDiceCuantoEsperar() throws Exception {
        dadoQueSePuedenCrearMensajes();
        given(otpChallengeStore.claimResendWindow("c1", Duration.ofSeconds(30))).willReturn(OptionalLong.of(18));

        OtpService.OtpResendResult result = service().resend("c1", Locale.forLanguageTag("es"));

        assertThat(result.status()).isEqualTo(OtpService.OtpResendResult.Status.TOO_SOON);
        // Ni se cambia el código ni se manda correo: el botón solo tiene que esperar.
        verify(otpChallengeStore, never()).recode(anyString(), anyString());
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void elReenvioDeUnChallengeCaducadoNoMandaCorreoNiRespondeQueNo() throws Exception {
        dadoQueSePuedenCrearMensajes();
        given(otpChallengeStore.claimResendWindow("c1", Duration.ofSeconds(30))).willReturn(OptionalLong.empty());
        given(otpChallengeStore.recode(eq("c1"), anyString())).willReturn(Optional.empty());

        OtpService.OtpResendResult result = service().resend("c1", Locale.forLanguageTag("es"));

        assertThat(result.status()).isEqualTo(OtpService.OtpResendResult.Status.NOT_FOUND);
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void elReenvioSinChallengeIdNoTocaRedisNiCorreo() throws Exception {
        dadoQueSePuedenCrearMensajes();

        assertThat(service().resend("  ", Locale.forLanguageTag("es")).status())
                .isEqualTo(OtpService.OtpResendResult.Status.NOT_FOUND);
        verify(otpChallengeStore, never()).claimResendWindow(anyString(), any(Duration.class));
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void elReenvioRespetaElIdiomaDeLaPeticion() throws Exception {
        dadoQueSePuedenCrearMensajes();
        given(otpChallengeStore.claimResendWindow("c1", Duration.ofSeconds(30))).willReturn(OptionalLong.empty());
        given(otpChallengeStore.recode(eq("c1"), anyString())).willReturn(Optional.of("admin@pepe.dev"));
        given(messageSource.getMessage("mail.otp.subject", null, new Locale("en"))).willReturn("Your access code");

        service().resend("c1", Locale.ENGLISH);

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, timeout(2000)).send(captor.capture());
        assertThat(captor.getValue().getSubject()).isEqualTo("Your access code");
    }

    /** Saca el código del texto plano del correo, que es lo que el usuario copia y pega. */
    private String codigoDelCorreoEnviado() throws Exception {
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, timeout(2000).atLeastOnce()).send(captor.capture());
        MimeMessage sent = captor.getValue();
        sent.saveChanges();
        MimeMultipart multipart = (MimeMultipart) sent.getContent();
        for (int i = 0; i < multipart.getCount(); i++) {
            BodyPart part = multipart.getBodyPart(i);
            if (part.getContentType().startsWith("text/plain")) {
                java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d{6})")
                        .matcher(part.getContent().toString());
                if (matcher.find()) {
                    return matcher.group(1);
                }
            }
        }
        throw new AssertionError("El correo no lleva un codigo de 6 digitos en la parte de texto");
    }

    private void dadoQueSePuedenCrearMensajes() {
        try {
            given(mailSender.createMimeMessage())
                    .willAnswer(invocation -> new MimeMessage(Session.getInstance(new Properties())));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Sencillo captor de un solo valor, para el hash que le pide el store al servicio. */
    private static final class CaptorHash {

        private String value;
    }
}
