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
  (Spring 6.1+). El advice Y el `SpringError` request-header del Título... (ya cubierto).
- **Spring framework 7**: `HttpStatus.PAYLOAD_TOO_LARGE.getReasonPhrase()` = `Content Too Large`.
- **`ErrorController`/`ErrorAttributes` cambiaron de paquete** en Boot 4:
  `org.springframework.boot.webmvc.error.*` (no `...web.servlet.error.*`).
- Registrar un bean `ErrorController` desactiva `ErrorMvcAutoConfiguration` (y el whitelabel).

## ESTADO FINAL

- `git log --oneline` de la sesión: 6 commits (5 de código/tests + docs).
- Suite completa: `Tests run: 86, Failures: 0` · BUILD SUCCESS.
- `docs/API.md`, `docs/PLAN-API.md`, `docs/PRODUCCION.md` y los tests son ahora la única
  fuente de verdad (no queda deuda anotada pendiente).