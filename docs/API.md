# Contrato de API — pepedev-portfolio

> Fuente de verdad del Bloque 7 (verificado contra el código y la app en ejecución).
> Stack: Spring Boot 4.1.1 · Spring Framework 7.0.9 · Java 21 · PostgreSQL+JSONB · Redis · springdoc.
> Base URL en dev: `http://localhost:8080`. Contrato vivo en `/v3/api-docs` (OpenAPI 3.1.0).
> Swagger UI en `/swagger-ui.html`. Schemas documentados: `ApiProblemDetail` (envelope de
> error, con `errors[]: ErrorField[]` en 400 de validación), `TokenResponse`, `LoginRequest`,
> `ContactRequest` y el multipart del storage (`use` + `file`).

## Autenticación

- **Access token**: `Authorization: Bearer <jwt>` (vida por defecto 15 min / `APP_JWT_ACCESS_TTL`).
  Claims: `sub` (email), `role`, `jti`, `typ=access`, `tv` (versión del token), `sid` (sesión),
  `iat`, `exp`. Algoritmo HS512.
- **Refresh token**: cookie **httpOnly** `refresh_token`, `Path=/auth`, `SameSite=Lax`,
  `Secure` si `APP_JWT_REFRESH_COOKIE_SECURE=true`, vida 7 días (`APP_JWT_REFRESH_TTL`). Rotación:
  cada refresh revoca el refresh anterior (denylist Redis `jwt:revoked:{jti}` + TTL restante).
- **Logout**: revoca refresh (cookie) y access (Bearer) en la denylist. Siempre `204` (revocación best-effort).
- **Sesiones activas**: cada login crea una fila en `auth_sessions` (`session_id` = claim `sid`).
  `GET /auth/sessions` lista las activas del usuario y revoca (`revoked_at`) + mete en la denylist
  las caducadas/revocadas por adelantado. `token_version` (`tv`) permite invalidar todos los tokens
  de golpe: se valida al rotar (`/auth/refresh`), por lo que un access token puede vivir hasta
  `APP_JWT_ACCESS_TTL` minutos tras una revocación global.
- Las operaciones `/admin/**` requieren `ROLE_ADMIN` (claim `role=ADMIN`). El resto de
  operaciones autenticadas aceptan cualquier rol autenticado.
- `Authorization` con token inválido/revocado/caducado → `401` (el filtro ignora el header y
  llega al `AuthenticationEntryPoint`). Sin rol suficiente → `403`.
- CORS: `APP_CORS_ALLOWED_ORIGINS` (por defecto `http://localhost:3000`).

## Formato de error único (RFC 9457, `ProblemDetail`)

Todos los errores del contrato usan el mismo envelope JSON (UTF-8):

```json
{
  "title": "Not Found",
  "status": 404,
  "detail": "Certificado no encontrado",
  "instance": "/admin/certificates/00000000-0000-0000-0000-000000000000"
}
```

| Campo | Contenido |
|---|---|
| `title` | reason phrase en inglés del status |
| `status` | código HTTP |
| `detail` | mensaje en español, específico del caso |
| `instance` | ruta del request que produjo el error |

Nota: `type` (RFC 9455) equivale a `about:blank` en todos los casos y **no se serializa** por el
serializador de Spring (solo aparecería si fuera distinto del default).

Reglas y extensiones:

- **Validación (`400`)**: añade `"errors": [{"field": "...", "message": "..."}]` con uno por
  campo; si hay errores de objeto, `field` es `null`.
- **Rate limit (`429`)**: incluye cabecera `Retry-After: <segundos>`.
- **401/403 de seguridad**: los escriben los filtros (`JwtAuthEntryPoint`/`JwtAccessDeniedHandler`)
  con el mismo shape (sin `errors[]`), con UTF-8 correcto.
- **Gap de contenedor cerrado**: las excepciones no previstas derivan a `/error`, servido por
  `JsonErrorController` con **el mismo envelope** (título razón del status, `detail` en español,
  sin `type`/`timestamp`/`path`/`trace`; `server.error.include-message=never`). Mensajes por
  status: `400`/`404`/`405`/`413`/`429` y, en cualquier otro caso, `500` "Error interno del
  servidor". **Los 413 de multipart desmesurado (15/20 MB del contenedor) también entran en el
  envelope**: `MaxUploadSizeExceededException` → `413` "El fichero supera el tamaño máximo
  permitido" (`@Override handleMaxUploadSizeExceededException`).
- **404 de recurso estático**: Spring Framework 7 lo resuelve antes de `/error` (`NoResourceFoundException`)
  y ya responde ProblemDetail JSON (`title: Not Found`, `detail: "No static resource ..."`).

Códigos de error más comunes por familia:

| 4xx | Cuándo |
|---|---|
| `400` | validación `@Valid`, JSON malformado/type mismatch, `use` inválido, multipart mal formado, fichero vacío |
| `401` | sin token/credenciales malas/refresh inválido/caducado/revocado |
| `403` | token válido sin `ROLE_ADMIN` en `/admin/**` |
| `404` | recurso inexistente (UUID, slug, fichero estático, CV sin URL) |
| `409` | conflicto (slug de project ya existe) |
| `413` | fichero mayor que el límite (5/10 MB negocio, 15/20 MB contenedor) — título RFC 9110 `Content Too Large` |
| `415` | tipo de fichero no permitido |
| `429` | rate limit de login o contacto (con `Retry-After`) |

## Rate limiting

Todo con `INCR`+`EXPIRE` en Redis (ventana fija, `Retry-After` en segundos restantes de ventana):

| Recurso | Clave | Umbral |
|---|---|---|
| Login | `rl:login:ip:{ip}` | 5 fallos / 15 min (`APP_LOGIN_RATE_MAX_IP`, ventana `APP_LOGIN_RATE_WINDOW`) |
| Login | `rl:login:email:{email}` | 10 fallos / 15 min (`APP_LOGIN_RATE_MAX_EMAIL`) |
| Contacto | `rl:contact:ip:{ip}` | 5 envíos / 15 min (`APP_CONTACT_RATE_MAX_IP`, `APP_CONTACT_RATE_WINDOW`) |

Notas: el login se **resetea al autenticar OK** (borra claves IP+email). El de contacto **no** se
resetea; expira con la ventana. El bloqueo de login se evalúa antes de autenticar (un bloqueo por
IP afecta a cualquier email desde esa IP). `server.forward-headers-strategy: framework` confía en
`X-Forwarded-For` tras proxy.

---

## Autenticación (`/auth`)

### POST `/auth/login`
Público. Body: `{ "email": "...", "password": "..." }` (ambos obligatorios).
- `200` → `{ "accessToken": "<jwt>", "tokenType": "Bearer", "expiresIn": 900 }`
  (Segundos) + `Set-Cookie: refresh_token=...; Path=/auth; HttpOnly; SameSite=Lax; Max-Age=604800`.
- `202` → `{ "challengeId": "<uuid>" }` cuando el OTP está activo para el usuario
  (feature flag `APP_AUTH_OTP_ENABLED=true` **y** `users.otp_enabled`): no se emiten tokens
  todavía, hay que llamar a `/auth/login/verify`.
- `400` validación (con `errors[]`). `401` credenciales inválidas. `429` rate limit + `Retry-After`.

### POST `/auth/login/verify`
Público. Body: `{ "challengeId": "...", "code": "123456" }`.
- `200` → tokens + cookie, y aquí se registra la sesión y se actualiza `last_login_at`.
- `400` validación. `401` challenge caducado (5 min por defecto), código incorrecto o
  intentos agotados (5 por defecto, `APP_AUTH_OTP_MAX_ATTEMPTS`).

### POST `/auth/refresh`
Público. Usa la cookie `refresh_token`. Sin body.
- `200` → mismo cuerpo que login y nueva cookie (rotación).
- `401` si falta la cookie / token inválido-caducado-revocado / usuario inexistente /
  sesión revocada / `tv` desactualizado.

### POST `/auth/logout`
Público. Revoca access (Bearer) y refresh (cookie) y marca la sesión como revocada.
Siempre `204` (revocación best-effort).

### GET `/auth/me`
Requiere access token. `200` → `{ "email": "...", "role": "ADMIN" }`.

### GET `/auth/sessions`
Requiere access token. `200` → array de sesiones activas del usuario, de la más reciente a la más antigua:
`[{ "id": "<uuid>", "createdAt": "...", "lastSeenAt": "...", "expiresAt": "...",
"ipAddress": "...", "userAgent": "...", "current": true }]`. De paso purga las filas
caducadas o ya revocadas.

### POST `/auth/sessions/{id}/revoke`
Requiere access token. Marca esa sesión como revocada y mete su `refresh_jti` en la denylist.
`204` siempre (idempotente, también si no existe); `400` si el id no es un UUID.

### POST `/auth/sessions/revoke-others`
Requiere access token. Revoca todas las sesiones salvo la actual (claim `sid` del access token). `204`.

### POST `/auth/logout-all`
Requiere access token. Revoca **todas** las sesiones, sube `users.token_version` (invalida los
tokens ya emitidos) y limpia la cookie de refresh. `204`. Auditoría: `REVOKE` / `SESSION`.

### POST `/auth/reset/request`
Público. Body: `{ "email": "..." }`.
- `202` siempre (exista o no la cuenta, para no filtrar qué emails están dados de alta).
  Si existe, se genera un token de un solo uso (Redis `pwreset:{token}`, 30 min) y se envía el
  correo con el enlace `{APP_FRONT_RESET_URL}?token=...`.
- `400` email ausente o inválido. `429` rate limit por IP (`APP_RESET_RATE_MAX_IP` / `APP_RESET_RATE_WINDOW`).

### POST `/auth/reset/confirm`
Público. Body: `{ "email": "...", "token": "...", "password": "..." }` (mínimo 8 caracteres).
- `204` → contraseña actualizada, `token_version` subido y sesiones revocadas.
- `400` validación. `401` token caducado, ya usado o de otro email.
  Auditoría: `RESET` / `USER`.

---

## Público (`/public`, con caché ETag)

Respuestas con `ETag` (SHA-256 del body) y `Cache-Control: public, max-age=0, must-revalidate`.
`If-None-Match` → `304` sin body. Idioma: `?lang=es|en` (default `es`, fallback localizable
sin señal al cliente). Claves Redis `pub:*` (TTL `APP_PUBLIC_CACHE_TTL`, 300 s); evicts en
escrituras de admin que tocan contenido publicado.

### GET `/public/profile` · GET `/public/projects` · GET `/public/projects/{slug}`
- `?lang` aplica. `{slug}` inexistente o sin versión publicada → `404` (igual para draft).
- `200` con la entidad/serialización pública (project detail incluye `es`/`en` resuelto).

### GET `/public/certificates`
- Filtros combinables (AND), case-insensitive: `kind` (`certificate`|`course`, minúsculas) e
  `issuer` (igualdad exacta). `kind` inválido → `400`. Sin coincidencias → `200 []` (nunca 404).
- Orden: `sortOrder ASC → issueDate DESC (NULLS LAST) → createdAt ASC`. Solo `PUBLISHED`.

### GET `/public/cv/{lang}`
- `302 Found` a la URL del CV del profile (nunca 301/308: la URL cambia al re-subir). `{lang}`
  es|en con fallback entre idiomas; `404` si no hay CV. Sin ETag (respuesta de redirección).

### POST `/contact`
Público. Body `ContactRequest`: `name`(≤120, req.), `email`(@Email ≤320, req.), `subject`(≤160,
req.), `body`(10..5000, req.), `website`(honeypot opcional).
Orden: validación → **honeypot** (`website` relleno = `201` falso idéntico, no persiste) → rate
limit por IP → persistir `Message` (IP anonimizada) → notificación por email **best-effort async**
(si falla SMTP, sigue `201`; la BD manda).
- `201` éxito. `400` validación / body inválido. `429` + `Retry-After`.
- La notificación es `multipart/alternative` (texto plano + HTML) renderizada con plantillas
  **Thymeleaf** (`templates/mail/contact.html`, estilo inline y tablas ~600px). El HTML se
  escapa con `th:text` (nunca `th:utext` con datos de usuario). `replyTo` al remitente y
  asunto `[Contacto] …`. Si `APP_CONTACT_FROM_EMAIL` o `APP_CONTACT_DEST_EMAIL` están vacíos
  no se envía correo.
- Tras persistir y lanzar la notificación, el visitante recibe un **acuse** (`mail/ack.html`,
  `multipart/alternative`): idioma por `Accept-Language` (`en` → inglés; cualquier otro →
  español). Se controla con `APP_CONTACT_SEND_ACK` (default `true`; `false` = solo la
  notificación al admin). Ambos envíos son best-effort async.

---

## Admin (`/admin/**`, `ROLE_ADMIN`)

Salvo `POST /admin/storage`, las respuestas devuelven la **entidad** (sin DTO admin).
`404` con `ResponseStatusException`. Escrituras con evict de caché pública solo si tocan algo
**publicado** (listado + detalle, con slug viejo y nuevo si se renombra).

**Auditoría (Sesión 2 develop)**: toda escritura de admin (proyectos, certificados, perfil,
mensajes, subidas de storage) queda registrada en la tabla `audit_log`
(actor `id`/`email` desde el JWT, `action` `CREATE|UPDATE|DELETE|READ|ARCHIVE|UPLOAD`,
`resource_type`, `resource_id`, `detail`, `created_at`). Sin actor autenticado ⇒
`actor_email='SISTEMA'`. La limpieza automática de orfanatos no se audita (sin actor).
La auditoría es write-only (sin endpoint de lectura) de momento.

### Projects
- `GET /admin/projects` → lista (todo status, `sortOrder ASC → createdAt ASC`).
- `GET /admin/projects/{id}` → detalle por UUID. `404` si no existe.
- `POST /admin/projects` → crea **siempre DRAFT**. Body `ProjectRequest`: `slug` (req.,
  `^[a-z0-9]+(-[a-z0-9]+)*$` ≤120, **único → `409`**), `title`/`subtitle`/`summary`/`descriptionMd`
  (`title` exige `es` o `en` no vacío), `thumbnailUrl`, `gallery[]`, `repoUrl`, `demoUrl`,
  `stack[]`, `periodStart`, `periodEnd`, `featured`, `sortOrder`.
- `PUT /admin/projects/{id}` → edición completa (mantiene `status`/`published_at`; ausentes =
  null salvo `sortOrder`→0 y `gallery`/`stack`→`[]`).
- **`featured` es `boolean` primitivo y Spring Boot 4.x mantiene
  `FAIL_ON_NULL_FOR_PRIMITIVES=true`**: omitirlo en el JSON da `400 "Failed to read request"`
  (no validación, es error de parseo). Enviar siempre `"featured": true|false`.
- `PATCH /admin/projects/{id}/publish` → `published_at=now()`. `PATCH .../unpublish` → limpia.
- `PUT /admin/projects/order` → body `[uuid, ...]` ordenado (posición = índice); `404` si alguno
  no existe.
- `DELETE /admin/projects/{id}` → `204`. Al borrar (`DELETE`) o cambiar
  `thumbnailUrl`/`gallery[]` (`PUT`), las URLs que ya no use nadie se borran de `uploads/`
  (limpieza de orfanatos).

### Certificates
- `GET /admin/certificates` → lista (todo status).
- `GET /admin/certificates/{id}` → `404` si no existe.
- `POST` crea **DRAFT**. Body `CertificateRequest`: `title`, `issuer` (req.), `kind` (req.,
  **`CERTIFICATE`/`COURSE` en mayúsculas** — el binding de Jackson es case-sensitive y
  `certificate`/`course` → `400`; en público se publica en minúsculas), `issueDate`, `expiryDate`,
  `credentialUrl`, `imageUrl`, `featured`, `sortOrder`.
- `PUT /{id}` edición completa · `PATCH /{id}/publish` / `unpublish` · `PUT /order` · `DELETE` `204`.
- Al borrar (`DELETE`) o cambiar `imageUrl` (`PUT`), el fichero antiguo de `uploads/` se
  elimina **si y solo si** ningún otro registro lo referencia (limpieza de orfanatos).

### Messages
- `GET /admin/messages?status=NEW|READ|ARCHIVED&page=0&size=20` — **paginado (Bloque 8)**:
  respuesta `{items: Message[], page, size, totalElements, totalPages, last}`. `status`
  opcional (sin filtro → todo); orden fijo determinista `createdAt DESC, id DESC` (desempate
  por UUID v7). `page` 0-based (default `0`), `size` 1..100 (default `20`); `page<0` o
  `size` fuera de rango → `400`; `status` inválido → `400`.
- `GET /admin/messages/{id}` → detalle (no muta estado). `DELETE` → `204`.
- `GET /admin/messages/unread-count` → `200 {"count": N}` (**Sesión 2 develop**):
  contador de mensajes con status `NEW` para el badge del panel. Sin parámetros.
- `PATCH /admin/messages/{id}/read` y `.../archive` → transiciones libres e idempotentes
  (any→READ / any→ARCHIVED; repetir = `200` no-op). Sin evicts de caché
  (messages no se sirve en público).

### Profile
- `GET /admin/profile` → entidad (para precargar el formulario).
- `PUT /admin/profile` → body `ProfileRequest`: `fullName` (req.), `headline`/`bio`
  (`es`/`en`, al menos una), `location`, `githubUrl`/`linkedinUrl`/`websiteUrl`/`cvUrlEs`/`cvUrlEn`/
  `avatarUrl` (`@URL` ≤255), `emailPublic` (`@Email` ≤320), `skills[{name,category,level}]`,
  `experiences[]`. `views_count` y timestamps se ignoran. Cada PUT → evict de `pub:profile:*`.
  `404` si la fila id=1 no existe (no debería ocurrir).
- Al cambiar `avatarUrl`/`cvUrlEs`/`cvUrlEn`, las URLs antiguas que ya no use nadie se borran
  de `uploads/` (limpieza de orfanatos).

### Storage (`POST /admin/storage?use={avatar|thumbnail|gallery|image|cv}`)
Multipart: parte `file` **obligatoria** (`@RequestPart`) + `use` por **query o form field, nunca
ambos** (si Spring recibe el mismo parámetro duplicado los une con coma → `400` "Uso no permitido").
- `200` → `{ "url": "https://.../files/<uuid>.<ext>" }` (montado con `APP_STORAGE_PUBLIC_URL`);
  URL servida por `GET /files/**` (público, estático).
- Validación por **magic bytes** (no extensión): `jpeg/png/webp`; `pdf` **solo para `cv`** → sino
  `415`. Tamaño `413`: 5 MB imágenes / 10 MB CV (y globales multipart 15/20 MB del contenedor).
- `400`: fichero vacío ("Se requiere un fichero no vacío"), `use` inválido, parte `file` ausente o
  request no multipart ("Petición no codificada como multipart/form-data").
- No muta entidades ni hace evicts; sin borrado de orfanatos por ahora.

### OTP (`PATCH /admin/otp`)
- Body `{ "enabled": true | false }` → `204`. Activa/desactiva el segundo factor por email del
  propio admin (columna `users.otp_enabled`). Solo surte efecto con el feature flag global
  `APP_AUTH_OTP_ENABLED=true`; con el flag apagado el login sigue siendo de un solo paso.
  `400` si falta `enabled`. Auditoría: `UPDATE` / `USER` con detalle `otp-enabled:<valor>`.

## Hardening por entorno (Sesión 5)

Al arrancar, la API revisa la configuración y escribe `WARN` en el log (`StartupSecurityWarnings`):

- `APP_JWT_SECRET` y `APP_JWT_REFRESH_SECRET` **deben ser distintos** y aleatorios por entorno
  (`openssl rand -base64 64`); avisa si coinciden o si parecen de desarrollo.
- `APP_JWT_REFRESH_COOKIE_SECURE=true` en producción: la cookie de refresh solo viaja por HTTPS.
- Si `APP_AUTH_OTP_ENABLED=true`, hace falta `APP_CONTACT_FROM_EMAIL` (remitente verificado)
  para poder entregar los códigos.

Variables de la sesión: `APP_FRONT_RESET_URL`, `APP_RESET_RATE_MAX_IP`, `APP_RESET_RATE_WINDOW`,
`APP_AUTH_OTP_ENABLED`, `APP_AUTH_OTP_TTL`, `APP_AUTH_OTP_MAX_ATTEMPTS` (ver `.env.example`).

## Comprobación rápida

```bash
curl -s "http://localhost:8080/public/profile" -D - -o /dev/null
curl -s -X POST http://localhost:8080/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"...","password":"..."}'
# error uniforme:
curl -s http://localhost:8080/auth/refresh -X POST          # 401 ProblemDetail
curl -s http://localhost:8080/files/inexistente.png        # 404 ProblemDetail
curl -s "http://localhost:8080/v3/api-docs" | python3 -m json.tool | head
```