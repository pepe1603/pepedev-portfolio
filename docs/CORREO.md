# Correo — pepedev-portfolio

Por qué el envío de correo es como es, y qué decisiones **no** volver a abrir sin motivo nuevo.
Si llegas aquí para cambiar algo, léete primero la sección 6: la mitad de este documento son
opciones descartadas a propósito, y volver a proponerlas sin un problema nuevo es dar vueltas en círculo.

## 1. Qué envía la API

Cinco flujos, todos disparados desde el backend. Ninguno es síncrono con la respuesta que el
usuario espera.

| Flujo | Lo dispara | Destinatario | Interruptor |
|---|---|---|---|
| Contacto + acuse | `POST /contact` | Quien escribe y el admin | siempre |
| Código OTP | `POST /auth/login` | El admin que entra | `APP_AUTH_OTP_ENABLED` + `users.otp_enabled` |
| Restablecimiento | `POST /auth/reset/request` | El admin, si la cuenta existe | `APP_MAIL_SECURITY_RESET` (default `true`) |
| Acceso nuevo | Login con tokens emitidos | El propio admin | `APP_MAIL_SECURITY_LOGIN` (default `false`) |
| Cierre de sesión | `POST /auth/logout` | El propio admin | `APP_MAIL_SECURITY_LOGOUT` (default `false`) |

Todos salen por `APP_CONTACT_FROM_EMAIL`. Sin ese valor no sale nada y el arranque avisa.
Detalle de cada variable en [API.md](API.md#variables) y en `.env.example`.

## 2. La invariante: el correo nunca tumba la petición

Ningún fallo de SMTP puede cambiar el código HTTP de un endpoint. Un contacto se guarda aunque el
correo no salga; un reset genera su token aunque el correo no salga; un login devuelve sus tokens
aunque el aviso no salga.

Esto no es una preferencia de estilo. Es un bug que ya ocurrió: `/auth/reset/request` devolvía
`500` porque `JavaMailSenderImpl` lanza `MailSendException`, que es `RuntimeException`, y el
`catch` de los servicios solo cazaba `MessagingException`, la checked de la firma. Corregido en
`3defede`; los cuatro envíos capturan `Exception` a propósito.

De ahí se deriva la regla que más cuesta mantener: **el log es la única señal**. Si el envío falla
en segundo plano, el proceso no se entera, no hay métrica, no hay excepción: hay una línea de log.
Por eso `LogCapture` existe en los tests, y por qué `smtpCaido...` comprueba la línea y no la
ausencia de excepción (con envío asíncrono, «no lanzó» es cierto siempre y no prueba nada).

## 3. Por qué hay un `MailService` y no cuatro

Hasta `2262a7a`, `ContactService`, `OtpService`, `PasswordResetService` y
`SecurityNotificationService` construían cada uno su `MimeMultipart`, cada uno resolvía el
remitente, cada uno tenía su `try/catch` y su log. 585 líneas en total, de las que casi todas eran
copia: **−45 netas** al centralizar (424 en los cuatro servicios más 116 de `MailService`).

`MailService` se queda con lo que de verdad era común —remitente, MIME texto+HTML, hilo, captura del
fallo— y los servicios solo describen *qué* mandan: a quién, con qué asunto, qué texto plano y qué
plantilla. El beneficiario de verdad no es el número de líneas: es que ahora hay **un sitio** donde
arreglar un fallo de correo, y no cuatro.

## 4. Por qué el envío sale a un hilo propio

Tres decisiones, en orden de importancia:

**Fuera del hilo de la petición.** Una llamada SMTP es de red, bloqueante y lenta. El usuario va a
leer el correo dentro de su bandeja de todos modos, así que alargar la respuesta HTTP solo le
ralentiza la web. Además, con los timeouts de 10 s (`application.yaml`), un SMTP colgado podría
retener un hilo de Tomcat veinte segundos.

**Ni `CompletableFuture.runAsync` ni el pool de Tomcat.** `runAsync` usa el `commonPool` de la JVM:
compartido con el resto de la aplicación y sin límite de tareas. Con el SMTP atascado, cada envío en
vuelo tumba un hilo de ahí. Ahora hay un `ThreadPoolExecutor` propio: **un hilo, cola de 100**, con
nombres `correo-N` y `daemon=true`. Un solo hilo basta porque lo que hace es tamiza correos, decenas
al día como mucho, y encadenarlos tiene la ventaja de que se entregan en orden.

**Si la cola se llena, se descarta el correo y se deja en el log.** Aquí se descartó el default de
Spring, `CallerRunsPolicy`, a propósito: ejecuta el envío **en el hilo del propio usuario**, que es
el bloqueo que se quiere evitar. `AbortPolicy` deja pasar la petición y pierde un correo que casi
nadie va a extrañar; `CallerRunsPolicy` ralentizaría la web. Para un portfolio de un solo admin, esa
es la elección.

## 5. El asunto se compone en el hilo de la petición

Consecuencia directa de centralizar: los servicios pasan **cadenas ya resueltas** a `MailService`, así
que el asunto y el HTML se arman antes de encolar.

Es intencionado y sale barato: renderizar es local y de milisegundos (los 24 renders de
`MailTemplateRendererTest` tardan 0,8 s en total), mientras que lo único que puede tardar o fallar es
el envío. A cambio, un fallo al componer deja log en vez de evaporarse en el pool, que era
justamente el fallo silencioso que se arregló en `3defede`.

El riesgo de moverlo es que ahora una clave i18n ausente rompe un endpoint, y los tests no lo veían:
los servicios usan un `MessageSource` mockeado y el bundle real solo se ejercita para lo que las
plantillas referencian. `MailMessageKeysTest` (`159e3f5`) cierra ese hueco: comprueba que las 16
claves que el código pide existen y no están vacías en ES y EN, y que ambos bundles tienen las mismas
claves `mail.*`.

## 6. Lo que no hay, y por qué

Estas son decisiones, no despistes pendientes. La infraestructura extra sale cara en tiempo y en
dinero, y el
problema que resolverían no existe a esta escala.

| No hay | Por qué |
|---|---|
| **RabbitMQ / broker** | El proyecto ya tiene PostgreSQL y Redis. Un admin, una entrada de contacto: los picos son de un correo cada tanto, no de miles por minuto. Un broker añadiría un servicio que mantener, y encima es lo que hace que un despliegue deje de ser `git pull && restart`. |
| **Reintentos** | Nadie mira dos veces un correo. Y un reintento con backoff es exactamente la carga que satura la cola y hace que se descarten los correos de los demás. |
| **Outbox / bandeja de salida** | Es la solución correcta para «no perder el correo». Aquí el correo perdido se recupera con un botón de reenviar, que además el usuario agradece. |
| **Procesar rebotes** | Ningún correo de un portfolio rebota en volumen. El aviso de rebote sería otro correo best-effort de un correo que ya no llegó. |
| **Métricas de correo** | El log basta a esta escala. Antes de instrumentar, mira si te sirve `grep`. |
| **Plantillas en BD / motor de plantillas** | Son ficheros `.properties` con Thymeleaf, versionados con el código y traducidos a mano. Un motor de plantillas solo aporta lo que ya da el versionado en Git. |
| **Configuración por web del SMTP** | `application.yaml` y variables de entorno, que es donde se configura todo lo demás. |

**Lo que sí se pierde, y es el precio exacto:** un correo en cola en el momento de un reinicio se
pierde. Por eso el apagado espera hasta 5 s a lo que está en vuelo (`apagarDejaTerminar...` falla si
se cambia a `shutdownNow()`), y por eso existe el reenvío.

## 7. Reenvíos: dos flujos, dos respuestas

- **OTP**: `POST /auth/login/resend`. Endpoint propio, porque el challenge va por `challengeId` y no
  por email. Genera un **código nuevo** (el store solo guarda el hash), **no alarga el challenge**
  (si devolviera el TTL entero, reenviar estiraría la vida del código indefinidamente) y tiene
  enfriamiento de 30 s con `Retry-After` en el 429.
- **Reset**: **no hay endpoint nuevo**. `/auth/reset/request` ya lo es: cada llamada genera un token
  nuevo y el rate limit por IP lo protege. El botón del frontend es una llamada y listo.

## 8. Cómo se comprueban estas decisiones

Cada una tiene un test que falla si la decisión se deshace. Es lo que permite cambiar el código sin
miedo:

| Decisión | Test |
|---|---|
| El envío no ocurre en el hilo de la petición | `MailServiceTest.elEnvioNoOcurreEnElHiloQueLoPide` |
| La cola llena descarta y no bloquea | `MailServiceTest.colaLlenaDescartaElCorreoYLoDejaEnElLog` |
| Apagar espera, no corta | `MailServiceTest.apagarEsperaAlCorreoQueEstaEnVueloEnLugarDeCortarlo` |
| El fallo de SMTP no se pierde | `PasswordResetServiceTest.smtpCaido…` (comprueba el log) |
| Las claves i18n existen en los dos idiomas | `MailMessageKeysTest` |
| El reenvío genera un código nuevo y el anterior muere | `OtpServiceTest.elReenvioMandaUnCodigoNuevoYElAnteriorDejaDeValer` |
| El reenvío no alarga el challenge | `OtpChallengeStoreTest.recodeCambiaElHashYConservaElTtlQueQueda` |
| Un SMTP atascado no alarga la respuesta del reset | `PasswordResetServiceTest.smtpAtasgadoNoAlargaLaRespuestaDelReset` |
| Un endpoint público está en la cadena de seguridad | `SecurityPublicPathsTest` |

244 tests en total. `./mvnw -o clean test` desde `portfolio-api/`.

## 9. Referencia rápida

```
MailService (canSend, sendAsync, apagar)
  ├── ContactService        → contacto al admin + acuse al visitante
  ├── OtpService            → código de acceso (crear challenge y reenviar)
  ├── PasswordResetService  → enlace de restablecimiento
  └── SecurityNotificationService → avisos de acceso nuevo y de cierre de sesión
```

Cambiar el remitente, los timeouts, el hilo o el manejo de errores: todo eso pasa por `MailService`.
Añadir un tipo de correo nuevo: `MailService` no se toca; se añade un `sendAsync` más si hace falta.
