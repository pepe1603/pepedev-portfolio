# Sesión 3 · API: proyecto generado, modelo de datos, persistencia y conexión a Terramount

> Fecha: 2026-09-14 · Estado: cerrada. El Bloque 3 (config y seguridad base) queda verificado con `/actuator/health` UP.

## Qué se hizo en esta sesión

1. **Paso 0.5 — Proyecto Spring Boot generado**: se extrajo `api.zip` (generado por el usuario en
   start.spring.io) a `api/`. Ajustes en `pom.xml`: quitado Thymeleaf (starter y test) y añadidos
   `springdoc-openapi-starter-webmvc-ui` **3.1.0** y `jjwt` **0.12.7** (api/impl/jackson). Compila.
   Commit `chore: proyecto spring boot 4.1.1 generado en spring.io`.
2. **Bloque 1.1 — Modelo de datos**: creado y aprobado `docs/MODELO-DATOS.md` (fuente de verdad): 5 tablas,
   JSONB bilingüe `{es,en}`, enums `varchar + CHECK`, índices, singleton `profile` (id SMALLINT = 1), seed
   placeholder, sin FKs de contenido. Commit `docs: modelo de datos mínimo (fuente de verdad)`.
3. **Bloque 1.2 — Migración**: `V1__init.sql` con las 5 tablas + CHECKs + índices + seed `profile`.
4. **Decisión de IDs y enums**: IDs **UUID v7** (RFC 9562) con `@UuidGenerator(style = UuidGenerator.Style.VERSION_7)`
   (Hibernate 7.4.5 lo soporta) y enums en **MAYÚSCULAS** en BD para cuadrar con `@Enumerated(STRING)` (opción A).
   Commits `feat(db): migración flyway V1__init` y `refactor(db): ids uuid v7 y enums en mayúsculas`.
5. **Bloque 2 — Persistencia JPA**: 5 enums (`UserRole`, `ProjectStatus`, `CertificateKind`, `CertificateStatus`,
   `MessageStatus`), 3 records JSONB (`GalleryImage`, `Skill`, `Experience`), 5 entidades (`User`, `Profile`,
   `Project`, `Certificate`, `Message`) y 5 repositorios. Compila sin BD.
   Commit `feat(api): entidades jpa y repositorios`.
6. **Bloque 2.1 — Conexión a Terramount**: `.env` real, `application.yaml` 100% por variables de entorno, túnel SSH
   (`-L 5432 -L 6379`), Flyway aplica V1 sobre **PostgreSQL 18.6** (contenedor Docker en el VPS) y
   `ddl-auto: validate` cuadra con el schema. Fix previo en el VPS: `GRANT ALL PRIVILEGES ON SCHEMA public TO portfolio_app`
   (lockdown del schema public en PG15+). Commit `feat(api): conexión a terramount por variables de entorno`.
7. **Bloque 3 — Config y seguridad base**: `SecurityConfig` (públicos: health, swagger, `/contact`; CORS por env;
   `BCryptPasswordEncoder`; STATELESS; CSRF off), `RedisConfig` (`RedisTemplate<String,Object>` con
   `GenericJacksonJsonRedisSerializer` Jackson 3), `OpenAPIConfig` (info + esquema `bearer-auth`) y
   `AdminBootstrap` (crea el admin desde `APP_ADMIN_EMAIL`/`APP_ADMIN_SECRET` si `users` está vacía).
   App arranca y Swagger UI funciona con `bearer-auth`.

## Diagnóstico del health (SMTP) resuelto en el Bloque 3

- `/actuator/health` daba `DOWN` (503) porque el `MailHealthIndicator` intenta un **login real** contra el SMTP:
  primero `AuthenticationFailedException: Must issue a STARTTLS command first` y luego, al activar STARTTLS,
  `535 Invalid username`.
- Correcciones: en `application.yaml` se añadió `spring.mail` con `mail.smtp.auth: true` +
  `mail.smtp.starttls.enable: true`; y en el `.env` `SPRING_MAIL_USERNAME` pasa de un email a **`resend`**
  (en Resend el usuario SMTP es literalmente `resend`, con la API key como contraseña). Verificado por prueba
  SMTP real: `no-reply@pepe1603.dev` → 535, `resend` → LOGIN OK.
- Resultado: `GET /actuator/health` → `{"status":"UP"}` (HTTP 200). El aviso `Using generated security password`
  seguirá saliendo hasta que el Bloque 4 defina el `UserDetailsService` real.

## Verificaciones en Terramount (Sesión 3)

- `users` tiene una fila: `admin@pepe1603.dev`, role `ADMIN`, creado en el arranque (UUID v7 OK).
- 6 tablas: `users`, `profile`, `projects`, `certificates`, `messages` + `flyway_schema_history` (owner `portfolio_app`).
- `flyway_schema_history`: V1 aplicada, `success = t`.

## Decisiones cerradas en la sesión

- Paquete Java: `dev.pepe1603.api` (guion bajo), tal como generó Initializr; se mantiene (en vez de `portfolioapi`).
- IDs de contenido: **UUID v7** vía `@UuidGenerator(style = VERSION_7)`; `profile.id` SMALLINT fijo = 1 sin generador.
- Enums: valores en BD en **MAYÚSCULAS** (CHECK) para cuadrar con `@Enumerated(STRING)`.
- Spring Boot 4 usa **Jackson 3** (`tools.jackson`) como default; para Redis se usa `GenericJacksonJsonRedisSerializer`
  (variante Jackson 3 de spring-data-redis 4.1.1). Jackson 2 sigue en el classpath vía jjwt.

## Hoja de ruta viva (detalle en docs/PLAN-API.md)

0 Cimientos ✔ · 0.5 Proyecto generado ✔ · 1.1 Modelo ✔ · 1.2 Migración ✔ · 2 JPA ✔ · 2.1 Conexión Terramount ✔ ·
**3 Config/seguridad base ✔** (health UP) · **4 Auth JWT (siguiente)** · 5 API pública · 6 CRM · 7 Contrato OpenAPI.

## Notas de operación para el Bloque 4

- La fila `users` (admin) quedó con las credenciales del primer arranque (`admin@pepe1603.dev` / placeholder).
  Al tocar auth hay que actualizarla o borrarla para que `AdminBootstrap` la recree con el `.env` actual (ver
  "Notas de operación" en PLAN-API.md).
- El túnel SSH a Terramount debe estar activo para arrancar la app.

---

## Prompt para la siguiente sesión (copiar/pegar)

```
Retomamos el proyecto pepedev-portfolio (docs en /home/pepe-dev/Projects/pepedev-portfolio).

Contexto cerrado (lee en orden docs/SESION3.md, docs/PLAN-API.md, docs/MODELO-DATOS.md y docs/REQUIREMENTS.md §6):
- Monorepo: api/ (Spring Boot 4.1.1, Java 21, Maven, paquete dev.pepe1603.api) ya enlazado y
  compilando; web/ (Nuxt, futuro); docs/. Bloques 0, 0.5, 1.1, 1.2, 2, 2.1 y 3 del PLAN-API COMPLETADOS y commiteados.
- BD/Redis remotos en Terramount POR TÚNEL SSH (ssh -L 5432:localhost:5432 -L 6379:localhost:6379 teramont-dev);
  corren como contenedores Docker. Flyway aplicó V1 sobre PostgreSQL 18.6 y ddl-auto:validate cuadra.
- Seguridad base OK: /actuator/health UP, Swagger UI activo con esquema bearer-auth, AdminBootstrap creó el admin
  (users tiene admin@pepe1603.dev con el secreto del primer arranque).
- Reglas: sin Docker en desarrollo; secrets solo por variables de entorno (.env, gitignored; .env.example es la
  plantilla versionada); Flyway gestiona el schema (nunca ddl-auto diferente de validate); contenido bilingüe ES/EN
  en JSONB {es,en}; conectar a la BD sin exponer contraseñas en el chat si es posible.

Nota de operación (importante para auth):
- La fila users (admin) todavía tiene las credenciales del primer arranque. En este bloque hay que decidir cómo
  quedará el login del admin: actualizar la fila (email + hash BCrypt del APP_ADMIN_SECRET actual del .env) o
  borrarla y dejar que AdminBootstrap la recree.
- SPRING_MAIL_USERNAME debe ser literal "resend" (no un email) porque el MailHealthIndicator baja el health si el
  login SMTP falla.

Método de trabajo (pair programming / mentoría):
- Yo (humano) desarrollo la API poco a poco, clase por clase, siguiendo docs/PLAN-API.md (el Bloque 4 = auth JWT:
  login, refresh rotativo, logout con denylist en Redis, filtro JWT y rate limit de login).
- Tú (IA) eres mi mentor: revisas mi código, me dices si está bien y qué corregir y por qué.
- Solo escribes tú el código cuando te lo pida explícitamente, y después me lo explicas para que aprenda.
- Se avanza commit por commit y no te adelantas ni creas nada sin mi aprobación.

Tarea de la próxima sesión:
- Empezar el Bloque 4 (auth JWT) siguiendo el plan. Primero preséntame el desglose del bloque y los puntos
  conflictivos (refresh en cookie vs body, estrategia de rate limit, cómo resolver el admin row vs .env) ANTES de escribir código.
- El objetivo del bloque: login → endpoint protegido → refresh → logout OK, con su commit
  suggerido "feat(api): auth jwt access+refresh".
```