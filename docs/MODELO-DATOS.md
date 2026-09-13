# Modelo de datos mínimo — pepedev-portfolio (fuente de verdad)

> Estado: propuesto (Bloque 1.1) · Aprobaciones pendientes marcadas con ✅ · Este documento
> manda sobre el esquema aplicado: `V1__init.sql`, clases JPA y DTOs se derivan de aquí.

## Reglas que aplican a todo el modelo

- Tabla = entidad con página propia o filtro propio. Listas de presentación viven en JSONB.
- **Sin claves foráneas entre tablas de contenido** → cada página pública = una sola lectura.
- Bilingüe ES/EN = columnas `JSONB` con forma `{ "es": "...", "en": "..." }`. Si falta un
  idioma, la API sirve el disponible (nulo permite el fallback).
- Enums como `VARCHAR(20)` + `CHECK` con **valores en MAYÚSCULAS** (en Java `@Enumerated(STRING)` persiste el nombre del enum).
- Timestamps: `TIMESTAMPTZ NOT NULL DEFAULT now()`. `updated_at` lo gestiona JPA (sin triggers).
- Identificadores: `UUID` **v7 (RFC 9562)** generados por Hibernate (`@UuidGenerator`) en users/projects/certificates/messages. Excepción: `profile.id SMALLINT` (= 1, singleton).
- El schema lo crea Flyway. `ddl-auto: validate` en Hibernate, jamás Hibernate con `update`.
- Secretos y admin: nunca en migraciones (el admin nace del bootstrap con `.env`).

## Tablas

### users — auth del CRM (1 fila: el admin)

| Columna | Tipo | Restricciones |
|---|---|---|
| `id` | UUID | PK, v7 (Hibernate) |
| `email` | VARCHAR(320) | NOT NULL, UNIQUE |
| `password_hash` | VARCHAR(255) | NOT NULL |
| `role` | VARCHAR(20) | NOT NULL, `CHECK (role IN ('ADMIN'))` |
| `created_at` | TIMESTAMPTZ | NOT NULL DEFAULT now() |
| `last_login_at` | TIMESTAMPTZ | NULL |

Sin relación con contenido. ⚠️ `role` solo admite `ADMIN` hoy: si mañana nacen más roles,
el CHECK se adaptaría en una V2 (advertir en la migración).

### profile — singleton (quién soy)

`id SMALLINT PRIMARY KEY CHECK (id = 1)` → solo existe la fila 1.

| Columna | Tipo | Notas |
|---|---|---|
| `full_name` | VARCHAR(120) | NOT NULL |
| `headline` | JSONB | `{es,en}` nullable |
| `bio` | JSONB | `{es,en}` nullable |
| `location` | VARCHAR(120) | NULL |
| `github_url` | VARCHAR(255) | NULL |
| `linkedin_url` | VARCHAR(255) | NULL |
| `email_public` | VARCHAR(320) | NULL |
| `website_url` | VARCHAR(255) | NULL |
| `cv_url_es` | VARCHAR(255) | NULL |
| `cv_url_en` | VARCHAR(255) | NULL |
| `avatar_url` | VARCHAR(255) | NULL |
| `skills` | JSONB | NOT NULL DEFAULT `'[]'::jsonb` — `[{name, category, level}]` |
| `experiences` | JSONB | NOT NULL DEFAULT `'[]'::jsonb` — `[{title, company, period, type(grado?, empleo, …), description}]` |
| `views_count` | INTEGER | NOT NULL DEFAULT 0 |
| `created_at` | TIMESTAMPTZ | NOT NULL DEFAULT now() |
| `updated_at` | TIMESTAMPTZ | NOT NULL DEFAULT now() — lo actualiza JPA |

### projects — página propia + SEO (slug)

| Columna | Tipo | Notas |
|---|---|---|
| `id` | UUID | PK, v7 (Hibernate) |
| `slug` | VARCHAR(120) | NOT NULL, UNIQUE → `{slug}` público |
| `title` | JSONB | NOT NULL `{es,en}` |
| `subtitle` | JSONB | `{es,en}` nullable |
| `summary` | JSONB | `{es,en}` (tarjeta del listado) |
| `description_md` | JSONB | `{es,en}` nullable, markdown |
| `thumbnail_url` | VARCHAR(255) | NULL |
| `gallery` | JSONB | NOT NULL DEFAULT `'[]'::jsonb` — `[{url, alt, caption}]` |
| `repo_url` | VARCHAR(255) | NULL |
| `demo_url` | VARCHAR(255) | NULL |
| `stack` | JSONB | NOT NULL DEFAULT `'[]'::jsonb` — array de strings; filtro = `contains` |
| `period_start` | DATE | NULL |
| `period_end` | DATE | NULL — NULL = en curso |
| `is_featured` | BOOLEAN | NOT NULL DEFAULT false |
| `sort_order` | INTEGER | NOT NULL DEFAULT 0 |
| `status` | VARCHAR(20) | NOT NULL DEFAULT 'DRAFT', `CHECK (status IN ('DRAFT','PUBLISHED'))` |
| `published_at` | TIMESTAMPTZ | NULL — se setea al publicar |
| `created_at` | TIMESTAMPTZ | NOT NULL DEFAULT now() |
| `updated_at` | TIMESTAMPTZ | NOT NULL DEFAULT now() — lo actualiza JPA |

### certificates — listado filtrable (kind/issuer)

| Columna | Tipo | Notas |
|---|---|---|
| `id` | UUID | PK, v7 (Hibernate) |
| `title` | JSONB | NOT NULL `{es,en}` |
| `issuer` | VARCHAR(120) | NOT NULL |
| `kind` | VARCHAR(20) | NOT NULL, `CHECK (kind IN ('CERTIFICATE','COURSE'))` |
| `issue_date` | DATE | NULL |
| `expiry_date` | DATE | NULL — NULL = sin caducidad |
| `credential_url` | VARCHAR(255) | NULL |
| `image_url` | VARCHAR(255) | NULL |
| `is_featured` | BOOLEAN | NOT NULL DEFAULT false |
| `sort_order` | INTEGER | NOT NULL DEFAULT 0 |
| `status` | VARCHAR(20) | NOT NULL DEFAULT 'DRAFT', `CHECK (status IN ('DRAFT','PUBLISHED'))` |
| `published_at` | TIMESTAMPTZ | NULL — ✅ propuesto (consistencia con projects) |
| `created_at` | TIMESTAMPTZ | NOT NULL DEFAULT now() |

### messages — bandeja de contacto

| Columna | Tipo | Notas |
|---|---|---|
| `id` | UUID | PK, v7 (Hibernate) |
| `name` | VARCHAR(120) | NOT NULL |
| `email` | VARCHAR(320) | NOT NULL |
| `subject` | VARCHAR(160) | NOT NULL |
| `body` | TEXT | NOT NULL |
| `ip` | VARCHAR(45) | NOT NULL — **anonimizada `/24`** antes de persistir |
| `user_agent` | VARCHAR(255) | NULL |
| `status` | VARCHAR(20) | NOT NULL DEFAULT 'NEW', `CHECK (status IN ('NEW','READ','ARCHIVED'))` |
| `created_at` | TIMESTAMPTZ | NOT NULL DEFAULT now() |

El rate-limit vive en Redis (no aquí). Consentimiento de privacidad en `body`/front (RNF-06) — no requiere columna propia.

## Índices

| Índice | Tipo | Motivo |
|---|---|---|
| `users.email` | UNIQUE (constraint) | login |
| `projects.slug` | UNIQUE (constraint) | URL pública |
| `projects.status` | B-tree **parcial** `WHERE status = 'published'` | listado público | ✅ propuesto (volumen pequeño; GIN sobre stack se difiere a V2) |
| `certificates.kind` | B-tree | filtro público kind |
| `certificates.status` | B-tree | listado público |
| `messages.status` | B-tree | bandeja por status |

## Seed (en V1__init)

- `profile`: insertar la fila `id = 1` con valores mínimos/placeholder (skills `[]`,
  experiences `[]`) — el contenido real entra vía CRM (Bloque 6.3).
- `users`: **sin seed**. El admin se crea en el bootstrap (Bloque 3) desde
  `APP_ADMIN_EMAIL`/`APP_ADMIN_SECRET` (`.env`).

## Mapeo de decisiones abiertas (Bloque 1.1)

| # | Tema | Decisión adoptada en este doc | Observación |
|---|---|---|---|
| 1 | IDs | UUID v7 (Hibernate `@UuidGenerator`) en users/projects/certificates/messages; profile SMALLINT 1 | ✅ aprobado |
| 2 | Enums | role=ADMIN · status=DRAFT/PUBLISHED · kind=CERTIFICATE/COURSE · message.status=NEW/READ/ARCHIVED (MAYÚSCULAS en BD y Java) | ✅ aprobado |
| 3 | i18n | JSONB nullable para permitir fallback; `title` obligatorio | ✅ |
| 4 | Arrays JSONB | NOT NULL DEFAULT `'[]'::jsonb` | ✅ |
| 5 | Periodo | DATE nullable; `period_end` NULL = en curso | ✅ |
| 6 | `published_at` en certificates | Sí (consistencia con projects) | ✅ propuesto |
| 7 | Índices | los de la tabla anterior, incluido parcial en projects | ✅ |
| 8 | `messages.ip` | columna normal, enmascarar `/24` en el service (Bloque 5.3) | cerrado con aclaración |
| 9 | Seed | solo profile placeholder; sin users | cerrado en PLAN |
| 10 | Timestamps | TIMESTAMPTZ + now(); updated_at vía JPA; messages/certificates solo created_at | cerrado en PLAN |

## Paquetes/dependencias afectadas por la aprobación (recordatorio 0.5)

- Paquete Java: **`dev.pepe1603.portfolio_api`** (el que generó Initializr; SESION2 y SETUP-FASE0 corregidos).
- springdoc-openapi **3.1.0** (serie 3.x, la que soporta Spring Boot 4) y jjwt **0.12.7** añadidos a mano al pom.

## Por qué es suficiente (recordatorio)

`technologies`+join → `stack[]`; `skills`/`experiences`/`gallery`/media/cvs → JSONB y URLs.
Se normalizaría solo si el producto creciera a multiusuario, métricas por tecnología,
selector de media o miles de proyectos. No es este producto.