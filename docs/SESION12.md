# Sesión 12 — Cierre de la deuda documentada (2026-09-26)

> Tras la tanda de tests (Piezas 1-5: **54 tests verdes** commiteados en `fe63163`,
> `ebf2811`, `591c91d`, `d23144e` y verificados en vivo) y con `docs/API.md` ya alineado,
> se ataca a la vez **toda** la deuda que quedó anotada en `PLAN-API.md`/`API.md`
> ("por ahora", "sin paginación", "sin borrado de orfanatos", gap de `/error`, swagger incompleto,
> sin runbook de producción). **Un commit por pieza.** Suite final: **86 tests, 0 fallos**.

## PIEZAS

| # | Deuda | Cierre | Commit |
|---|---|---|---|
| A | Bandeja **sin paginación** | `/admin/messages` paginado (`PageResponse`: `items/page/size/totalElements/totalPages/last`), `page` 0-based, `size` 1..100, orden determinista `createdAt DESC, id DESC`, índice `idx_messages_status_created_at` (migración V2). Slice `AdminMessageListTest` (7 casos) | `77450cb feat(api): paginacion en /admin/messages con orden determinista (Bloque 8)` |
| B | Storage **sin borrado de orfanatos** | `StorageService.deleteByUrl`/`baseName` (patrón `UUID.ext`, anti-traversal), `StorageReferenceChecker`, `OrphanFileCleaner`; cableado en `AdminProjectService.{update,delete}`, `AdminCertificateService.{update,delete}`, `AdminProfileService.update`. Pre-captura de URLs viejas → borrado **solo si nadie más las referencia**, best-effort dentro de la transacción. 13 tests nuevos | `b21cd7b feat(api): limpieza de ficheros huerfanos en uploads/ al borrar o sustituir` |
| C | **Gap de errores a nivel contenedor** | `JsonErrorController implements ErrorController` (reemplaza al `BasicErrorController` de Boot 4: paquete `org.springframework.boot.webmvc.error`): `/error` → ProblemDetail JSON sin `type/timestamp/path/trace`, `detail` en español, `server.error.include-message=never`. `handleMaxUploadSizeExceededException` override → 413 con `detail` propio (antes 400 vía `MultipartException`). Hallazgo: el **título 413 en Spring 7 es `Content Too Large`** (RFC 9110), no `Payload Too Large`. Los 404 de recurso estático no pasan por `/error` (`NoResourceFoundException` ya es ProblemDetail de Framework 7) | `e70175f feat(api): cierre del gap de errores a nivel contenedor (/error JSON + 413 multipart)` |
| D | Swagger **sin `errors[]`/multipart** | Schemas `ApiProblemDetail` + `ErrorField` (autogenerados por springdoc) y `@ApiResponse` del envelope en `login`, `contact`, `upload` (400/413/415) | `07b239e docs(api): swagger documenta envelope de error ...` |
| E | Producción **sin runbook** | `docs/PRODUCCION.md`: env vars de producción, build + systemd, proxy TLS (Caddy/nginx + `forward-headers-strategy`), checklist de verificación (Secure cookie, 413 JSON), operación (Flyway inmutable, backups, rotación JWT). CORS (`APP_CORS_ALLOWED_ORIGINS`) y cookie `Secure` (`APP_JWT_REFRESH_COOKIE_SECURE`) ya eran configurables por env (`.env.example`) | — (docs) |
| F | Anexos pendientes | Anexo de cierre en `PLAN-API.md` + `SESION12.md` | — (docs) |

## HALLAZGOS DE IMPLEMENTACIÓN (C)

- **`MaxUploadSizeExceededException`**: está en la lista de `handleException` de
  `ResponseEntityExceptionHandler`, por lo que **no se puede declarar un `@ExceptionHandler`
  propio** (ambiguo). La vía correcta es **`@Override handleMaxUploadSizeExceededException(...)`**
  (Spring 6.1+).
- **Spring Framework 7**: `HttpStatus.PAYLOAD_TOO_LARGE.getReasonPhrase()` = `Content Too Large`.
- **`ErrorController`/`ErrorAttributes` cambiaron de paquete** en Boot 4:
  `org.springframework.boot.webmvc.error.*` (no `...web.servlet.error.*`).
- Registrar un bean `ErrorController` desactiva `ErrorMvcAutoConfiguration` (y el whitelabel).

## VERIFICACIÓN EN VIVO (túnel relanzado, app en `:8080`, devtools)

Tras relanzar el túnel (`ssh -L 5432 ... -L 6379 ... teramont-dev`; estaba caído del lado del
VPS y la app colgaba en Hikari), `Flyway` aplicó la migración `V2` en arranque. Batería verificada
con curl (18 comprobaciones, todas OK; los 4 "fallos" iniciales eran del script de verificación,
no de la app):

| Zona | Resultado en vivo |
|---|---|
| Health / Flyway | `{"status":"UP"}` tras ~18s · **V2 (índice paginación) aplicada** |
| Público | `/public/profile` 200 con `ETag` + `Cache-Control` → `304` con `If-None-Match` |
| `404` estático | `/public/nope` → 404 ProblemDetail (`title: Not Found`, sin `type/trace`) |
| `/error` | directo → 401 por diseño (NO público; el dispatch interno ERROR sí `permitAll`); comportamiento del controller cubierto por `JsonErrorControllerTest` |
| Auth | `login {}` 400 `errors[2]` · credenciales mal → 401 · refresh sin cookie → 401 · admin login → `me role=ADMIN` |
| **Paginación (A)** | `/admin/messages?page=0&size=5` → `{items,page,size,totalElements:16,totalPages:4,last}` · `page=-1`, `size=101`, `status=oof` → 400 |
| **413 contenedor (C)** | `use=cv` con fichero 16 MB → **413** `"El fichero supera el tamaño máximo permitido"` (override) |
| **Orfanatos (B)** | upload `image` → fichero en `uploads/` → certificado DRAFT con esa `imageUrl` → `DELETE` 204 → **fichero borrado del disco** |
| `429` contacto | 5×201 → 6ª petición → 429 + `Retry-After: 900` |
| **Swagger (D)** | `/v3/api-docs` contiene `ApiProblemDetail`, `ErrorField` y `multipart/form-data` en storage · `swagger-ui` 302→200 |
| `/files/**` | URL generada se sirve con 200 y **bytes idénticos** |

Documento corregido en la verificación: `CertificateRequest.kind` del **admin** es case-sensitive
(`CERTIFICATE`/`COURSE` en mayúsculas; `certificate` → 400), aunque en público se publica en
minúsculas — actualizado en `docs/API.md`.

## ESTADO FINAL

- `git log --oneline` de la sesión: 6 commits (5 de código/tests + docs).
- Suite completa: `Tests run: 86, Failures: 0` · BUILD SUCCESS.
- Verificación en vivo: **plan completo OK** (piezas A-D y contrato general) contra la app real.
- `docs/API.md`, `docs/PLAN-API.md`, `docs/PRODUCCION.md` y los tests son ahora la única
  fuente de verdad (no queda deuda anotada pendiente).