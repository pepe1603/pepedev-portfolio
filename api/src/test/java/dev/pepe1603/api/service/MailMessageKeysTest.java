package dev.pepe1603.api.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * Las claves que el código pide con {@code messageSource.getMessage} se escapan de la cobertura del
 * resto de tests: los servicios usan un MessageSource mockeado, y el bundle real solo se exercise
 * para lo que las plantillas referencian.
 *
 * <p>Una clave que falte no la detecta nadie hasta que falla en producción, y como el asunto se
 * compone en el hilo de la petición eso es un 500 en el login, no un correo que no llega. Este test
 * es el contrato entre el código y los dos bundles.
 */
class MailMessageKeysTest {

    private static final String PREFIJO = "mail.";

    /**
     * Solo las que se resuelven desde Java. Las que usan las plantillas las cubre
     * {@code MailTemplateRendererTest}, que las renderiza con el bundle real.
     */
    private static final List<String> CLAVES_DE_CODIGO = List.of(
            "mail.ack.subject",
            "mail.ack.confirm",
            "mail.otp.subject",
            "mail.reset.subject",
            "mail.session.login.subject",
            "mail.session.login.intro",
            "mail.session.login.fecha",
            "mail.session.login.ip",
            "mail.session.login.navegador",
            "mail.session.login.warning",
            "mail.session.logout.subject",
            "mail.session.logout.intro",
            "mail.session.logout.fecha",
            "mail.session.logout.ip",
            "mail.session.logout.navegador",
            "mail.session.logout.warning");

    @Test
    void todasLasClavesQuePideElCodigoExistenEnLosDosIdiomas() {
        for (String bundle : List.of("messages", "messages_en")) {
            Properties properties = carga(bundle);
            assertThat(CLAVES_DE_CODIGO)
                    .as("claves usadas desde el código que faltan o están vacías en %s", bundle)
                    .allSatisfy(clave -> assertThat(properties.getProperty(clave))
                            .as("%s en %s", clave, bundle)
                            .isNotBlank());
        }
    }

    @Test
    void losDosIdiomasTienenLasMismasClavesDeCorreo() {
        Set<String> es = clavesDeCorreo(carga("messages"));
        Set<String> en = clavesDeCorreo(carga("messages_en"));

        assertThat(sinPrefijo(es)).as("claves mail.* que solo existen en español").isEqualTo(sinPrefijo(en));
    }

    private Set<String> clavesDeCorreo(Properties properties) {
        Set<String> claves = new TreeSet<>();
        for (String clave : properties.stringPropertyNames()) {
            if (clave.startsWith(PREFIJO)) {
                claves.add(clave);
            }
        }
        return claves;
    }

    private Set<String> sinPrefijo(Set<String> claves) {
        Set<String> resultado = new TreeSet<>();
        for (String clave : claves) {
            resultado.add(clave.substring(PREFIJO.length()));
        }
        return resultado;
    }

    private Properties carga(String nombre) {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(nombre + ".properties")) {
            if (in == null) {
                throw new IllegalStateException("No encuentro " + nombre + ".properties en el classpath");
            }
            Properties properties = new Properties();
            properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            return properties;
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer " + nombre, e);
        }
    }
}
