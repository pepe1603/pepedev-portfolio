package dev.pepe1603.portfolio_api.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

class MailTemplateRendererTest {

    private static MailTemplateRenderer renderer;

    @BeforeAll
    static void setUpEngine() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCacheable(true);

        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");
        messageSource.setDefaultEncoding("UTF-8");

        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        engine.setTemplateEngineMessageSource(messageSource);
        renderer = new MailTemplateRenderer(engine);
    }

    private Map<String, Object> variables() {
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("name", "Ana García");
        vars.put("email", "ana@example.es");
        vars.put("ip", "192.168.1.42");
        vars.put("fecha", "27/09/2026 09:00");
        vars.put("subject", "Consulta sobre un proyecto");
        vars.put("body", "Hola,\nquiero saber más.");
        return vars;
    }

    @Test
    void contactHtmlContieneLosCampos() {
        String html = renderer.renderContactHtml(variables(), Locale.forLanguageTag("es"));

        assertThat(html)
                .contains("Ana García")
                .contains("ana@example.es")
                .contains("192.168.1.42")
                .contains("27/09/2026 09:00")
                .contains("Consulta sobre un proyecto")
                .contains("Hola,\nquiero saber más.");
    }

    @Test
    void contactHtmlEscapaContenidoDeUsuario() {
        Map<String, Object> vars = variables();
        vars.put("name", "<b>Pepe</b>");
        vars.put("email", "<script>alert('x')</script>@x.es");
        vars.put("subject", "<i>spam</i>");
        vars.put("body", "<script>alert('pwned')</script>");

        String html = renderer.renderContactHtml(vars, Locale.forLanguageTag("es"));

        assertThat(html)
                .contains("&lt;b&gt;Pepe&lt;/b&gt;")
                .contains("&lt;script&gt;")
                .doesNotContain("<script>");
    }

    @Test
    void contactHtmlRenderizaEnEspanolPorDefecto() {
        String html = renderer.renderContactHtml(variables());

        assertThat(html)
                .contains("Nuevo mensaje de contacto")
                .contains("Recibiste un nuevo mensaje desde tu portfolio.")
                .doesNotContain("New contact message");
    }

    @Test
    void contactHtmlRenderizaEnInglesConLocaleEn() {
        String html = renderer.renderContactHtml(variables(), Locale.forLanguageTag("en"));

        assertThat(html)
                .contains("New contact message")
                .contains("You received a new message from your portfolio.")
                .contains("Ana García")
                .doesNotContain("Nuevo mensaje de contacto");
    }

    @Test
    void ackHtmlContieneLosCamposEnEspanol() {
        String html = renderer.renderAckHtml(variables(), Locale.forLanguageTag("es"));

        assertThat(html)
                .contains("Hemos recibido tu mensaje")
                .contains("Hola,")
                .contains("Ana García")
                .contains("Consulta sobre un proyecto")
                .doesNotContain("We've received your message");
    }

    @Test
    void ackHtmlRenderizaEnInglesConLocaleEn() {
        String html = renderer.renderAckHtml(variables(), Locale.forLanguageTag("en"));

        assertThat(html)
                .contains("received your message")
                .contains("Hi,")
                .contains("Ana García")
                .doesNotContain("Hemos recibido tu mensaje");
    }

    @Test
    void ackHtmlEscapaContenidoDeUsuario() {
        Map<String, Object> vars = variables();
        vars.put("name", "<b>Pepe</b>");
        vars.put("subject", "<script>alert('spam')</script>");

        String html = renderer.renderAckHtml(vars, Locale.forLanguageTag("es"));

        assertThat(html)
                .contains("&lt;b&gt;Pepe&lt;/b&gt;")
                .contains("&lt;script&gt;")
                .doesNotContain("<script>");
    }

    private Map<String, Object> resetVariables(String url) {
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("resetUrl", url);
        return vars;
    }

    @Test
    void resetHtmlIncluyeElEnlaceYElTextoEnEspanol() {
        String html = renderer.renderResetHtml(resetVariables("https://pepe.dev/reset?token=abc123"),
                Locale.forLanguageTag("es"));

        assertThat(html)
                .contains("https://pepe.dev/reset?token=abc123")
                .contains("Restablece tu contraseña")
                .contains("caduca en 30 minutos")
                .doesNotContain("Reset your password");
    }

    @Test
    void resetHtmlRenderizaEnInglesConLocaleEn() {
        String html = renderer.renderResetHtml(resetVariables("https://pepe.dev/reset?token=abc123"),
                Locale.forLanguageTag("en"));

        assertThat(html)
                .contains("Reset your password")
                .contains("single-use")
                .doesNotContain("Restablece tu contraseña");
    }

    @Test
    void resetHtmlEscapaElEnlace() {
        String html = renderer.renderResetHtml(resetVariables("https://x.es/reset?token=1&evil=\"><b>"),
                Locale.forLanguageTag("es"));

        assertThat(html)
                .contains("&lt;b&gt;")
                .doesNotContain("<b>\"");
    }
}