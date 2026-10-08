package dev.pepe1603.portfolio_api.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Expande las clases CSS declaradas en {@code <head><style>} de las plantillas de correo hacia
 * estilos en línea ({@code style="..."}) en el HTML ya renderizado.
 *
 * <p>Por qué hace falta: los clientes de correo ignoran la hoja de estilos de la cabecera, así que
 * un correo con las reglas solo en {@code <head><style>} llegaría sin estilos. Al revés también
 * pasa: 25 líneas de {@code style="..."} repetidas en cada etiqueta hacen la plantilla ilegible. El
 * punto medio es usar clases como fuente de verdad y expandirlas aquí, en el envío.
 *
 * <p>Alcance deliberado y pequeño:
 * <ul>
 *   <li>Solo selectores de clase simples ({@code .nombre}) a nivel superior. Los selectores de
 *       elemento, de id o con pseudo-clases se ignoran: no se pueden expandir a un atributo.</li>
 *   <li>Los bloques {@code @media} (u otras reglas con llaves anidadas) no se expanden, pero se
 *       dejan en el HTML: solo afectan a los clientes que sí soportan CSS de cabecera.</li>
 *   <li>El bloque {@code <style>} se conserva en el correo, de modo que los clientes que lo
 *       soportan (Apple Mail, webmail) mantienen también las reglas que no se pueden inlinear.</li>
 *   <li>Una clase sin regla no es un error: se deja en la etiqueta y se avisa por log, para que el
 *       correo salga igual y el fallo quede visible en los logs.</li>
 * </ul>
 *
 * <p>Cuando hay varias clases, se respeta el orden de la atributo: la última gana en las
 * propiedades en conflicto, igual que en CSS.
 */
@Component
public class MailStyleInliner {

    private static final Logger LOG = LoggerFactory.getLogger(MailStyleInliner.class);

    private static final Pattern STYLE_BLOCK = Pattern.compile("<style[^>]*>(.*?)</style>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final Pattern TAG = Pattern.compile("<([a-zA-Z][a-zA-Z0-9:_-]*)((?:\"[^\"]*\"|[^<>])*)>");

    private static final Pattern CLASS_ATTRIBUTE = Pattern.compile("\\sclass\\s*=\\s*\"([^\"]*)\"");

    private static final Pattern STYLE_ATTRIBUTE = Pattern.compile("\\sstyle\\s*=\\s*\"([^\"]*)\"");

    private static final Pattern CLASS_SELECTOR = Pattern.compile("^\\.([A-Za-z_][A-Za-z0-9_-]*)$");

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private static final Pattern DECLARATION_SEPARATOR = Pattern.compile(";\\s*");

    /**
     * Devuelve el HTML con las clases resueltas a estilos en línea. Si la plantilla no declara
     * ninguna clase, devuelve el HTML sin tocar.
     */
    public String inline(String html) {
        Map<String, String> rules = reglas(html);
        if (rules.isEmpty()) {
            return html;
        }
        Matcher tags = TAG.matcher(html);
        StringBuffer out = new StringBuffer();
        while (tags.find()) {
            tags.appendReplacement(out, Matcher.quoteReplacement(resolverEtiqueta(tags.group(0), rules)));
        }
        tags.appendTail(out);
        return out.toString();
    }

    private Map<String, String> reglas(String html) {
        Map<String, String> rules = new LinkedHashMap<>();
        Matcher blocks = STYLE_BLOCK.matcher(html);
        while (blocks.find()) {
            rules.putAll(parseRules(blocks.group(1)));
        }
        return rules;
    }

    private Map<String, String> parseRules(String css) {
        Map<String, String> rules = new LinkedHashMap<>();
        int cursor = 0;
        while (cursor < css.length()) {
            int open = css.indexOf('{', cursor);
            if (open < 0) {
                break;
            }
            String selector = css.substring(cursor, open).trim();
            int close = closingBrace(css, open);
            if (!selector.startsWith("@")) {
                for (String part : selector.split(",")) {
                    Matcher matcher = CLASS_SELECTOR.matcher(part.trim());
                    if (matcher.matches()) {
                        rules.put(matcher.group(1), declarations(css.substring(open + 1, close)));
                    }
                }
            }
            cursor = close + 1;
        }
        return rules;
    }

    private int closingBrace(String css, int open) {
        int depth = 0;
        for (int i = open; i < css.length(); i++) {
            char current = css.charAt(i);
            if (current == '{') {
                depth++;
            } else if (current == '}' && --depth == 0) {
                return i;
            }
        }
        return css.length();
    }

    private String declarations(String body) {
        String normalized = WHITESPACE.matcher(body).replaceAll(" ").trim();
        normalized = DECLARATION_SEPARATOR.matcher(normalized).replaceAll(";");
        if (normalized.isEmpty()) {
            return "";
        }
        return normalized.endsWith(";") ? normalized : normalized + ";";
    }

    private String resolverEtiqueta(String tag, Map<String, String> rules) {
        int space = tag.indexOf(' ');
        int end = tag.length() - 1;
        if (space < 0 || end <= space + 1) {
            return tag;
        }
        String name = tag.substring(1, space);
        // Se conserva el espacio inicial: los patrones de atributo lo exigen como separador.
        String attributes = tag.substring(space, end);
        boolean selfClosing = attributes.endsWith("/");
        if (selfClosing) {
            attributes = attributes.substring(0, attributes.length() - 1);
        }

        Matcher classAttribute = CLASS_ATTRIBUTE.matcher(attributes);
        if (!classAttribute.find()) {
            return tag;
        }

        List<String> resolved = new ArrayList<>();
        Set<String> unresolved = new LinkedHashSet<>();
        for (String className : classAttribute.group(1).trim().split("\\s+")) {
            if (className.isEmpty()) {
                continue;
            }
            String declarations = rules.get(className);
            if (declarations == null) {
                unresolved.add(className);
                LOG.warn("La clase .{} no está definida en el <style> de la plantilla de correo", className);
            } else if (!declarations.isEmpty()) {
                resolved.add(declarations);
            }
        }

        String style = String.join("", resolved);
        Matcher existingStyle = STYLE_ATTRIBUTE.matcher(attributes);
        boolean hasInlineStyle = existingStyle.find();

        if (!hasInlineStyle && unresolved.isEmpty()) {
            // Caso habitual: la etiqueta solo lleva clases, así que se sustituye el atributo en su
            // sitio y el resto de atributos (role, width, align...) conservan el orden de siempre.
            return CLASS_ATTRIBUTE.matcher(tag)
                    .replaceAll(Matcher.quoteReplacement(" style=\"" + style + "\""));
        }

        if (hasInlineStyle) {
            // El estilo de la etiqueta manda sobre la clase, como en CSS: se coloca al final.
            String inline = existingStyle.group(1);
            if (!inline.isBlank()) {
                style = style.isEmpty() ? inline : style + (inline.endsWith(";") ? inline : inline + ";");
            }
        }
        String rest = STYLE_ATTRIBUTE.matcher(CLASS_ATTRIBUTE.matcher(attributes).replaceAll(""))
                .replaceAll("")
                .trim();

        StringBuilder out = new StringBuilder("<").append(name);
        if (!unresolved.isEmpty()) {
            out.append(" class=\"").append(String.join(" ", unresolved)).append('"');
        }
        if (!style.isEmpty()) {
            out.append(" style=\"").append(style).append('"');
        }
        if (!rest.isEmpty()) {
            out.append(' ').append(rest);
        }
        if (selfClosing) {
            out.append('/');
        }
        return out.append('>').toString();
    }
}
