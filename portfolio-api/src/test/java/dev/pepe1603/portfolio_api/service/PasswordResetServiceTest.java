package dev.pepe1603.portfolio_api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import dev.pepe1603.portfolio_api.entity.User;
import dev.pepe1603.portfolio_api.enums.UserRole;
import dev.pepe1603.portfolio_api.repository.UserRepository;
import dev.pepe1603.portfolio_api.security.PasswordResetTokenStore;
import dev.pepe1603.portfolio_api.security.SecurityMailProperties;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.MessageSource;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

class PasswordResetServiceTest {

    private final PasswordResetTokenStore tokenStore = mock(PasswordResetTokenStore.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private final MailTemplateRenderer templateRenderer = mock(MailTemplateRenderer.class);
    private final MessageSource messageSource = mock(MessageSource.class);
    private final SecurityMailProperties mailProperties = new SecurityMailProperties();

    @BeforeEach
    void setUp() {
        given(messageSource.getMessage(anyString(), isNull(), any())).willReturn("Restablece tu contraseña");
        given(templateRenderer.renderResetHtml(any(), any())).willReturn("<html>RESET</html>");
    }

    private PasswordResetService service() {
        return service(true);
    }

    private PasswordResetService service(boolean resetMailEnabled) {
        ReflectionTestUtils.setField(mailProperties, "resetEnabled", resetMailEnabled);
        return new PasswordResetService(tokenStore, userRepository, mailSender, templateRenderer, messageSource,
                mailProperties, "from@pepe.dev", "https://pepe.dev/reset");
    }

    private User admin() {
        User user = new User();
        user.setEmail("admin@pepe.dev");
        user.setRole(UserRole.ADMIN);
        return user;
    }

    @Test
    void requestConCuentaExistenteGeneraTokenYEnviaCorreo() throws Exception {
        given(userRepository.findByEmail("admin@pepe.dev")).willReturn(Optional.of(admin()));
        given(tokenStore.create(anyString(), any())).willReturn("tok123");
        given(mailSender.createMimeMessage()).willAnswer(invocation -> new MimeMessage(Session.getInstance(new Properties())));

        service().requestReset("admin@pepe.dev", Locale.forLanguageTag("es"));

        verify(tokenStore).create("admin@pepe.dev", java.time.Duration.ofMinutes(30));

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        MimeMessage sent = captor.getValue();
        sent.saveChanges();
        assertThat(sent.getSubject()).isEqualTo("Restablece tu contraseña");
        assertThat(sent.getAllRecipients()[0]).hasToString("admin@pepe.dev");
        assertThat(sent.getContentType()).contains("multipart/alternative");
    }

    @Test
    void requestConEmailNoRegistradoNoGeneraTokenNiEnviaCorreo() throws Exception {
        given(userRepository.findByEmail("nadie@pepe.dev")).willReturn(Optional.empty());

        service().requestReset("nadie@pepe.dev", Locale.forLanguageTag("es"));

        verify(tokenStore, never()).create(anyString(), any());
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void sinFromEmailConfiguradoNoGeneraToken() {
        ReflectionTestUtils.setField(mailProperties, "resetEnabled", true);
        PasswordResetService service = new PasswordResetService(tokenStore, userRepository, mailSender,
                templateRenderer, messageSource, mailProperties, "", "https://pepe.dev/reset");

        service.requestReset("admin@pepe.dev", Locale.forLanguageTag("es"));

        verify(tokenStore, never()).create(anyString(), any());
    }

    @Test
    void conElInterruptorDeResetApagadoNoGeneraTokenNiEnviaCorreo() throws Exception {
        given(userRepository.findByEmail("admin@pepe.dev")).willReturn(Optional.of(admin()));

        service(false).requestReset("admin@pepe.dev", Locale.forLanguageTag("es"));

        verify(tokenStore, never()).create(anyString(), any());
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void consumeUserDevuelveElUsuarioYConsumeElToken() {
        given(tokenStore.consume("tok123", "admin@pepe.dev")).willReturn(Optional.of("admin@pepe.dev"));
        given(userRepository.findByEmail("admin@pepe.dev")).willReturn(Optional.of(admin()));

        assertThat(service().consumeUser("tok123", "admin@pepe.dev")).isPresent();
    }

    @Test
    void consumeUserConTokenInvalidoDevuelveVacio() {
        given(tokenStore.consume("malo", "admin@pepe.dev")).willReturn(Optional.empty());

        assertThat(service().consumeUser("malo", "admin@pepe.dev")).isEmpty();
    }

    @Test
    void consumeUserConTokenVacioNoTocaRedis() {
        assertThat(service().consumeUser(" ", "admin@pepe.dev")).isEmpty();

        verify(tokenStore, never()).consume(anyString(), anyString());
    }
    @Test
    void smtpCaidoNoDevuelve500YElTokenQuedaCreado() throws Exception {
        given(userRepository.findByEmail("admin@pepe.dev")).willReturn(Optional.of(admin()));
        given(tokenStore.create(anyString(), org.mockito.ArgumentMatchers.any())).willReturn("token-1");
        given(mailSender.createMimeMessage()).willReturn(new MimeMessage(Session.getInstance(new Properties())));
        // Lo que lanza JavaMailSenderImpl cuando no puede hablar con el servidor SMTP.
        doThrow(new MailSendException("Mail server connection failed"))
                .when(mailSender).send(any(MimeMessage.class));

        // Antes esto escapaba como MailSendException y /auth/reset/request devolvía 500 en vez del
        // 202 documentado. El token se crea igualmente: el correo es best-effort, no la operación.
        assertThatCode(() -> service().requestReset("admin@pepe.dev", Locale.forLanguageTag("es")))
                .doesNotThrowAnyException();
        verify(tokenStore).create(anyString(), org.mockito.ArgumentMatchers.any());
    }

}