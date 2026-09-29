package dev.pepe1603.portfolio_api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import dev.pepe1603.portfolio_api.dto.contact.ContactRequest;
import dev.pepe1603.portfolio_api.repository.MessageRepository;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.util.Locale;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.MessageSource;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

class ContactServiceTest {

    private final MessageRepository messageRepository = mock(MessageRepository.class);
    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private final MailTemplateRenderer templateRenderer = mock(MailTemplateRenderer.class);
    private final MessageSource messageSource = mock(MessageSource.class);

    private ContactService service(boolean sendAck) {
        return new ContactService(messageRepository, mailSender, templateRenderer, messageSource,
                "to@pepe.dev", "from@pepe.dev", sendAck);
    }

    private ContactRequest request() {
        return new ContactRequest("Ana García", "ana@example.es", "Consulta", "Hola,\nquiero saber más.", "");
    }

    private MimeMessage createMime() throws Exception {
        return new MimeMessage(Session.getInstance(new Properties()));
    }

    private void stubRenderer() {
        given(templateRenderer.renderContactHtml(any(), any())).willReturn("<html><body>NOTIF HTML</body></html>");
        given(templateRenderer.renderAckHtml(any(), any())).willReturn("<html><body>ACK HTML</body></html>");
        given(messageSource.getMessage(eq("mail.ack.subject"), isNull(), any())).willReturn("Hemos recibido tu mensaje");
        given(messageSource.getMessage(eq("mail.ack.confirm"), any(), any())).willReturn("confirmación");
    }

    @Test
    void enviaNotificacionYAcuseMultipartAlternative() throws Exception {
        given(mailSender.createMimeMessage()).willAnswer(invocation -> createMime());
        stubRenderer();
        service(true).save(request(), "192.168.1.1", "curl-test", Locale.forLanguageTag("es"));

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, timeout(2000).times(2)).send(captor.capture());

        verify(templateRenderer).renderContactHtml(any(), any());
        verify(templateRenderer).renderAckHtml(any(), any());

        assertThat(captor.getAllValues()).hasSize(2);
        for (MimeMessage sent : captor.getAllValues()) {
            sent.saveChanges();
            assertThat(sent.getContentType()).contains("multipart/alternative");
        }

        MimeMessage notification = findByRecipient(captor.getAllValues(), "to@pepe.dev");
        MimeMessage ack = findByRecipient(captor.getAllValues(), "ana@example.es");
        assertThat(notification.getSubject()).isEqualTo("[Contacto] Consulta");
        assertThat(ack.getSubject()).isEqualTo("Hemos recibido tu mensaje");
    }

    @Test
    void noEnviaAcuseSiSendAckEsFalse() throws Exception {
        given(mailSender.createMimeMessage()).willAnswer(invocation -> createMime());
        stubRenderer();
        service(false).save(request(), "192.168.1.1", "curl-test", Locale.forLanguageTag("es"));

        verify(templateRenderer, never()).renderAckHtml(any(), any());
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, timeout(2000)).send(captor.capture());
        assertThat(captor.getAllValues()).hasSize(1);
        assertThat(captor.getValue().getSubject()).isEqualTo("[Contacto] Consulta");
    }

    @Test
    void acuseUsaInglesConLocaleEn() throws Exception {
        given(mailSender.createMimeMessage()).willAnswer(invocation -> createMime());
        stubRenderer();
        given(messageSource.getMessage(eq("mail.ack.subject"), isNull(), eq(Locale.ENGLISH)))
                .willReturn("We've received your message");

        service(true).save(request(), "192.168.1.1", "curl-test", Locale.ENGLISH);

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, timeout(2000).times(2)).send(captor.capture());
        MimeMessage ack = findByRecipient(captor.getAllValues(), "ana@example.es");
        assertThat(ack.getSubject()).isEqualTo("We've received your message");
    }

    @Test
    void noEnviaSiFaltanLasDireccionesDeConfiguracion() {
        ContactService sinDestino =
                new ContactService(messageRepository, mailSender, templateRenderer, messageSource,
                        "", "from@pepe.dev", true);
        ContactService sinRemitente =
                new ContactService(messageRepository, mailSender, templateRenderer, messageSource,
                        "to@pepe.dev", "", true);

        sinDestino.save(request(), "192.168.1.1", "curl-test", Locale.forLanguageTag("es"));
        sinRemitente.save(request(), "192.168.1.1", "curl-test", Locale.forLanguageTag("es"));

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    private MimeMessage findByRecipient(java.util.List<MimeMessage> messages, String address) throws Exception {
        return messages.stream()
                .filter(mime -> {
                    try {
                        return recipient(mime).equals(address);
                    } catch (Exception e) {
                        return false;
                    }
                })
                .findFirst()
                .orElseThrow();
    }

    private String recipient(MimeMessage mime) throws Exception {
        InternetAddress[] to = (InternetAddress[]) mime.getRecipients(MimeMessage.RecipientType.TO);
        return to[0].getAddress();
    }

    @Test
    void smtpCaidoDejaRastroEnElLogYElMensajeQuedaGuardado() throws Exception {
        stubRenderer();
        given(mailSender.createMimeMessage())
                .willAnswer(inv -> createMime());
        doThrow(new MailSendException("Mail server connection failed"))
                .when(mailSender).send(any(MimeMessage.class));

        // Ni la notificacion al admin ni el acuse al visitante pueden fallar en silencio.
        try (LogCapture log = LogCapture.de(ContactService.class)) {
            service(true).save(request(), "192.168.1.1", "curl-test", Locale.forLanguageTag("es"));
            verify(messageRepository).save(any());
            assertThat(log.eventuallyErrorWith(new MailSendException("Mail server connection failed"))).isTrue();
        }
    }
}