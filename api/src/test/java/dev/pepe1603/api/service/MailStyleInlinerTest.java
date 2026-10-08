package dev.pepe1603.api.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MailStyleInlinerTest {

    private final MailStyleInliner inliner = new MailStyleInliner();

    private String html(String styles, String body) {
        return "<html><head><style>" + styles + "</style></head><body>" + body + "</body></html>";
    }

    @Test
    void convierteLasClasesEnEstilosEnLinea() {
        String result = inliner.inline(html(".card { color:#27272a; font-size:14px; }",
                "<table class=\"card\"><tr><td>Hola</td></tr></table>"));

        assertThat(result)
                .contains("<table style=\"color:#27272a;font-size:14px;\">")
                .doesNotContain("class=");
    }

    @Test
    void variasClasesSeAplicanEnOrdenYLaUltimaGana() {
        String result = inliner.inline(html(".p { margin:0 0 12px; } .code { color:#18181b; margin:0; }",
                "<p class=\"p code\">123456</p>"));

        assertThat(result).contains("<p style=\"margin:0 0 12px;color:#18181b;margin:0;\">");
    }

    @Test
    void conservaElBloqueDeEstilosParaLosClientesQueLoSoportan() {
        String result = inliner.inline(html(".card { color:#27272a; }", "<td class=\"card\"></td>"));

        assertThat(result)
                .contains("<style>.card { color:#27272a; }</style>")
                .contains("style=\"color:#27272a;\"");
    }

    @Test
    void elEstiloDeLaEtiquetaMandaSobreLaClase() {
        String result = inliner.inline(html(".link { color:#2563eb; }",
                "<a class=\"link\" style=\"color:#111111;\">ana@example.es</a>"));

        assertThat(result).contains("style=\"color:#2563eb;color:#111111;\"");
    }

    @Test
    void unaClaseSinReglaSeMantieneYNoRompeElCorreo() {
        String result = inliner.inline(html(".card { color:#27272a; }",
                "<td class=\"card huerfana\"></td>"));

        assertThat(result)
                .contains("<td class=\"huerfana\" style=\"color:#27272a;\">");
    }

    @Test
    void unaClaseDesconocidaSeQuedaComoEstaba() {
        String result = inliner.inline(html(".card { color:#27272a; }", "<td class=\"huerfana\"></td>"));

        assertThat(result).contains("<td class=\"huerfana\">");
    }

    @Test
    void noExpandeLasReglasDentroDeMediaQuery() {
        String styles = ".p { margin:0 0 12px; } @media only screen and (max-width:600px) { .p { margin:0; } }";
        String result = inliner.inline(html(styles, "<p class=\"p\">Hola</p>"));

        assertThat(result)
                .contains("<p style=\"margin:0 0 12px;\">")
                .contains("@media only screen and (max-width:600px) { .p { margin:0; } }");
    }

    @Test
    void ignoraLosSelectoresQueNoSonDeClase() {
        String styles = "td { padding:28px; } #card { color:#000000; } .card:hover { color:#ff0000; } .card { color:#27272a; }";
        String result = inliner.inline(html(styles, "<td class=\"card\">Hola</td>"));

        assertThat(result).contains("<td style=\"color:#27272a;\">Hola</td>");
    }

    @Test
    void sinClasesNoTocaElHtml() {
        String original = "<html><body><p style=\"color:#27272a;\">Hola</p></body></html>";

        assertThat(inliner.inline(original)).isEqualTo(original);
        assertThat(inliner.inline("<p class=\"card\">Hola</p>")).isEqualTo("<p class=\"card\">Hola</p>");
    }

    @Test
    void mantieneLosAtributosPresentacionalesDelCorreo() {
        String result = inliner.inline(html(".card { color:#27272a; }",
                "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" class=\"card\">"
                        + "<tr><td align=\"center\" class=\"card\">x</td></tr></table>"));

        assertThat(result)
                .contains("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" "
                        + "style=\"color:#27272a;\">")
                .contains("<td align=\"center\" style=\"color:#27272a;\">");
    }

    @Test
    void noTocaElTextoNiLasEtiquetasQueNoTienenClase() {
        String original = "<p>Consulta <b>urgente</b> &amp; rápida</p>";
        String result = inliner.inline(html(".p { margin:0 0 12px; }", original));

        assertThat(result).contains(original);
    }

    @Test
    void recogeLasReglasDeVariosBloquesDeEstilos() {
        String result = inliner.inline(
                "<html><head><style>.p { margin:0; }</style><style>.code { letter-spacing:8px; }</style></head>"
                        + "<body><p class=\"p code\">1</p></body></html>");

        assertThat(result).contains("style=\"margin:0;letter-spacing:8px;\"");
    }
}
