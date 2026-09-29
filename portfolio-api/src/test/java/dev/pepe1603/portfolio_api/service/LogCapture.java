package dev.pepe1603.portfolio_api.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import java.time.Duration;
import org.slf4j.LoggerFactory;

/**
 * Captura lo que un servicio escribe en el log. Necesario para los correos que se mandan en un
 * {@code CompletableFuture}: ahí un fallo de SMTP no rompe nada, simplemente se pierde en el pool,
 * y la única señal de que algo fue mal es la linea de log. Sin esta comprobación, "no rompe" y
 * "falló en silencio" son indistinguibles.
 */
final class LogCapture implements AutoCloseable {

    private static final Duration ESPERA = Duration.ofSeconds(3);

    private final Logger logger;
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    private LogCapture(Class<?> tipo) {
        logger = (Logger) LoggerFactory.getLogger(tipo);
        appender.start();
        logger.addAppender(appender);
    }

    static LogCapture de(Class<?> tipo) {
        return new LogCapture(tipo);
    }

    /**
     * Espera activa: los envíos son asíncronos, así que la linea aparece después de la aserción.
     */
    boolean eventuallyErrorWith(Throwable causa) {
        long limite = System.nanoTime() + ESPERA.toNanos();
        while (System.nanoTime() < limite) {
            for (ILoggingEvent evento : appender.list) {
                if (evento.getLevel() == Level.ERROR && causaEn(evento, causa)) {
                    return true;
                }
            }
            dormir();
        }
        return false;
    }

    private boolean causaEn(ILoggingEvent evento, Throwable causa) {
        Throwable registrado = evento.getThrowableProxy() instanceof ThrowableProxy proxy
                ? proxy.getThrowable()
                : null;
        for (Throwable actual = registrado; actual != null; actual = actual.getCause()) {
            if (actual.getClass() == causa.getClass() && actual.getMessage() != null
                    && actual.getMessage().equals(causa.getMessage())) {
                return true;
            }
        }
        return false;
    }

    private void dormir() {
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() {
        logger.detachAppender(appender);
        appender.stop();
    }
}
