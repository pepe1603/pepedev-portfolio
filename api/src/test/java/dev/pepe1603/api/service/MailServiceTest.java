package dev.pepe1603.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;

class MailServiceTest {

    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private final List<MailService> creados = new ArrayList<>();

    @AfterEach
    void apagarLosHilos() {
        creados.forEach(MailService::apagar);
    }

    /** El de producción: hilo propio con nombre y cola de 100. */
    private MailService servicioReal() {
        return registra(new MailService(mailSender, "from@pepe.dev"));
    }

    /**
     * Un hilo y dos huecos, para saturarlo en el cuarto correo en vez de en el centésimo primero.
     * La factoría de hilos es la de aquí, no la de producción: lo que se prueba aquí es la
     * saturación, no el nombre del hilo.
     */
    private MailService servicioEstrecho() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(2));
        return registra(new MailService(mailSender, "from@pepe.dev", executor));
    }

    private MailService registra(MailService servicio) {
        creados.add(servicio);
        return servicio;
    }

    @Test
    void elEnvioNoOcurreEnElHiloQueLoPide() {
        AtomicReference<String> hiloDelEnvio = new AtomicReference<>();
        dadoQueSePuedenCrearMensajes();
        doAnswer(invocation -> {
            hiloDelEnvio.set(Thread.currentThread().getName());
            return null;
        }).when(mailSender).send(any(MimeMessage.class));

        servicioReal().sendAsync("a@pepe.dev", "Asunto", "texto", "<html></html>");

        verify(mailSender, after(2000)).send(any(MimeMessage.class));
        // Ni el hilo del test ni el commonPool: el hilo propio de correo, y con nombre reconocible
        // para que un volcado de hilos diga enseguida cuál lleva veinte segundos atascado.
        assertThat(hiloDelEnvio.get()).startsWith("correo-");
    }

    @Test
    void colaLlenaDescartaElCorreoYLoDejaEnElLog() {
        // SMTP que no contesta nunca: es lo que llena la cola.
        CountDownLatch atascado = new CountDownLatch(1);
        dadoQueSePuedenCrearMensajes();
        doAnswer(invocation -> {
            atascado.await(5, TimeUnit.SECONDS);
            return null;
        }).when(mailSender).send(any(MimeMessage.class));
        MailService servicio = servicioEstrecho();

        try (LogCapture log = LogCapture.de(MailService.class)) {
            for (int i = 1; i <= 3; i++) {
                servicio.sendAsync("a@pepe.dev", "Correo " + i, "texto", "<html></html>");
            }
            // El cuarto no cabe: uno en vuelo y dos en la cola.
            servicio.sendAsync("a@pepe.dev", "Correo descartado", "texto", "<html></html>");

            // Lo importante no es que se descarte, sino que la petición del usuario no se quede
            // esperando al SMTP atascado. Con CallerRunsPolicy esto sería un bloqueo de 5 s.
            assertThat(log.eventuallyWarn("Cola de correo llena")).isTrue();
            assertThat(log.eventuallyWarn("Correo descartado")).isTrue();
        } finally {
            atascado.countDown();
        }
    }

    @Test
    void sinRemitenteConfiguradoNoSeEncolaNiSeIntentaEnviar() {
        MailService sinRemitente = registra(new MailService(mailSender, "  "));

        assertThat(sinRemitente.canSend()).isFalse();
        sinRemitente.sendAsync("a@pepe.dev", "Asunto", "texto", "<html></html>");

        // after() y no never() a secas: si el guard se rompiera, la tarea async llegaría al mock un
        // instante después y la comprobación pasaría sin querer.
        verify(mailSender, after(200).never()).send(any(MimeMessage.class));
    }

    @Test
    void laColaDeProduccionAguantaUnaRacadaDeCorreos() {
        List<String> destinatarios = new CopyOnWriteArrayList<>();
        dadoQueSePuedenCrearMensajes();
        doAnswer(invocation -> {
            MimeMessage mensaje = invocation.getArgument(0);
            mensaje.saveChanges();
            destinatarios.add(mensaje.getAllRecipients()[0].toString());
            return null;
        }).when(mailSender).send(any(MimeMessage.class));

        for (int i = 0; i < 5; i++) {
            servicioReal().sendAsync("destinatario" + i + "@pepe.dev", "Asunto", "texto", "<html></html>");
        }

        esperaA(() -> destinatarios.size() == 5);
        assertThat(destinatarios).containsExactlyInAnyOrder("destinatario0@pepe.dev", "destinatario1@pepe.dev",
                "destinatario2@pepe.dev", "destinatario3@pepe.dev", "destinatario4@pepe.dev");
    }

    @Test
    void apagarEsperaAlCorreoQueEstaEnVueloEnLugarDeCortarlo() {
        AtomicBoolean enCurso = new AtomicBoolean();
        AtomicBoolean entregado = new AtomicBoolean();
        dadoQueSePuedenCrearMensajes();
        // El correo tarda un poco en salir. Lo que se comprueba es que apagar() lo espere en vez de
        // interrumpirlo: si el apagado fuera un shutdownNow() inmediato, entregado se quedaría en
        // false y el correo se perdería a medio camino en cada despliegue.
        doAnswer(invocation -> {
            enCurso.set(true);
            Thread.sleep(300);
            entregado.set(true);
            return null;
        }).when(mailSender).send(any(MimeMessage.class));
        MailService servicio = servicioReal();

        servicio.sendAsync("a@pepe.dev", "Asunto", "texto", "<html></html>");
        esperaA(enCurso::get);

        servicio.apagar();

        assertThat(entregado.get()).isTrue();
    }

    private void esperaA(java.util.function.BooleanSupplier condicion) {
        esperaA(condicion, 5);
    }

    private void esperaA(java.util.function.BooleanSupplier condicion, int segundos) {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(segundos);
        while (!condicion.getAsBoolean() && System.nanoTime() < limite) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void dadoQueSePuedenCrearMensajes() {
        try {
            given(mailSender.createMimeMessage())
                    .willAnswer(invocation -> new MimeMessage(Session.getInstance(new Properties())));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
