# Sesión 11 — Bloque 7: contrato, errores unificados y Swagger (2026-09-25)

> Cierre del Bloque 7. **Todo verificado contra la app en ejecución** (`:8080`, devtools,
> curl/python). 6 commits (uno por pieza).

## RESUMEN DE CAMBIOS

| Pieza | Qué | Commit |
|---|---|---|
| 1 | Advice global `config/ApiExceptionHandler.java` (`extends ResponseEntityExceptionHandler`): errores de negocio, validación `errors[]`, multipart, bad-credentials, ratelimits, 404 — todo `ProblemDetail` RFC 9457 con `instance` = path | `15c2e0d feat(api): advice global de errores con ProblemDetail RFC 9457` |
| 2 | 401/403 de **filtros de seguridad** con el mismo shape (`security/SecurityErrorWriter` + `JwtAuthEntryPoint`/`JwtAccessDeniedHandler`), UTF-8 correcto | `bca8d80 fix(api): 401/403 de seguridad en formato ProblemDetail y UTF-8` |
| 3 | 429 de login/contacto y 401 bad-credentials **centralizados** en el advice (quitados los handlers locales con `Map`) | `4d916ab refactor(api): errores 429 y bad-credentials centralizados en el advice` |
| 4 | **Revert del workaround** `@RequestPart(required=false)` → `@RequestPart MultipartFile` obligatoria (el 500 era por no haber handler de `MultipartException`) | `1c7e09d refactor(api): revertido workaround de RequestPart en storage` |
| 5 | `docs/API.md`: contrato alineado al comportamiento real (error envelope unificado, auth+, ETag, rate limits, multipart storage, gaps conocidos) | `df9e683 docs(api): contrato de API alineado al comportamiento verificado` |
| 6 | Swagger: `/admin/storage` documentado como `multipart/form-data` (springdoc lo mostraba como `application/json`) sin tocar el runtime | `2007df8 fix(api): contrato multipart de /admin/storage correcto en OpenAPI` |

## RETRACCIÓN DEL HALLAZGO FALSO (SESION10)

SESION10 afirmaba que `@ExceptionHandler`/`@ControllerAdvice` **no se ejecutan** en Boot
4.1.1/Spring 7.0.9. **Es falso**: el advice global (`extends ResponseEntityExceptionHandler`)
resuelve y se verificó en vivo (400/401/403/404/413/415/429 como ProblemDetail). Notas corregidas
en `docs/SESION10.md` y `docs/PLAN-API.md`.

Causa real del `500` del storage: el advice anterior **no tenía** `@ExceptionHandler` de
`MultipartException`. Detalles de Spring 7.0.9 útiles:
- `handleExceptionInternal(ex, null, ...)` rellena el body solo si la excepción es `ErrorResponse`
  → multipart, bad-credentials y ratelimits (que no lo son) reciben `ProblemDetail` explícito.
- `MethodArgumentNotValidException.getBody()` devuelve `"Invalid request content."` **sin**
  `errors[]` → requiere override para exponer los errores de campo.
- `createResponseEntity` (sobrescrito) fija `instance` = `request.getRequestURI()`.

## CONTRATO DE ERROR VERIFICADO (RFC 9457)

```json
{ "type": "about:blank", "title": "Not Found", "status": 404,
  "detail": "Certificado no encontrado", "instance": "/admin/certificates/{id}" }
```

- Validación 400 → `"errors": [{"field","message"}]`.
- 429 → cabecera `Retry-After: <segundos>` (ventana Redis 900 s).
- 401/403 de filtros: mismo shape (sin `errors[]`), UTF-8.
- Sin `trace`, `timestamp`, `path`. **Gaps** (documentados en `docs/API.md`): rechazos a nivel de
  contenedor y cualquier no-previsto van por `/error` (BasicErrorController).

## SWAGGER

- `/v3/api-docs` OpenAPI 3.1.0 · `security: [{'bearer-auth': []}]` global correcto ·
  `/swagger-ui/index.html` 200.
- Gaps detectados y resueltos/fuera de alcance:
  - `/admin/storage` → **arreglado** (documentado multipart vía `@Operation`/`@RequestBody`,
    runtime intacto: no-multipart → 400 "Petición no codificada...", upload real → 200).
  - `springdoc` 3.1 usa swagger-annotations con `@Schema.properties = StringToClassMapItem[]`
    (API antigua; `SchemaProperty` no compila y provoca cascada "cannot find symbol" de Lombok).
  - Los paths públicos no declaran scheme alguno: el global aplica a todo; no se marcó
    override "sin auth" en `/public/**` (no bloquea nada, solo estética del candado en Swagger UI).

## OBSERVACIONES / DOC DEUDA

- `featured` de `ProjectRequest` es `boolean` primitivo y Boot 4.x mantiene
  `FAIL_ON_NULL_FOR_PRIMITIVES=true` → omitirlo da 400 "Failed to read request" (no validación).
  Documentado en `docs/API.md`; el front debe enviar `featured` siempre.
- `@RequestParam use` + form field `use` duplicado → Spring une con coma → ya valueOf → 400.
  No es bug; el front envía `use` una vez.
- Upload de control solo probó rutas; el fichero se borró de `uploads/`.
- `boot7-run.log` (untracked) → se añadió `*.log` a `.gitignore`.

## Prompt para la siguiente sesión (copiar/pegar)

```
Retomamos pepedev-portfolio (docs en /home/pepe-dev/Projects/pepedev-portfolio).

Contexto cerrado (lee en orden docs/SESION11.md, docs/API.md, docs/PLAN-API.md):
- Todos los bloques 0-7 COMPLETOS y verificados. API lista para el front.
- Error unificado ProblemDetail RFC 9457 en TODO: advice global ApiExceptionHandler + 401/403 de
  filtros con UTF-8 + 429 con Retry-After + validación con errors[] + instance=path.
  Gaps conocidos: rechazos a nivel de contenedor y no-previstos van por /error.
- docs/API.md es el contrato (auth Bearer + cookie refresh rotativa con denylist, ETag público,
  rate limits, multipart storage, 409 slug, featured obligatorio por FAIL_ON_NULL_FOR_PRIMITIVES).
- Swagger verificado: /v3/api-docs (OpenAPI 3.1.0, bearer-auth global) y /swagger-ui/index.html.
- Operación dev: tunnel ssh para PG/Redis; app en :8080 con devtools (auto-restart al compilar);
  log en portfolio-api/boot7-run.log (ahora gitignored con *.log). Redes/infra pendientes como en
  desarrollo: front Nuxt consumiría la API (CORS APP_CORS_ALLOWED_ORIGINS).

Sesiones y metodología: docs/SESION1..11, un commit por pieza, código lo escribo yo (IA) solo
cuando lo pides y lo explico después. `.env` real en raíz (source antes de curl).

Tareas futuras candidatas (NO bloquear nada más del backend):
- Front Nuxt (consumir /public/** + ETag, CRM contra /admin/**).
- Swagger UI decorativo: marcar como "no auth" las operaciones públicas (override de security).
- Borrado de orfanatos en uploads/ y paginación en mensajes (deuda anotada).
```

---

*Fin de la sesión 11 (Bloque 7 cerrado).*