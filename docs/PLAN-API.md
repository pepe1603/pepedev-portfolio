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
| **5.1** | API pública: profile + projects | Listado/detalle de proyectos por slug + profile singleton, caché Redis + ETag | 5 commits (`27ba602`→`2bb59fb`) | `curl` OK contra `.env` (**pendiente de verificar**) |
| **5.2** | API pública: certificates | Listado con filtro kind/issuer | `feat(api): endpoint público certificates` | `curl` OK |
| **5.3** | API pública: contacto + CV | `POST /contact` (honeypot + rate limit + validación + SMTP + persistencia), redirect CV es/en | `feat(api): contacto y descarga de cv` | Envío simulado OK · anti-spam activo |
| **6.1** | CRM: CRUD projects + certificates | Crear/editar/publicar/despublicar/ordenar/borrar | `feat(api): crud admin projects y certificates` | Auth exigido · CRUD completo probado |
| **6.2** | CRM: bandeja de mensajes | Listar por status, marcar leído/archivado, borrar | `feat(api): bandeja de mensajes admin` | Operaciones bandeja probadas |
| **6.3** | CRM: storage + profile | Upload multipart → URL (`avatar`, `thumbnail`, `gallery`, `image`, CV es/en) + edición de profile | `feat(api): storage y gestión de profile` | Subida validada (mime/tamaño/nombre) · URL servida |
| **7** | Contrato y cierre | `docs/API.md` alineado con lo implementado + respuestas de error + Swagger verificado | `docs: contrato openapi verificado y docs de api` | El front puede consumir todo · Swagger UI lo documenta |

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
- **Boot 4 = Spring Framework 7 + Spring Data 4 (rompe APIs de Boot 3, visto en Bloque 5.1)**: `Sort.by(Direction, String...)` → `Sort.by(Sort.Order.asc/desc(...))`; `RedisTemplate.delete(K...)` varargs → `delete(Collection<K>)`; `CacheControl.cachePublic()` deja de ser estático (factories: `maxAge/noCache/noStore`).
- **Health con SMTP**: si `smtp.resend.com` no responde desde la máquina de dev, `/actuator/health` baja a `DOWN` y tarda ~134 s; arrancar con `MANAGEMENT_HEALTH_MAIL_ENABLED=false` para verificar en dev (override de entorno, no de código).