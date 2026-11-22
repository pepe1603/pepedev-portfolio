package dev.pepe1603.api.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Único punto de la aplicación que habla con el servidor SMTP.
 *
 * <p>Existe para que el resto de servicios no repitan el armado del {@code MimeMultipart}, que era
 * idéntico en los cuatro, ni el {@code try/catch} que traga el fallo. Aquí hay una sola versión de
 * las dos cosas, y por lo tanto un solo sitio donde arreglar un fallo de correo.
 *
 * <p>Todos los correos de la API son <em>best-effort</em>: nunca deben tumbar la petición que los
 * disparó. El fallo de transporte llega como {@code MailSendException}, que es unchecked (el
 * {@code MessagingException} de la firma solo cubre el armado del mensaje), así que se captura
 * {@code Exception} y no solo la checked.
 *
 * <p>Ningún envío sale del hilo que atiende la petición. Hablar con un servidor SMTP es una llamada
 * de red bloqueante y lenta, y lo que hace la API no tiene por qué esperar: el usuario va a leer el
 * correo dentro de su bandeja de todos modos, así que alargar la respuesta solo le ralentiza la web.
 * Por eso el envío va a un hilo propio, y no al {@code commonPool} de {@code CompletableFuture}: ese
 * pool es compartido con el resto de la aplicación y se queda sin hilos si el SMTP se atasca.
 *
 * <p>Un único hilo basta con creciento, y por lo que hace es tamiza correos que casi nadie va a
 * extrañar: decenas al día como mucho. La cola está acotada para que un SMTP colgado no acabe
 * comiéndose la memoria. Si se llena, el correo se descarta dejando rastro en el log, en lugar de
 * bloquear al usuario que está initiating la operación.
 */
@Service
public class MailService {

    private static final Logger LOG = LoggerFactory.getLogger(MailService.class);
    private static final int COLA_MAXIMA = 100;
    private static final int ESPERA_APAGADO_SEGUNDOS = 5;

    private final JavaMailSender mailSender;
    private final String fromEmail;
    private final ThreadPoolExecutor executor;

    /**
     * El {@code @Autowired} no es opcional: hay un segundo constructor para poder testear la
     * saturación de la cola sin llenarla de 100 correos, y con dos constructores Spring deja
     * de elegir el único que puede resolver solo, busca uno por defecto y falla al arrancar
     * con "No default constructor found". Los tests no lo detectan porque instancian la
     * clase directamente y en el proyecto no hay ningún test que levante el contexto entero.
     */
    @Autowired
    public MailService(JavaMailSender mailSender, @Value("${APP_CONTACT_FROM_EMAIL:}") String fromEmail) {
        this(mailSender, fromEmail, executorDeCorreo());
    }

    /** Visible para testear la saturación de la cola sin tener que llenarla de 100 correos. */
    MailService(JavaMailSender mailSender, String fromEmail, ThreadPoolExecutor executor) {
        this.mailSender = mailSender;
        this.fromEmail = fromEmail;
        this.executor = executor;
    }

    private static ThreadPoolExecutor executorDeCorreo() {
        return new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(COLA_MAXIMA),
                hiloDeCorreo(), new ThreadPoolExecutor.AbortPolicy());
    }

    /**
     * Hilo daemon con nombre reconocible, para que un volcado de hilos diga de inmediato que ese
     * "correo-3" lleva veinte segundos atascado hablando con el SMTP.
     */
    private static ThreadFactory hiloDeCorreo() {
        AtomicInteger contador = new AtomicInteger();
        return tarea -> {
            Thread hilo = new Thread(tarea, "correo-" + contador.incrementAndGet());
            // Daemon: un correo a medio enviar no puede impedir que la JVM termine.
            hilo.setDaemon(true);
            return hilo;
        };
    }

    /**
     * Sin remitente configurado no se puede enviar nada. Quien llama lo consulta antes de crear
     * tokens o challenges, para no generar credenciales que nadie va a recibir por correo.
     */
    public boolean canSend() {
        return fromEmail != null && !fromEmail.isBlank();
    }

    /** Encola el envío y vuelve. El fallo se registra y nunca se propaga a la petición. */
    public void sendAsync(String to, String subject, String plain, String html) {
        sendAsync(to, null, subject, plain, html);
    }

    public void sendAsync(String to, String replyTo, String subject, String plain, String html) {
        if (!canSend()) {
            return;
        }
        try {
            executor.execute(() -> {
                try {
                    deliver(to, replyTo, subject, plain, html);
                } catch (Exception e) {
                    logFailure(subject, to, e);
                }
            });
        } catch (RejectedExecutionException e) {
            // Cola llena. Descartar el correo nuevo es preferible aRunnableRunsPolicy, que
            // ejecutaría el envío en el hilo del usuario y devolvería el problema de bloqueo.
            LOG.warn("Cola de correo llena ({} en espera): se descarta '{}' a {}. El usuario no se entera.",
                    executor.getQueue().size(), subject, to);
        }
    }

    /**
     * La alternativa texto + HTML. El texto plano no es opcional: es lo que ven los clientes de
     * correo que no renderizan HTML, y evita que un aviso de acceso nuevo acabe en la carpeta de
     * spam por ser una imagen con texto dentro.
     */
    private void deliver(String to, String replyTo, String subject, String plain, String html)
            throws MessagingException {
        MimeMessage mime = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mime, "UTF-8");
        helper.setFrom(fromEmail);
        helper.setTo(to);
        if (replyTo != null) {
            helper.setReplyTo(replyTo);
        }
        helper.setSubject(subject);
        MimeMultipart alternative = new MimeMultipart("alternative");
        MimeBodyPart textPart = new MimeBodyPart();
        textPart.setText(plain, "UTF-8");
        MimeBodyPart htmlPart = new MimeBodyPart();
        htmlPart.setContent(html, "text/html; charset=UTF-8");
        alternative.addBodyPart(textPart);
        alternative.addBodyPart(htmlPart);
        mime.setContent(alternative);
        mailSender.send(mime);
    }

    private void logFailure(String subject, String to, Exception e) {
        LOG.error("No se pudo enviar el correo '{}' a {}", subject, to, e);
    }

    /**
     * Al apagar, deja terminar lo que ya está en vuelo en vez de cortarlo a mitad.
     */
    @PreDestroy
    void apagar() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(ESPERA_APAGADO_SEGUNDOS, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
