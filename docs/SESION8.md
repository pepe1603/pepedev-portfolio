# Sesión 8 · API: Bloque 6.1 — CRM CRUD de projects + certificates

> Fecha: 2026-09-18 · Estado: **implementación cerrada · DoD VERIFICADO manualmente (Postman)**
> (la realiza el humano con Postman usando la guía de esta misma sesión). Bloque 6.1 código
> completo: CRUD admin de `projects` y `certificates` bajo `/admin/**`.

## Qué se hizo en esta sesión

El humano aprobó el desglose y las decisiones **antes** de escribir código; después pidió que
**la IA escribiera el bloque completo** y se avanzó commit por commit:

| Commit | Pieza |
|---|---|
| `9850a42` | DTOs admin: `ProjectRequest` y `CertificateRequest` + constraint `@LocalizedNonBlank` |
| `ffe55c9` | `AdminProjectService` (slug/status/published_at/evicts) + `existsBySlug` en repo |
| `7caf9ae` | `AdminCertificateService` (análogo, sin slug) |
| `fa23b88` | `AdminProjectController` y `AdminCertificateController` bajo `/admin` |
| `e420af2` | `SecurityConfig`: `/admin/**` con `hasRole("ADMIN")` |
| `61ecbd7` | docs (este documento + PLAN-API.md) |

## Decisiones cerradas en esta sesión (Bloque 6.1)

- **Auth**: `/admin/**` exige `hasRole("ADMIN")`; el filtro JWT ya inyecta `ROLE_ADMIN` desde el claim `role`.
- **Endpoints** (projects y certificates con la misma forma):
  - `GET /admin/{resource}` → listado con **todo** status, `sortOrder ASC → createdAt ASC`.
  - `GET /admin/{resource}/{id}` → detalle por **UUID** (no slug: permite editar drafts).
  - `POST` → crear, **siempre `DRAFT`** → 201 + `Location`. `sortOrder` ausente = 0;
    `gallery`/`stack` ausentes = `[]`. Sin evict (invisible en público).
  - `PUT /{id}` → edición **completa** (optativos ausentes = null). **Mantiene `status` y
    `published_at`** (editar un publicado no lo despublica). Si estaba publicado → evict
    listado + detalle del slug viejo y del nuevo si se renombró.
  - `PATCH /{id}/publish` → `status=PUBLISHED`, `published_at=now()` (server-managed).
  - `PATCH /{id}/unpublish` → `status=DRAFT`, `published_at=null`.
  - `PUT /order` → lista **ordenada** de UUIDs; posición = índice; **404 si alguno no existe**
    (rollback del lote, `@Transactional`); evict solo del listado.
  - `DELETE /{id}` → **hard delete**, 204; evict si estaba publicado.
- **Slug**: lo gestiona el cliente (`^[a-z0-9]+(-[a-z0-9]+)*$`, ≤120); único → **409**.
  En `PUT` se excluye a sí mismo (`existsBySlugAndIdNot`).
- **`is_featured`**: bool en el payload de create/update; **sin límite en backend** (el front
  elige hasta 3 destacados según RF-01).
- **Respuestas**: devuelven la **entidad** (id, status, timestamps incluidos; el UI admin los
  necesita y no hay nada sensible que ocultar).
- **Validación**: `@Valid` en el body (`@LocalizedNonBlank` exige al menos `es` o `en` no vacío
  en `title`; `kind` inválido → 400 por deserialización Jackson). `slug` inválido → 400.
  404 con `ResponseStatusException`. Todo sigue en `BasicErrorController` (unificar → Bloque 7).
- **Evicts tras la escritura y solo si toca algo publicado**: la caché pública nunca convive con
  contenido desactualizado; un draft jamás genera evict.

## Verificación (HECHA — humano con Postman, 2026-09-19)

A1→B3 OK a la primera. En B4 el único obstáculo fue de Postman (el UUID iba entre llaves
`{...}`, que se envían literalmente y Spring no puede convertir a `UUID` → 400; se pasó el UUID
pelado y todo el ciclo B4→B11 salió según lo esperado). Se validaron también los 404 de
`/public/cv` con el placeholder de perfil y el 302 con URLs de prueba en `profile`.

Base `http://localhost:8080`. Primero obtén un access token:

```
curl -s -X POST http://localhost:8080/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"000316jose@gmail.com","password":"..."}'
# copia accessToken del body y úsalo como: -H "Authorization: Bearer <token>"
```

1. `GET /admin/projects` **sin** token → **401** (auth exigida).
2. `POST /admin/projects` con body válido (title con al menos `es`) → **201** + `Location`, `status=DRAFT`.
3. `GET /public/projects` → **no** aparece (draft invisible).
4. `PATCH /admin/projects/{id}/publish` → 200, `status=PUBLISHED`, `published_at` seteados.
5. `GET /public/projects` → **aparece** (y con ETag); `GET /public/projects/{slug}` → 200.
6. `PUT /admin/projects/{id}` cambiando el `slug` → 200; detalle público con slug nuevo → 200,
   con slug viejo → **404**.
7. `POST /admin/projects` repitiendo un slug existente → **409**.
8. `PUT /admin/projects/order` con `["{idC}","{idA}"]` → el listado público refleja el nuevo orden.
9. `PATCH /admin/projects/{id}/unpublish` → 200; `GET /public/projects/{slug}` → **404**.
10. `DELETE /admin/projects/{id}` → **204**; detalle público → **404**.
11. Certificados: mismo recorrido; además `kind` inválido → **400** y `title` sin `es` ni `en` → **400**.
12. (Opcional) `PATCH .../publish` sobre un id inexistente → **404**.

## Notas de operación

- Túnel: `ssh -L 5432:localhost:5432 -L 6379:localhost:6379 teramont-dev`.
- Arranque dev: `setsid nohup bash -c 'MANAGEMENT_HEALTH_MAIL_ENABLED=false ./mvnw spring-boot:run'`
  desde `api/`; detener con `pkill -f spring-boot:run`.
- El rate limit de login (5/IP) aplica también al probar el CRUD: usa pocos intentos fallidos.
- Boot 4: recordatorio de las APIs rotas — ya absorbidas en bloques 5.x y 6.1.

## Hoja de ruta viva (detalle en docs/PLAN-API.md)

0-3 ✔ · 4 Auth JWT ✔ · 5.1 profile+projects ✔ · 5.2 certificates ✔ · 5.3 contacto+CV ✔ ·
**6.1 CRM CRUD ✔ (verificado)** · 6.2 bandeja de mensajes (siguiente) · 6.3 storage+profile · 7 Contrato OpenAPI.

---

## Prompt para la siguiente sesión (copiar/pegar)

```
Retomamos el proyecto pepedev-portfolio (docs en /home/pepe-dev/Projects/pepedev-portfolio).

Contexto cerrado (lee en orden docs/SESION7.md, docs/SESION8.md, docs/PLAN-API.md, docs/MODELO-DATOS.md):
- Bloques 0-3, 4 (auth JWT), 5.1 y 5.2 COMPLETOS. 5.3 (contacto+CV) IMPLEMENTADO (6 commits
  62ce705→3136418) y 6.1 (CRM CRUD) IMPLEMENTADO (6 commits 9850a42→e420af2); AMBOS DoD ya
  VERIFICADOS manualmente en la sesión 8 (guías en SESION7 y SESION8).
- /admin/** con hasRole(ADMIN): CRUD de projects y certificates bajo /admin/{recurso}
  (listar por UUID, crear=DRAFT, PUT completo mantiene status/published_at, PATCH publish/unpublish,
  PUT /order, DELETE hard). Decisiones: slug del cliente (único→409), published_at server-managed,
  is_featured sin límite backend, respuestas=entidad, @LocalizedNonBlank para title (al menos es|en),
  evicts solo si toca publicado y tras la escritura.
- API pública completa (Bloque 5) + POST /contact + GET /public/cv/{lang}. Auth JWT intacta
  (login → /auth/me → refresh rotativo → logout). /contact permitAll.
- Reglas: sin Docker en dev; secrets solo por .env; Flyway validate; bilingüe JSONB {es,en};
  túnel ssh para BD/Redis; errores 4xx por BasicErrorController hasta Bloque 7.

Notas de operación: .env real limpio y source .env OK; health SMTP DOWN en dev →
MANAGEMENT_HEALTH_MAIL_ENABLED=false; background con setsid nohup...spring-boot:run; pkill -f
spring-boot:run; Boot 4 rompe APIs de Boot 3 (Sort.Order, redis.delete(Collection), CacheControl,
nullsFirst/Last).

Método de trabajo (pair programming / mentoría):
- Yo (humano) desarrollo la API poco a poco, clase por clase, siguiendo docs/PLAN-API.md.
- Tú (IA) eres mi mentor: revisas mi código, me dices si está bien y qué corregir y por qué.
- Solo escribes tú el código cuando te lo pida explícitamente, y después me lo explicas para que aprenda.
- Se avanza commit por commit (UN commit por pieza para más control).

Tarea de la próxima sesión:
- Bloque 6.2 (CRM: bandeja de mensajes) siguiendo el plan. PRIMERO preséntame el desglose y los
  puntos abiertos (endpoints bajo /admin/messages, listar por status, marcar leído/archivado,
  borrar, validación y 404s, si las mutaciones afectan a caché pública) ANTES de escribir código.
```

---

*Fin de la sesión 8.*