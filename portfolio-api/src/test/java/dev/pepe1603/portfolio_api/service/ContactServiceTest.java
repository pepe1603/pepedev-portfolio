package dev.pepe1603.portfolio_api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import dev.pepe1603.portfolio_api.dto.contact.ContactRequest;
import dev.pepe1603.portfolio_api.repository.MessageRepository;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;

class ContactServiceTest {

    private final MessageRepository messageRepository = mock(MessageRepository.class);
    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private final MailTemplateRenderer templateRenderer = mock(MailTemplateRenderer.class);
    private final ContactService service =
            new ContactService(messageRepository, mailSender, templateRenderer, "from@pepe.dev", "to@pepe.dev");

    private ContactRequest request() {
        return new ContactRequest("Ana García", "ana@example.es", "Consulta", "Hola,\nquiero saber más.", "");
    }

    @Test
    void enviaMultipartAlternativeConTextoPlanoYHtml() throws Exception {
        given(templateRenderer.renderContactHtml(any())).willReturn("<html><body>HTML CONTACTO</body></html>");
        MimeMessage mime = new MimeMessage(Session.getInstance(new Properties()));
        given(mailSender.createMimeMessage()).willReturn(mime);

        service.save(request(), "192.168.1.1", "curl-test");

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, timeout(2000)).send(captor.capture());
        MimeMessage sent = captor.getValue();
        sent.saveChanges();

        assertThat(sent.getContentType()).contains("multipart/alternative");
        Multipart multipart = (Multipart) sent.getContent();
        Object plain = multipart.getBodyPart(0).getContent();
        Object html = multipart.getBodyPart(1).getContent();
        assertThat(plain)
                .isInstanceOf(String.class)
                .asString()
                .contains("De: Ana García <ana@example.es>")
                .contains("Hola,\nquiero saber más.");
        assertThat(html).isInstanceOf(String.class).asString().contains("<html><body>HTML CONTACTO</body></html>");
        verify(templateRenderer).renderContactHtml(any());
    }

    @Test
    void noEnviaSiFaltanLasDireccionesDeConfiguracion() {
        ContactService sinDestino =
                new ContactService(messageRepository, mailSender, templateRenderer, "from@pepe.dev", "");
        ContactService sinRemitente =
                new ContactService(messageRepository, mailSender, templateRenderer, "", "to@pepe.dev");

        sinDestino.save(request(), "192.168.1.1", "curl-test");
        sinRemitente.save(request(), "192.168.1.1", "curl-test");

        verify(mailSender, never()).send(any(MimeMessage.class));
    }
}