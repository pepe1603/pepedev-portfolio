# Sesión 10 · API: Bloque 6.3 — CRM storage + profile

> Fecha: 2026-09-23 · Estado: **implementación cerrada · DoD PENDIENTE de verificación manual
> (Postman)**. Bloque 6.3 completo: subida de ficheros a storage local servido por la propia API
> y edición del profile singleton.

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
  - Fichero vacío / `use` inválido / falta de parte → **400** (`use` se recibe como `String` y se
    pasa a `StorageUse.valueOf(use.toUpperCase(Locale.ROOT))`; `@RequestPart` obligatorio).
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

## Verificación (PENDIENTE — humano con Postman)

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

## Notas de operación

- Túnel: `ssh -L 5432:localhost:5432 -L 6379:localhost:6379 teramont-dev`.
- Arranque dev: `setsid nohup bash -c 'MANAGEMENT_HEALTH_MAIL_ENABLED=false ./mvnw spring-boot:run'`
  desde `portfolio-api/`; detener con `pkill -f spring-boot:run`.
- El directorio de uploads (`APP_STORAGE_DIR`) se crea solo al arrancar la app; los ficheros
  quedan en `uploads/` junto al repo (gitignored). Sin borrado de orfanatos por ahora.
- Recordatorio Boot 4/Jackson 3: las respuestas `Map` de `{ "url": ... }` se serializan con el
  `ObjectMapper` de Jackson 3 configurado por Boot (sin código propio).
- Errores 4xx siguen saliendo por `BasicErrorController` (formato unificado → Bloque 7).

## Hoja de ruta viva (detalle en docs/PLAN-API.md)

0-3 ✔ · 4 Auth JWT ✔ · 5.1-5.3 ✔ · 6.1 CRM CRUD ✔ · 6.2 bandeja de mensajes ✔ ·
**6.3 storage+profile ✔ (implementado; DoD pendiente de verificación)** · 7 Contrato OpenAPI.

---

## Prompt para la siguiente sesión (copiar/pegar)

```
Retomamos el proyecto pepedev-portfolio (docs en /home/pepe-dev/Projects/pepedev-portfolio).

Contexto cerrado (lee en orden docs/SESION10.md, docs/PLAN-API.md y docs/MODELO-DATOS.md):
- Bloques 0-3, 4 (auth JWT), 5.1-5.3 COMPLETOS y verificados. 6.1, 6.2 y 6.3 (storage + profile)
  IMPLEMENTADOS; DoD de 6.1 y 6.2 VERIFICADO manualmente (Postman), el de 6.3 PENDIENTE.
  POST /admin/storage?use= → { "url" } (magic bytes, límites 5/10 MB, UUID+ext, sin evicts),
  GET /admin/profile y PUT /admin/profile (evict pub:profile ES+EN; CV reutiliza esa caché).
- /admin/** con hasRole(ADMIN); /files/** público sirviendo APP_STORAGE_DIR desde local FS.
- Reglas: sin Docker en dev; secrets solo por .env; Flyway validate; bilingüe JSONB {es,en};
  túnel ssh para BD/Redis; errores 4xx por BasicErrorController hasta Bloque 7.

Notas de operación: .env real limpio y source .env OK (APP_STORAGE_DIR + APP_STORAGE_PUBLIC_URL);
health SMTP DOWN en dev → MANAGEMENT_HEALTH_MAIL_ENABLED=false; background con setsid nohup...
spring-boot:run; pkill -f spring-boot:run; Boot 4 rompe APIs de Boot 3 (Sort.Order,
redis.delete(Collection), CacheControl, nullsFirst/Last); @URL es de Hibernate Validator, no de
jakarta.validation.constraints.

Método de trabajo (pair programming / mentoría):
- Yo (humano) desarrollo la API poco a poco, clase por clase, siguiendo docs/PLAN-API.md.
- Tú (IA) eres mi mentor: revisas mi código, me dices si está bien y qué corregir y por qué.
- Solo escribes tú el código cuando te lo pida explícitamente, y después me lo explicas para que aprenda.
- Se avanza commit por commit (UN commit por pieza para más control).

Tarea de la próxima sesión:
- (PDTE) Verificar manualmente (Postman) el DoD del Bloque 6.3 con la guía de docs/SESION10.md.
- Bloque 7: Contrato y cierre (docs/API.md alineado con lo implementado + respuestas de error
  unificadas + Swagger verificado).
```

---

*Fin de la sesión 10.*