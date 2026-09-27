package dev.pepe1603.portfolio_api.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.templatemode.TemplateMode;

class MailTemplateRendererTest {

    private static MailTemplateRenderer renderer;

    @BeforeAll
    static void setUpEngine() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCacheable(true);

        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
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
        String html = renderer.renderContactHtml(variables());

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

        String html = renderer.renderContactHtml(vars);

        assertThat(html)
                .contains("&lt;b&gt;Pepe&lt;/b&gt;")
                .contains("&lt;script&gt;")
                .doesNotContain("<script>");
    }
}