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
}