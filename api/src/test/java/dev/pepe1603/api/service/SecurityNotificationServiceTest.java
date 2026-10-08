package dev.pepe1603.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import dev.pepe1603.api.entity.User;
import dev.pepe1603.api.enums.UserRole;
import dev.pepe1603.api.repository.AuthSessionRepository;
import dev.pepe1603.api.security.SecurityMailProperties;
import jakarta.mail.BodyPart;
import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.MessageSource;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

class SecurityNotificationServiceTest {

    private static final Locale ES = Locale.forLanguageTag("es");
    private static final Locale EN = Locale.forLanguageTag("en");

    private final AuthSessionRepository authSessionRepository = mock(AuthSessionRepository.class);
    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private final MailTemplateRenderer templateRenderer = mock(MailTemplateRenderer.class);
    private final MessageSource messageSource = mock(MessageSource.class);
    private final SecurityMailProperties properties = new SecurityMailProperties();

    @BeforeEach
    void setUp() {
        given(messageSource.getMessage(anyString(), isNull(), any()))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(templateRenderer.renderSessionLoginHtml(any(), any()))
                .willAnswer(invocation -> "<html>LOGIN " + htmlVariables(invocation.getArgument(0)) + "</html>");
        given(templateRenderer.renderSessionLogoutHtml(any(), any()))
                .willAnswer(invocation -> "<html>LOGOUT " + htmlVariables(invocation.getArgument(0)) + "</html>");
    }

    private static String htmlVariables(Map<String, Object> variables) {
        return variables.get("ip") + "|" + variables.get("userAgent") + "|" + variables.get("fecha");
    }

    private SecurityNotificationService service() {
        return service(true, true, "from@pepe.dev");
    }

    private SecurityNotificationService service(boolean loginMail, boolean logoutMail, String fromEmail) {
        ReflectionTestUtils.setField(properties, "loginEnabled", loginMail);
        ReflectionTestUtils.setField(properties, "logoutEnabled", logoutMail);
        return new SecurityNotificationService(authSessionRepository, new MailService(mailSender, fromEmail),
                templateRenderer, messageSource, properties);
    }

    private User admin() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("admin@pepe.dev");
        user.setRole(UserRole.ADMIN);
        return user;
    }

    private void stubMime() throws Exception {
        given(mailSender.createMimeMessage()).willAnswer(invocation ->
                new MimeMessage(Session.getInstance(new Properties())));
    }

    @Test
    void conElInterruptorDeLoginApagadoNoConsultaElHistoricoNiEnvia() throws Exception {
        stubMime();

        service(false, true, "from@pepe.dev").notifyNewLogin(admin(), "10.0.0.1", "curl/8", ES);

        verify(authSessionRepository, never()).existsByUserIdAndIpAddress(any(), anyString());
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void conElInterruptorDeLogoutApagadoNoEnvia() throws Exception {
        stubMime();

        service(true, false, "from@pepe.dev").notifyLogout(admin(), "10.0.0.1", "curl/8", ES);

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void sinRemitenteNoConsultaElHistoricoNiEnvia() throws Exception {
        stubMime();

        service(true, true, "").notifyNewLogin(admin(), "10.0.0.1", "curl/8", ES);

        verify(authSessionRepository, never()).existsByUserIdAndIpAddress(any(), anyString());
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void siLaIpYaSeHabiaUsadoNoEnviaAviso() throws Exception {
        stubMime();
        given(authSessionRepository.existsByUserIdAndIpAddress(any(), anyString())).willReturn(true);

        service().notifyNewLogin(admin(), "10.0.0.1", "curl/8", ES);

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void siElNavegadorYaSeHabiaUsadoNoEnviaAviso() throws Exception {
        stubMime();
        given(authSessionRepository.existsByUserIdAndUserAgent(any(), anyString())).willReturn(true);

        service().notifyNewLogin(admin(), "10.0.0.1", "curl/8", ES);

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void conIpYNavegadorDesconocidosEnviaElAvisoDeLogin() throws Exception {
        stubMime();

        service().notifyNewLogin(admin(), "10.0.0.1", "curl/8", ES);

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, timeout(2000)).send(captor.capture());
        MimeMessage sent = captor.getValue();
        sent.saveChanges();
        assertThat(sent.getContentType()).contains("multipart/alternative");
        assertThat(sent.getAllRecipients()[0].toString()).isEqualTo("admin@pepe.dev");
        assertThat(sent.getSubject()).isEqualTo("mail.session.login.subject");
        verify(templateRenderer).renderSessionLoginHtml(any(), eq(ES));
    }

    @Test
    void elAvisoDeLoginLlevaLosDatosDeLaSesionEnElHtmlYEnElTextoPlano() throws Exception {
        stubMime();

        service().notifyNewLogin(admin(), "10.0.0.1", "curl/8", ES);

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, timeout(2000)).send(captor.capture());
        MimeMessage sent = captor.getValue();
        sent.saveChanges();
        String content = contentAsString(sent);
        assertThat(content)
                .contains("10.0.0.1")
                .contains("curl/8")
                .contains("mail.session.login.fecha")
                .contains("mail.session.login.warning");
    }

    @Test
    void conLocaleEnElAvisoUsaElCorreoEnIngles() throws Exception {
        stubMime();

        service().notifyLogout(admin(), "10.0.0.1", "curl/8", EN);

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, timeout(2000)).send(captor.capture());
        MimeMessage sent = captor.getValue();
        sent.saveChanges();
        assertThat(sent.getSubject()).isEqualTo("mail.session.logout.subject");
        assertThat(contentAsString(sent)).contains("mail.session.logout.navegador");
        verify(templateRenderer).renderSessionLogoutHtml(any(), eq(Locale.ENGLISH));
    }

    @Test
    void sinUserAgentElTextoPlanoPoneUnGuionLargo() throws Exception {
        stubMime();

        service().notifyLogout(admin(), "10.0.0.1", null, ES);

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, timeout(2000)).send(captor.capture());
        MimeMessage sent = captor.getValue();
        sent.saveChanges();
        assertThat(contentAsString(sent)).contains("mail.session.logout.navegador —");
    }

    @Test
    void elAvisoDeLogoutNoConsultaElHistoricoDeSesiones() throws Exception {
        stubMime();

        service().notifyLogout(admin(), "10.0.0.1", "curl/8", ES);

        verify(authSessionRepository, never()).existsByUserIdAndIpAddress(any(), anyString());
        verify(mailSender, timeout(2000)).send(any(MimeMessage.class));
    }

    @Test
    void siFallaLaBaseDeDatosElLoginNoSeRompe() {
        given(authSessionRepository.existsByUserIdAndIpAddress(any(), anyString()))
                .willThrow(new org.springframework.dao.QueryTimeoutException("redis/BD no responde"));

        assertThatCode(() -> service().notifyNewLogin(admin(), "10.0.0.1", "curl/8", ES))
                .doesNotThrowAnyException();
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void siFallaElSmtpElLoginNoSeRompe() throws Exception {
        stubMime();
        willFailSending();

        assertThatCode(() -> service().notifyNewLogin(admin(), "10.0.0.1", "curl/8", ES))
                .doesNotThrowAnyException();
        assertThatCode(() -> service().notifyLogout(admin(), "10.0.0.1", "curl/8", ES))
                .doesNotThrowAnyException();
        // El envío se intentó de verdad: el fallo es del SMTP, no un no-op silencioso.
        verify(mailSender, timeout(2000).times(2)).send(any(MimeMessage.class));
    }

    private void willFailSending() throws Exception {
        // Es lo que lanza JavaMailSender cuando el servidor SMTP rechaza o no responde.
        doThrow(new MailSendException("smtp caído")).when(mailSender).send(any(MimeMessage.class));
    }

    private String contentAsString(MimeMessage sent) throws Exception {
        StringBuilder sb = new StringBuilder();
        Multipart multipart = (Multipart) sent.getContent();
        for (int i = 0; i < multipart.getCount(); i++) {
            sb.append(((BodyPart) multipart.getBodyPart(i)).getContent());
        }
        return sb.toString();
    }
}
