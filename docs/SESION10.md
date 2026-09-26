# Sesión 10 · API: Bloque 6.3 — CRM storage + profile

> Fecha: 2026-09-23 (implementación) / 2026-09-24 (verificación) · Estado: **implementación
> cerrada · DoD VERIFICADO manualmente (curl)**. Bloque 6.3 completo: subida de ficheros a storage
> local servido por la propia API y edición del profile singleton.

## Qué se hizo en esta sesión

El humano decidió añadir primero un `GET /admin/profile` para que el front cargue el estado antes
de editar (pieza 0) y delegó en la IA el resto del bloque, avanzando commit por commit:

| Commit | Pieza |
|---|---|
| `c20df78` | `GET /admin/profile` (service + controller, lee la entidad id=1) |
| `70a0e3f` | Config de storage: `StorageProperties`, límites multipart, resource handler `/files/**`, `/files/**` público, `.env` `APP_STORAGE_*` |
| `3abdc27` | `StorageUse` + `StorageService` (magic bytes, tamaños, UUID+ext, guardado, URL) |
| `eca0968` | `AdminStorageController` `POST /admin/storage` → `{ "url" }` |
| `c0eb431` | `ProfileRequest` DTO con validación |
| `360caaf` | `AdminProfileService.update` + evict de caché pública |
| `45103c0` | `AdminProfileController` `PUT /admin/profile` @Valid |
| `ad56ad1` | Fix verificación: falta de parte `file` o fichero vacío → **400** (era 500). `upload/` gitignored |
| (docs) | PLAN-API + este documento con la guía de verificación |

## Decisiones cerradas en esta sesión (Bloque 6.3)

- **Storage en local FS**: `APP_STORAGE_DIR` + `APP_STORAGE_PUBLIC_URL`; los ficheros los sirve
  la propia API (`GET /files/**` estático y **público** en `SecurityConfig`). Sin Docker ni
  S3/MinIO. `.env.example` y `.env` real renombran `APP_UPLOAD_DIR` → `APP_STORAGE_DIR` y añaden
  `APP_STORAGE_PUBLIC_URL=http://localhost:8080/files`. `APP_STORAGE_DIR` **sin default** (fail
  fast); el directorio se crea al arrancar (`@PostConstruct` de `StorageService`).
- **Endpoint único `POST /admin/storage?use={avatar|thumbnail|gallery|image|cv}`**: multipart,
  devuelve `{ "url" }` (URL absoluta = `APP_STORAGE_PUBLIC_URL` + `/` + UUID.ext). El front
  persiste la URL con el CRUD existente (6.1). El upload **no muta entidades ni hace evicts**.
- **Validación**:
  - **Magic bytes** (nunca el `contentType` del cliente): JPEG `FF D8 FF`, PNG
    `89 50 4E 47 0D 0A 1A 0A`, WEBP `RIFF....WEBP`, PDF `%PDF`. `use=cv` → solo PDF; resto →
    solo imágenes. Tipo no permitido → **415**.
  - **Tamaño**: 5 MB imágenes / 10 MB CV → **413**. Límites globales multipart
    `spring.servlet.multipart` 15/20 MB (holgados a propósito: el parser no debe rechazar antes
    que el negocio, y 10 MB exactos falla por el overhead del boundary).
  - **Nombre** generado por servidor (UUID v4 aleatorio + extensión normalizada del tipo
    detectado: jpg/png/webp/pdf) → sin colisiones ni path traversal.
  - Fichero vacío / `use` inválido / falta de parte / request no multipart → **400**. `use` se
    recibe como `String` y se pasa a `StorageUse.valueOf(use.toUpperCase(Locale.ROOT))`.
  - **CORRECCIÓN (Bloque 7)**: la parte `file` es `@RequestPart MultipartFile` **obligatoria**.
    El workaround anterior `required=false` + check explícito se **revertió** (`1c7e09d`): el 500
    real era porque el advice no tenía `@ExceptionHandler(MultipartException.class)`. El advice
    global SÍ resuelve (ver Piezas 1-4): "no codificado como multipart" y "falta la parte 'file'"
    → 400, fichero vacío → 400 (lo valida `StorageService.store`).
  - **Sin borrado de orfanatos** por ahora.
- **`GET /admin/profile`** (pieza 0, añadida por el humano): devuelve la entidad `Profile` para
  que el front pueble el formulario; 404 `ResponseStatusException` si la fila id=1 no existiera.
- **`PUT /admin/profile`** (completo, como 6.1): `full_name` (@NotBlank @Size 120), `headline` y
  `bio` (@LocalizedNonBlank), `location` (@Size 120), URLs `github_url/linkedin_url/website_url/
  cv_url_es/cv_url_en/avatar_url` (@Size 255 + `@URL` de **Hibernate Validator** — `@URL` no
  existe en `jakarta.validation.constraints`), `email_public` (@Size 320 + `@Email`),
  `skills[{name,category,level}]` y `experiences` (@Size 50; null → lista vacía, coherencia con
  `NOT NULL DEFAULT '[]'::jsonb`). `views_count` y timestamps **se ignoran silenciosamente**
  (ausentes del DTO; Jackson en Boot descarta lo desconocido por defecto, así que mandarlos en el
  body tampoco falla). 404 si la fila id=1 no existiera. Cada PUT → `evictProfile()` (ES+EN); el
  **CV reutiliza la caché `pub:profile:{lang}`** (`CvController`), por eso no requiere evict
  propio. Perfil siempre es público → el evict es **incondicional**.

## Verificación (VERIFICADO — 2026-09-24, curl contra localhost:8080)

Base `http://localhost:8080`. Primero obtén un access token:

```
curl -s -X POST http://localhost:8080/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"000316jose@gmail.com","password":"..."}'
# copia accessToken del body y úsalo como: -H "Authorization: Bearer <token>"
```

Necesitas ficheros de prueba: `foto.jpg`, `foto.png`, `foto.webp` (si no tienes webp, vale un
png renombrado a `.webp` NO debe pasar — el check es por bytes), `cv.pdf`, y un `.txt` renombrado
a `foto.png` (debe dar 415). Si no tienes >5MB o >10MB, genéralos con
`dd if=/dev/urandom of=grande.bin bs=1M count=6`. Importante: **el `contentType` que mande el
cliente no importa** — solo los bytes (el test del `.txt` renombrado a `.png` lo demuestra).

1. `POST /admin/storage?use=avatar` con `foto.png` **sin** token → **401**.
2. `POST /admin/storage?use=avatar` con `foto.png` y token → **200** con `{"url": "http://localhost:8080/files/<uuid>.png"}`.
3. `GET /files/<uuid>.png` sin token → **200** y sirve la imagen (público, sin auth).
4. `POST /admin/storage?use=image` con `foto.jpg` → **200** con URL `.jpg`.
5. `POST /admin/storage?use=cv` con `cv.pdf` → **200** con URL `.pdf`.
6. `POST /admin/storage?use=avatar` con `cv.pdf` → **415** (PDF no vale como imagen).
7. `POST /admin/storage?use=cv` con `foto.jpg` → **415** (imagen no vale como CV).
8. `POST /admin/storage?use=avatar` con el `.txt` renombrado a `foto.png` → **415** (bytes, no contentType).
9. `POST /admin/storage?use=image` con `grande.bin` (>5MB) → **413** (payload demasiado grande).
   Importante: `grande.bin` debe **tener un header válido** (p. ej. `printf '\x89PNG\r\n\x1a\n' >
   grande.bin` y luego `dd if=/dev/urandom bs=1M count=6 >> grande.bin`); si son bytes aleatorios
   puros el check de tipo gana antes y da 415 (comportamiento correcto, tipo antes que tamaño).
10. `POST /admin/storage?use=avatar` sin parte `file` → **400**; con `?use=foo` → **400**; sin `?use` → **400**.
11. `GET /admin/profile` sin token → **401**; con token → **200** con la entidad (perfil actual).
12. `PUT /admin/profile` sin token → **401**; **con `@Valid`**: body malo → **400**
    (ejemplos: `full_name` vacío; `headline` sin ni `es` ni `en`; `email_public` no-email;
    `github_url` sin protocolo, p. ej. `google.com` no pasa `@URL` — necesita `https://`).
13. `PUT /admin/profile` con token y body completo válido → **200** con la entidad actualizada y
    `updated_at` nuevo (JPA). Incluye `views_count: 999` y timestamps en el body → **se ignoran**
    (el `views_count` de la respuesta sigue igual).
14. Tras el PUT: `GET /public/profile?lang=es` y `?lang=en` reflejan el nuevo `headline`/`bio`
    (el evict `pub:profile` ES+EN ha funcionado; sin él, la caché daría valores viejos hasta el
    TTL). Repite el PUT y comprueba que el cambio se ve inmediatamente.
15. (Opcional) Con `cv_url_es` apuntando a la URL del PDF subido: `GET /public/cv/es` → **302**
    con `Location` a esa URL (ahora servida por `/files/**`). Nota: la URL del PDF sirve una
    respuesta 302->200 (no es un redirect a otro host).
16. `GET /admin/profile` después del PUT → **200** y devuelve lo guardado (el front rellena el
    formulario con esto).

### Resultado de la verificación (todas ✔)

| Paso | Esperado | Obtenido |
|---|---|---|
| 1 | 401 | 401 ✔ |
| 2 | 200 + url `/files/<uuid>.png` | 200 ✔ (`3234d884-...png`) |
| 3 | GET `/files/<uuid>.png` 200 público | 200 ✔, contentType correcto, **bytes idénticos** al subido |
| 4-5 | image jpg / cv pdf 200 | 200 / 200 ✔ |
| 6-8 | 415 (pdf como avatar, jpg como cv, .txt→`foto.png`) | 415 / 415 / 415 ✔ |
| 9 | image >5MB → 413; cv >10MB → 413 | 413 / 413 ✔ (con header válido) |
| 10 | multipart sin parte → 400; `use=foo` → 400; sin `use` → 400 | 400 / 400 / 400 ✔ |
| 11 | GET `/admin/profile` 401 / 200 | 401 / 200 ✔ |
| 12 | 4 bodies inválidos → 400 | 400 ×4 ✔ |
| 13 | PUT completo + `viewsCount:999`+timestamps ignorados | 200 ✔, `viewsCount` sigue 0, `updatedAt` nuevo |
| 14 | evict: `/public/profile` es y en muestran lo nuevo | es=ES / en=EN inmediato ✔ |
| 15 | `/public/cv/es` → 302 con Location | 302 → `cv_url_es` ✔ |
| 16 | GET `/admin/profile` post-PUT | 200 ✔ |

Nota de prueba: `POST /admin/storage` **sin** `multipart/form-data` (ni parte ni contentType
multipart) → **400** tras el fix `ad56ad1` (antes 500). Los ficheros con header válido >15 MB aún
los corta el propio Tomcat (respuesta `100` + `413` sin body) antes de llegar a Spring; los límites
de negocio (5/10 MB) se evaluan antes, así que el comportamiento es consistente.

## Notas de operación

- Túnel: `ssh -L 5432:localhost:5432 -L 6379:localhost:6379 teramont-dev`.
- Arranque dev: `setsid nohup bash -c 'MANAGEMENT_HEALTH_MAIL_ENABLED=false ./mvnw spring-boot:run'`
  desde `portfolio-api/`; detener con `pkill -f spring-boot:run`.
- El directorio de uploads (`APP_STORAGE_DIR`) se crea solo al arrancar la app; los ficheros
  quedan en `uploads/` junto al repo (gitignored). Sin borrado de orfanatos por ahora.
- Recordatorio Boot 4/Jackson 3: las respuestas `Map` de `{ "url": ... }` se serializan con el
  `ObjectMapper` de Jackson 3 configurado por Boot (sin código propio).
- Errores 4xx siguen saliendo por `BasicErrorController` (formato unificado → Bloque 7).
- **RETRACTADO (Bloque 7)**: la afirmación de que `@ExceptionHandler`/`@ControllerAdvice` "no se
  ejecutan" en Boot 4.1.1/Spring 7.0.9 era **incorrecta**. El advice global
  (`config/ApiExceptionHandler extends ResponseEntityExceptionHandler`) resuelve y está
  **verificado en vivo**: `ResponseStatusException` de negocio, validación 400 con `errors[]`,
  `MultipartException` (no-multipart / parte ausente), tamaño excedido, `BadCredentialsException`
  401, ratelimits 429 con `Retry-After`, `NoResourceFoundException` 404 — todo como `ProblemDetail`
  RFC 9457 con `instance` = path. Detalle clave de Spring 7.0.9: `handleExceptionInternal(ex,
  null, ...)` solo rellena el body si la excepción es `ErrorResponse` (por eso `MultipartException`,
  bad-credentials y ratelimits necesitan `ProblemDetail` explícito) y `MethodArgumentNotValidException.
  getBody()` no incluye los `errors[]` (por eso el override). El 500 del storage se debía a **no
  haber handler de `MultipartException`**, no a que el mécanismo estuviera roto. Mantener igualmente
  `ResponseStatusException` para el negocio (estilo claro), pero ya no "como workaround".
- `.env`: la línea de `SPRING_MAIL_PASSWORD` tenía el valor sin comillas (bash la interpretaba:
  "rcyh: orden no encontrada"); envuelta en comillas simples en la sesión de verificación.`

## Hoja de ruta viva (detalle en docs/PLAN-API.md)

0-3 ✔ · 4 Auth JWT ✔ · 5.1-5.3 ✔ · 6.1 CRM CRUD ✔ · 6.2 bandeja de mensajes ✔ ·
**6.3 storage+profile ✔ (implementado y DoD VERIFICADO)** · 7 Contrato OpenAPI.

---

## Prompt para la siguiente sesión (copiar/pegar)

> **NOTA (Bloque 7): este prompt quedó superado. La afirmación sobre `@ExceptionHandler` era falsa**
> (retractada arriba, ver `docs/SESION11.md` y `docs/API.md`). Se conserva como historial.

```
Retomamos el proyecto pepedev-portfolio (docs en /home/pepe-dev/Projects/pepedev-portfolio).

Contexto cerrado (lee en orden docs/SESION10.md, docs/PLAN-API.md y docs/MODELO-DATOS.md):
- Bloques 0-3, 4 (auth JWT), 5.1-5.3 COMPLETOS y verificados. 6.1, 6.2 y 6.3 (storage + profile)
  IMPLEMENTADOS y con DoD VERIFICADO manualmente (Postman/curl).
  POST /admin/storage?use= → { "url" } (magic bytes, límites 5/10 MB, UUID+ext, sin evicts;
  falta de parte/fichero vacío → 400 por el advice global),
  GET /admin/profile y PUT /admin/profile (evict pub:profile ES+EN; CV reutiliza esa caché).
- /admin/** con hasRole(ADMIN); /files/** público sirviendo APP_STORAGE_DIR desde local FS
  (uploads/ gitignored).
- Reglas: sin Docker en dev; secrets solo por .env; Flyway validate; bilingüe JSONB {es,en};
  túnel ssh para BD/Redis; errores 4xx por BasicErrorController hasta Bloque 7.

Notas de operación: .env real limpio y source .env OK (APP_STORAGE_DIR + APP_STORAGE_PUBLIC_URL;
SPRING_MAIL_PASSWORD comillada); health SMTP DOWN en dev → MANAGEMENT_HEALTH_MAIL_ENABLED=false;
background con setsid nohup... spring-boot:run; pkill -f spring-boot:run; Boot 4 rompe APIs de
Boot 3 (Sort.Order, redis.delete(Collection), CacheControl, nullsFirst/Last); @URL es de Hibernate
Validator, no de jakarta.validation.constraints. IMPORTANTE: @ExceptionHandler/@ControllerAdvice
NO se ejecutan en este proyecto (verificado); usar ResponseStatusException. Investigar la causa de
esto en Bloque 7.

Método de trabajo (pair programming / mentoría):
- Yo (humano) desarrollo la API poco a poco, clase por clase, siguiendo docs/PLAN-API.md.
- Tú (IA) eres mi mentor: revisas mi código, me dices si está bien y qué corregir y por qué.
- Solo escribes tú el código cuando te lo pida explícitamente, y después me lo explicas para que aprenda.
- Se avanza commit por commit (UN commit por pieza para más control).

Tarea de la próxima sesión:
- Bloque 7: Contrato y cierre (docs/API.md alineado con lo implementado + respuestas de error
  unificadas + Swagger verificado). Investigar primero por qué @ExceptionHandler no resuelve en
  esta combinación Boot 4.1.1/Spring 7.0.9 (reproducción mínima) antes de elegir el diseño de
  errores.
```

---

*Fin de la sesión 10.*