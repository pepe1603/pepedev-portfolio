# Plan de construcción de la API — pepedev-portfolio

> Objetivo: dejar la API (`portfolio-api/`) lista para que el front (Nuxt) consuma todo lo que
> necesita, avanzando **commit por commit** y aprobando cada paso. NO se corre delante:
> cada bloque se revisa y aproba antes del siguiente.
> Stack cerrado: Spring Boot 4.1.1 (estable de Initializr, ver SESION2) · Java 21 · Maven · PostgreSQL + JSONB · Flyway ·
> Spring Security + jjwt · Redis · springdoc OpenAPI. Sin Docker en desarrollo.

## Reglas de trabajo

- Un commit = un paso del plan. Antes de cada commit: `git status`, revisión del diff y tu aprobación.
- El schema manda: se diseña primero el **modelo de datos**, luego la migración Flyway, y las clases JPA se escriben después para cuadrar con `ddl-auto: validate`.
- Secretos solo por variables de entorno (`.env`), nunca en git.
- Se avanza sin entrar en código hasta que el paso anterior esté aprobado.

## Hoja de ruta (bloques → pasos → commit)

| # | Tarea | Entregable | Commit sugerido | DoD (definición de hecho) |
|---|---|---|---|---|
| ✔ **0** | Cimientos del repo | `.gitignore`, `.env.example`, docs (incluido este plan), rama `main`. El proyecto `portfolio-api/` lo genera EL USUARIO en start.spring.io y lo coloca en la raíz (pom en `portfolio-api/pom.xml`), después se commitea como `chore: proyecto spring boot generado en spring.io` | `chore: base del monorepo` | `git status` limpio de secretos |
| ✔ **1.1** | **Definir el modelo de datos** (revisión, sin código) | Modelo detallado: tablas, columnas, tipos, JSONB `{es,en}`, enums (CHECK), índices, singleton, seed, decisiones abiertas resueltas | `docs: modelo de datos mínimo (fuente de verdad)` | Modelo revisado y **aprobado por ti** |
| ✔ **1.2** | Migración del modelo | `V1__init.sql` con las 5 tablas + CHECKs + índices + seed `profile` | `feat(db): migración flyway V1__init` | SQL revisado (se aplica en el paso 2.1, al conectar) |
| ✔ **2** | Persistencia JPA | 5 entidades + mapeo JSONB (Hibernate 6 `@JdbcTypeCode(SqlTypes.JSON)`) + enums Java + repos Spring Data | `feat(api): entidades jpa y repositorios` | Compila sin BD (escribir no requiere conexión) |
| ✔ **2.1** | **Conexión con Terramount (env)** | Crear `.env` real (credenciales que proporcione el usuario), arrancar con `./mvnw spring-boot:run`: Flyway aplica V1, `ddl-auto: validate` cuadra con el schema, `/actuator/health` OK | `feat(api): conexión a terramount por variables de entorno` | App arranca contra PG/Redis remotos · V1 aplicada · validate OK |
| ✔ **3** | Config y seguridad base | `SecurityConfig` (health/swagger/contacto públicos), CORS por env, RedisConfig, OpenAPI info, `AdminBootstrap` (admin desde env) | `feat(api): config de seguridad, redis, cors y openapi` | App arranca · `/actuator/health` 200 |
| ✔ **4** | Auth JWT | `login`, `refresh` rotativo, `logout` (denylist en Redis), filtro JWT, rate limit de login | 6 commits por pieza (`525d603`→`ac47b8c`) | Flujo login → endpoint protegido → refresh → logout OK · rate limit 5×401 + 429 |
| ✔ **5.1** | API pública: profile + projects | Listado/detalle de proyectos por slug + profile singleton, caché Redis + ETag | 7 commits (`27ba602`→`ef680f7`) | `curl` OK contra `.env` (verificado en SESION5) |
| **5.2** | API pública: certificates | Listado con filtro kind/issuer | 5 commits (`bc103ea`→`2b62369`) | `curl` OK |
| ✔ **5.3** | API pública: contacto + CV | `POST /contact` (honeypot + rate limit + validación + SMTP + persistencia), redirect CV es/en | `feat(api): contacto y descarga de cv` | Verificado (Postman, sesión 8) |
| ✔ **6.1** | CRM: CRUD projects + certificates | Crear/editar/publicar/despublicar/ordenar/borrar | 6 commits (`9850a42`→`e420af2`) | Verificado (Postman, sesión 8) |
| ✔ **6.2** | CRM: bandeja de mensajes | Listar por status, marcar leído/archivado, borrar | 4 commits (`cc1ab0d`→`452b47f`) | Verificado (Postman, sesión 9) |
| ✔ **6.3** | CRM: storage + profile | Upload multipart → URL (`avatar`, `thumbnail`, `gallery`, `image`, CV es/en) + edición de profile | 7 commits (`c20df78`→`45103c0`) + fix `ad56ad1` | **DoD VERIFICADO** (2026-09-24, curl; guía y resultados en SESION10.md) |
| ✔ **7** | Contrato y cierre | `docs/API.md` alineado con lo implementado + respuestas de error unificadas (RFC 9457) + Swagger verificado | 5 commits: `15c2e0d`, `bca8d80`, `4d916ab`, `1c7e09d`, `df9e683`, `2007df8` | **VERIFICADO** (2026-09-25; detalle en SESION11.md) |

## Hito

Al terminar el Bloque 7 la API está **lista para el front**: endpoints públicos (landing,
proyectos, certificados, contacto, CV), auth JWT, CRM completo, storage y contrato OpenAPI
verificado.

## Decisiones cerradas que se aplican según llega su paso

- Admin (Blocker 4): crearlo por `ApplicationRunner` desde `APP_ADMIN_EMAIL`/`APP_ADMIN_SECRET` si `users` está vacía — nunca un hash en una migración.
- **Auth JWT (Bloque 4)**: refresh token en **cookie httpOnly** (`SameSite=Lax`, `Path=/auth`, `Secure` por `APP_JWT_REFRESH_COOKIE_SECURE`) y access en `Authorization: Bearer`. TTLs: access 15 min (`APP_JWT_ACCESS_TTL`) · refresh 7 días (`APP_JWT_REFRESH_TTL`). Dos secretos distintos (`APP_JWT_SECRET`/`APP_JWT_REFRESH_SECRET`), claims `sub`(email)/`role`/`jti`/`typ`. **Rotación**: cada refresh revoca el anterior en la denylist de Redis (`jwt:revoked:{jti}` + TTL restante); logout revoca refresh (cookie) y access (Bearer).
- **Rate limit login (Bloque 4)**: `INCR`+`EXPIRE` en Redis, ventana fija, **5 fallos/IP + 10 fallos/email por 15 min**, reset al login OK → 429 con `Retry-After` (`APP_LOGIN_RATE_*`). Detrás de proxy se confía en `X-Forwarded-For` (`server.forward-headers-strategy: framework`).
- **Admin de BD resuelto (Bloque 4)**: la fila del primer arranque se borró (`DELETE FROM users`) y `AdminBootstrap` la recreó con el `.env` actual (`000316jose@gmail.com`).
- Enums (Blocker 1): `varchar + CHECK`, no tipos enum PG; en Java `@Enumerated(STRING)`.
- Bilingüe (Blocker 1): `headline`, `bio`, `subtitle`, `title`, `summary`, `description_md` en JSONB `{es,en}`; `certificates` también tiene `status`.
- IP en `messages` (Blocker 5.3): guardar anonimizada (`/24`); el rate-limit vive en Redis.
- Singleton `profile` (Blocker 1.1): `id SMALLINT CHECK (id = 1)`, una sola fila.
- Timestamps (Blocker 1.1): `TIMESTAMPTZ DEFAULT now()`; `updated_at` lo gestiona JPA, sin triggers.
- Sin índice GIN sobre `stack[]` de momento (volumen pequeño); se añadiría en V2 si el filtro lo pide.
- Tests (referencia durante todo el plan): unitarios con mocks; integración = H2-compatible o schema real en Terramount según se decida en el Bloque 2.
- **API pública (Bloque 5.1)**: solo `PUBLISHED` visible vía `/public/*` (404 sin revelar si es draft o inexistente). Idioma por query param `?lang=es|en` (default `es`), fallback por campo bilingüe con `LocalizedText` (sin señal al cliente). Claves Redis `pub:profile:{lang}` / `pub:projects:{lang}` / `pub:project:{slug}:{lang}` guardando el **body JSON serializado**; TTL configurable `APP_PUBLIC_CACHE_TTL` (300 s). ETag fuerte SHA-256 del body + `Cache-Control: public, max-age=0, must-revalidate` → 304 en `If-None-Match`. Evicts explícitos `evictProfile()` / `evictProjectsList()` / `evictProject(slug)` borran variantes ES+EN; listos para Bloque 6. DTO público sin `views_count` ni timestamps; listado sin paginación/filtros (volumen ≤15).
- **Error dispatch (Bloque 5.1)**: `dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()` en `SecurityConfig`. Sin esto, `/error` (donde viajan los `ResponseStatusException`) exigía auth y **un 404 público salía como 401**. El body de error sigue siendo el `BasicErrorController` (con stack en dev por devtools); unificar formato → Bloque 7.
- **API pública certificates (Bloque 5.2)**: `GET /public/certificates` solo `PUBLISHED`, orden `sortOrder ASC → issueDate DESC (NULLS LAST) → createdAt ASC` (sin slug que desempate: no hay detalle). Filtros `kind` y `issuer` **combinables (AND)** y case-insensitive; `kind` inválido → 400. `kind` se publica en **minúsculas** (`certificate`/`course`; BD y Java en MAYÚSCULAS, solo lo normaliza el mapper). `issuer` es **igualdad exacta** (no contains), sirve el VARCHAR tal cual (no bilingüe). Sin endpoint de emisores: el front los deriva de la lista (≤15). Caché: la clave guarda **solo la lista completa** `pub:certificates:{lang}`; los filtros se aplican **en memoria** en el controller (≤15 items) sobre el body cacheado, por eso sin filtros se sirve el body byte-estable directo. Evict `evictCertificatesList()` borra ES+EN; listo para Bloque 6. Sin coincidencias o tabla vacía → `[]` con 200/ETag (nunca 404).
- **Contacto + CV (Bloque 5.3)**: `POST /contact` orden: `@Valid` (name≤120, email≤320 @Email, subject≤160, body 10..5000) → **honeypot** `website` relleno = **éxito falso idéntico** (201, sin persistir ni enviar) → **rate limit solo IP** `rl:contact:ip:{ip}` (`APP_CONTACT_RATE_MAX_IP` 5 / `APP_CONTACT_RATE_WINDOW` 900 s) → 429 + `Retry-After` con el mismo body `Map` del login → persistir `Message` (IP **anonimizada /24** en IPv4 y **/64** en IPv6, IPv4-mapped normalizada, UA truncada a 255) → **mail best-effort async** (`CompletableFuture.runAsync`): si falta `APP_CONTACT_FROM_EMAIL` o SMTP falla, se loguea pero la respuesta sigue siendo **201 Created** (la BD es la fuente de verdad). `Reply-To` = email del remitente. `GET /public/cv/{lang}` (es|en) → **302 Found** (nunca 301/308: la URL cambia al re-subir el PDF) **reutilizando la caché `pub:profile:{lang}`** (sin claves/evicts propios; CV nunca diverge de `/public/profile`); fallback ES↔EN, 404 si ambos nulos. Sin ETag/304 en el redirect (302 sin body).

- **CRM (Bloque 6.1)**: `/admin/**` exige `hasRole("ADMIN")` (el filtro JWT ya inyecta `ROLE_<role>`). Endpoints en `/admin/projects` y `/admin/certificates`: `GET` listado (todo `status`, `sortOrder ASC → createdAt ASC`), `GET /{id}` (por UUID, no slug: permite editar drafts), `POST` crear (siempre `DRAFT`), `PUT /{id}` edición **completa** (mantiene `status`/`published_at`; optativos ausentes = null salvo `sortOrder`→0 y `gallery`/`stack`→`[]`), `PATCH /{id}/publish` (setea `published_at=now()`), `PATCH /{id}/unpublish` (lo limpia), `PUT /order` (lista **ordenada** de UUIDs → posición=índice, 404 si alguno no existe → rollback del lote), `DELETE /{id}` (hard delete, 204). Las respuestas devuelven la **entidad** (sin DTO admin). `slug` lo fija el cliente (`^[a-z0-9]+(-[a-z0-9]+)*$`, ≤120), único → **409** (`existsBySlugAndIdNot` en PUT). 404 con `ResponseStatusException`. **Evicts tras la escritura y solo si toca algo publicado**: publish/unpublish/editar-publicado/borrar-publicado → listado + detalle (slug viejo **y** nuevo si se renombra); reorder → solo listado; crear/editar DRAFT → sin evict. Bilingüe: `title` exige al menos `es` o `en` no vacío vía `@LocalizedNonBlank`. `kind` inválido → 400 por Jackson. Errores siguen en `BasicErrorController` (formato unificado → Bloque 7).

- **Bandeja de mensajes (Bloque 6.2)**: `/admin/messages` (auth heredada del patrón `/admin/**`). `GET` listado con `?status=NEW|READ|ARCHIVED` opcional (sin filtro → todo; orden `createdAt DESC`, más reciente primero; `status` inválido → 400 por type mismatch de Spring), `GET /{id}` detalle por UUID (no muta el estado), `PATCH /{id}/read` y `PATCH /{id}/archive` (transiciones **libres e idempotentes**: any→READ / any→ARCHIVED, repetir = no-op 200; sin `/unread`), `DELETE /{id}` hard delete 204. Sin DTO ni `@Valid` (las mutaciones no llevan body: el verbo es la acción). 404 con `ResponseStatusException`. **Sin paginación** (volumen pequeño) y **sin evicts de caché pública** (messages no se cachea ni se sirve en público; `pub:*` solo contiene profile/projects/certificates).

- **Storage + profile (Bloque 6.3)**: storage en **local FS** (`APP_STORAGE_DIR` + `APP_STORAGE_PUBLIC_URL`), servido por la propia API vía `GET /files/**` estático y **público** en `SecurityConfig` (sin Docker ni S3/MinIO). `POST /admin/storage?use={avatar|thumbnail|gallery|image|cv}` (multipart) → `{ "url" }`: validación por **magic bytes** (allowlist imágenes jpeg/png/webp; **PDF solo para `cv`**) + tamaño máx **5 MB imágenes / 10 MB CV** + nombre generado por servidor (**UUID + extensión normalizada**). Tipo no permitido → **415**; tamaño excedido → **413**; fichero vacío / `use` inválido / sin parte → **400**. Límites globales multipart `spring.servlet.multipart` 15/20 MB (holgados; el negocio valida los 5/10). El upload **no muta entidades ni hace evicts**; **sin borrado de orfanatos** por ahora. `GET /admin/profile` devuelve la entidad para que el front cargue el estado antes de editar (añadido en pieza 0). `PUT /admin/profile` completo: `full_name` (@NotBlank), `headline`/`bio` (@LocalizedNonBlank), `location`, urls `github/linkedin/website/cv_es/cv_en/avatar` (@Size 255 + `@URL` de **Hibernate Validator**, no existe en `jakarta.validation.constraints`), `email_public` (@Size 320 + `@Email`), `skills[{name,category,level}]`, `experiences` (lista vacía si ausentes, coherencia con `NOT NULL DEFAULT '[]'`). `views_count` y timestamps **se ignoran en silencio** (ausentes del DTO; Jackson descarta lo desconocido por defecto en Boot). 404 si la fila id=1 no existiera. Cada PUT → `cacheService.evictProfile()` (variantes ES+EN); **el CV reutiliza la caché de profile** (`CvController` lee `pub:profile:{lang}`), así que no necesita evict propio. Nota verificación (CORREGIDA en Bloque 7): el advice global `@ControllerAdvice` **SÍ resuelve** en este proyecto; el 500 del storage era por no tener `@ExceptionHandler` de `MultipartException`. Por eso la parte `file` se revirtió a `@RequestPart MultipartFile` obligatoria (`1c7e09d`) y los casos "no multipart"/"sin parte"/"vacío" → 400 los cubre el advice (ver `config/ApiExceptionHandler`).

## Pendiente de decidir (se cierran en su bloque, no antes)

- Estrategia de tests de BD (H2 vs Terramount dedicado) → Bloque 2.
- Mapeo JSONB con Hibernate 6 nativo (sin dependencias extra) → Bloque 2.

## Notas de operación

- **Admin ya creado en BD ≠ `.env` (pendiente al Bloque 4).** En el primer arranque
  (Sesión 3) `AdminBootstrap` creó la fila `users` con `admin@pepe1603.dev` y el secreto
  de aquel momento (placeholder `cambia_esto`). Luego el `.env` se actualizó a
  `000316jose@gmail.com` con un secreto nuevo, **pero la fila de BD no cambió**
  (`AdminBootstrap` solo corre si `users` está vacía). Al llegar el Bloque 4 hay que
  actualizar esa fila (email + hash BCrypt del nuevo secreto) o borrarla para que el
  bootstrap la recree con las credenciales actuales del `.env`.
- El túnel a Terramount es requisito para arrancar: `ssh -L 5432:localhost:5432 -L 6379:localhost:6379 teramont-dev` (PG y Redis corren como contenedores Docker en el VPS).
- **Boot 4 = Spring Framework 7 + Spring Data 4 (rompe APIs de Boot 3, visto en Bloques 5.1 y 5.2)**: `Sort.by(Direction, String...)` → `Sort.by(Sort.Order.asc/desc(...))`; `RedisTemplate.delete(K...)` varargs → `delete(Collection<K>)`; `CacheControl.cachePublic()` deja de ser estático (factories: `maxAge/noCache/noStore`); `Sort.Order.nulls(...)` → `nullsFirst()/nullsLast()/nullsNative()`.
- **Health con SMTP**: si `smtp.resend.com` no responde desde la máquina de dev, `/actuator/health` baja a `DOWN` y tarda ~134 s; arrancar con `MANAGEMENT_HEALTH_MAIL_ENABLED=false` para verificar en dev (override de entorno, no de código).
## Anexo (Sesión 12): cierre de la deuda documentada — 2026-09-26

Toda la deuda que quedó anotada en esta guía (frasando "por ahora", "pendiente", "gap",
"Sin paginación", "sin borrado de orfanatos", "BasicErrorController") se cerró con tests
verdes (suite completa: **86 tests, 0 fallos**) y un commit por pieza:

| # | Deuda (dónde estaba anotada) | Cierre | Commit |
|---|---|---|---|
| A | Bandeja de mensajes **sin paginación** (línea Bloque 6.2) | `GET /admin/messages` paginado: `{items,page,size,totalElements,totalPages,last}`, `page` 0-based + `size` 1..100 (400 si fuera de rango), orden determinista `createdAt DESC, id DESC`, índice compuesto `idx_messages_status_created_at` (V2). Slice test `AdminMessageListTest` (7 casos) | `77450cb` |
| B | Storage **sin borrado de orfanatos** (línea Bloque 6.3) | `StorageService.deleteByUrl` (+`baseName` con patrón `UUID.(jpg\|png\|webp\|pdf)` y guarda anti-traversal), `StorageReferenceChecker` (profile avatar/cv×2, certificates imageUrl, projects thumbnail+gallery), `OrphanFileCleaner`. Conectado a `AdminProjectService.{update,delete}`, `AdminCertificateService.{update,delete}`, `AdminProfileService.update`: se limpian las URLs que dejan de usarse **solo si** nadie más las referencia. Tests unitarios + de servicios (13 nuevos) | `b21cd7b` |
| C | **Gap de errores a nivel contenedor** (`/error` con `timestamp/path/trace`; 413 de multipart fuera del envelope) | `JsonErrorController implements ErrorController` (reemplaza `BasicErrorController`): `/error` siempre ProblemDetail JSON con `title` de razón, `detail` en español y **sin** `type/timestamp/path/trace`; `server.error.include-message=never`. `@Override handleMaxUploadSizeExceededException` → `413 "El fichero supera el tamaño máximo permitido"` (antes caía en el 400 de `MultipartException`; título 413 en Spring 7 = **`Content Too Large`**). 404 de recurso estático ya era ProblemDetail de Framework 7 (`NoResourceFoundException`), fuera de `/error`. Tests: `JsonErrorControllerTest` (4) + casos en `ApiErrorContractTest` | `e70175f` |
| D | Swagger **sin documentar** `errors[]` ni multipart | Schemas `ApiProblemDetail` (`errors[]: ErrorField[]`) + `ErrorField`; `@ApiResponse` para el envelope en `login`, `contact` y `upload` (400/413/415) | `07b239e` |
| E | Producción (CORS/cookie ya configurables) **sin runbook** | `docs/PRODUCCION.md`: variables de producción, build+systemd, proxy TLS (Caddy/nginx), checklist de verificación y operación | `Pieza E` (docs) |
| F | Anexos de sesión **pendientes** del Bloque 7 | `docs/SESION12.md` con esta sesión de cierre | `Pieza F` (docs) |
