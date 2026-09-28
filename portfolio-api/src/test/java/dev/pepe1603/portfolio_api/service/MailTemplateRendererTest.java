package dev.pepe1603.portfolio_api.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
        renderer = new MailTemplateRenderer(engine, new MailStyleInliner());
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

    private Map<String, Object> otpVariables(String code) {
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("code", code);
        vars.put("minutes", 5L);
        return vars;
    }

    @Test
    void otpHtmlMuestraElCodigoYElVigenciaEnEspanol() {
        String html = renderer.renderOtpHtml(otpVariables("123456"), Locale.forLanguageTag("es"));

        assertThat(html)
                .contains("Tu código de acceso")
                .contains("123456")
                .contains("caduca en 5 minutos")
                .doesNotContain("Your access code");
    }

    @Test
    void otpHtmlRenderizaEnInglesConLocaleEn() {
        String html = renderer.renderOtpHtml(otpVariables("123456"), Locale.forLanguageTag("en"));

        assertThat(html)
                .contains("Your access code")
                .contains("expires in 5 minutes")
                .contains("123456")
                .doesNotContain("Tu código de acceso");
    }

    private static final String URL_RESET = "https://pepe.dev/reset?token=abc123";

    private static final Locale ESPANOL = Locale.forLanguageTag("es");

    private static final List<String> CLASES_COMPARTIDAS = List.of(
            "body", "page", "card", "card__header", "card__title", "card__body", "card__footer");

    /** Los cuatro correos tal y como se renderizan, en español. */
    private List<String> correos() {
        return List.of(
                renderer.renderContactHtml(variables(), ESPANOL),
                renderer.renderAckHtml(variables(), ESPANOL),
                renderer.renderResetHtml(resetVariables(URL_RESET), ESPANOL),
                renderer.renderOtpHtml(otpVariables("123456"), ESPANOL));
    }

    /** El correo sin el bloque {@code <style>}, que es la parte que llega al cliente de correo. */
    private String cuerpo(String html) {
        return html.replaceAll("(?s)<style.*?</style>", "");
    }

    @Test
    void lasClasesDeLasPlantillasSeExpandenAEstilosEnLinea() {
        for (String html : correos()) {
            assertThat(cuerpo(html)).doesNotContain("class=\"");
        }
    }

    @Test
    void losEstilosEnLineaConservanLosValoresOriginales() {
        for (String html : correos()) {
            assertThat(cuerpo(html))
                    .contains("<body style=\"margin:0;padding:0;background-color:#f4f4f5;\">")
                    .contains("style=\"background-color:#f4f4f5;padding:24px 12px;\"")
                    .contains("style=\"max-width:600px;width:100%;background-color:#ffffff;"
                            + "border-radius:12px;overflow:hidden;border:1px solid #e4e4e7;\"")
                    .contains("style=\"background-color:#18181b;color:#ffffff;padding:24px 28px;"
                            + "font-family:Arial,Helvetica,sans-serif;\"")
                    .contains("style=\"margin:0;font-size:20px;font-weight:600;\"")
                    .contains("style=\"padding:28px;font-family:Arial,Helvetica,sans-serif;color:#27272a;"
                            + "font-size:14px;line-height:1.6;")
                    .contains("style=\"background-color:#fafafa;border-top:1px solid #e4e4e7;"
                            + "padding:14px 28px;font-family:Arial,Helvetica,sans-serif;font-size:12px;"
                            + "color:#71717a;\"");
        }
    }

    @Test
    void elBloqueStyleSeConservaParaLosClientesQueLoSoportan() {
        for (String html : correos()) {
            assertThat(html)
                    .contains("<style>")
                    .contains("</style>")
                    .contains(".card__header {");
        }
    }

    @Test
    void lasClasesCompartidasCoincidenEnLasCuatroPlantillas() {
        Map<String, String> referencia = null;
        for (String html : correos()) {
            Map<String, String> reglas = new LinkedHashMap<>();
            for (String clase : CLASES_COMPARTIDAS) {
                Matcher matcher = Pattern.compile("\\." + clase + "\\s*\\{([^}]*)}").matcher(html);
                assertThat(matcher.find()).as("falta la clase .%s en la plantilla", clase).isTrue();
                reglas.put(clase, matcher.group(1).replaceAll("\\s+", " ").trim());
            }
            if (referencia == null) {
                referencia = reglas;
            } else {
                assertThat(reglas)
                        .as("las clases compartidas han divergido entre plantillas")
                        .isEqualTo(referencia);
            }
        }
    }

    @Test
    void elEnlaceDeResetInLineaElEstiloDelBoton() {
        String html = renderer.renderResetHtml(resetVariables(URL_RESET), ESPANOL);

        assertThat(cuerpo(html))
                .contains(URL_RESET)
                .contains("style=\"display:inline-block;background-color:#18181b;color:#ffffff;"
                        + "text-decoration:none;font-weight:600;padding:12px 24px;border-radius:8px;\"");
    }

    @Test
    void elCodigoDeOtpMantieneColorYEspaciado() {
        String html = renderer.renderOtpHtml(otpVariables("123456"), ESPANOL);

        assertThat(cuerpo(html))
                .contains("style=\"margin:0 0 16px;font-size:32px;letter-spacing:8px;font-weight:700;"
                        + "color:#18181b;\">123456")
                .contains("style=\"padding:28px;font-family:Arial,Helvetica,sans-serif;color:#27272a;"
                        + "font-size:14px;line-height:1.6;text-align:center;\"");
    }

    @Test
    void elEscapadoYElInlinerConvivenEnElCorreoDeContacto() {
        Map<String, Object> vars = variables();
        vars.put("subject", "<script>alert(1)</script>");

        String html = renderer.renderContactHtml(vars, ESPANOL);

        assertThat(cuerpo(html))
                .contains("&lt;script&gt;alert(1)&lt;/script&gt;")
                .doesNotContain("<script>")
                .contains("style=\"white-space:pre-wrap;background-color:#fafafa;border:1px solid #e4e4e7;"
                        + "border-radius:8px;padding:14px;\"")
                .contains("style=\"color:#2563eb;\"");
    }
}