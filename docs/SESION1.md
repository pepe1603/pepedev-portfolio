# Sesión 1 · Comparación Supabase vs stack propio + modelo de datos mínimo

> Fecha: 2026-09-12 · Estado: cerrada (decisiones tomadas, docs actualizados, sin código)

## Objetivo de la sesión

Evaluar si convenía mover el proyecto a Supabase (usando el schema PostgreSQL generado
desde su consola) o mantener el backend propio definido en los docs. El producto real es:
**landing pública (SSR/SEO) + CRM privado de gestión del portfolio**.

## Decisiones tomadas (definitivas)

### 1. Stack: continúa Spring Boot 3 + Nuxt 3. Supabase queda DESCARTADO.

> **Corrección posterior (Sesión 2)**: al descargar desde start.spring.io la serie 3.x ya no
> está disponible. Se adopta la **estable que ofrece el sitio: Spring Boot 4.1.1** (ver docs/SESION2.md).

Razones (desarrolladas y aceptadas en la sesión):

1. El backend **es** la demostración de la skill más fuerte (Spring/JWT/Flyway/Redis)
   ante reclutadores, objetivo explícito del proyecto. Supabase borra esa demostración.
2. Menos esfuerzo es un mito: Supabase requiere aprender PostgREST, RLS, Storage y una
   Edge Function solo para el SMTP del formulario → segundo stack completo + vendor lock-in.
3. Patrón central de Supabase = "el cliente consulta la BD" → rompe con tener un contrato
   de API (endpoints, DTOs, validación), que aquí es parte del portafolio.
4. El CRM tiene seguridad real (JWT, BCrypt, rate limiting, honeypot) = lógica de negocio
   que vive en un backend de verdad (Spring Security), no en políticas RLS.
5. Portabilidad: PostgreSQL propio migra Terramount → Hostinger con `pg_dump` + Flyway;
   con Supabase los datos viven en su cloud.
6. Frontend es idéntico en ambas opciones (Nuxt SSR); con Spring cuentas historia
   full-stack Java + TypeScript.

### 2. Modelo de datos: modelo MÍNIMO (rechaza el esquema relacional de Supabase)

Se reemplazó el modelo preliminar de REQUIREMENTS por 3 tablas de contenido + auth.
Regla de oro:

> **Tabla** = entidad con página propia o filtro propio. **JSONB embebido** = lista de
> presentación sin detalle ni filtro.

| Tabla | Rol | Nota clave |
|---|---|---|
| `users` | Auth del CRM (Spring Security) | Sin relación con contenido (1 solo dueño) |
| `profile` | Singleton "quién soy" | skills[] y experiences[] en JSONB; CV es/en; unicamente 1 fila |
| `projects` | Página propia + SEO (slug) | `stack[]` y `gallery[]` JSONB; sin join technologies |
| `certificates` | Listado filtrable (kind/issuer) | credencial + imagen como URLs |
| `messages` | Bandeja de contacto | ip, user_agent, status new/read/archived |

Eliminados frente al schema de Supabase y al plan previo: `technologies` +
`project_technologies` (join → `stack[]`), `skills` (→ JSONB en profile), `experiences`
(→ JSONB en profile), `media` (→ URLs en columnas/arrays), `cvs` (→ dos URLs en profile).

**No hay claves foráneas entre tablas de contenido** → cada página pública = una sola
lectura, sin joins, cacheable en Redis con invalidación por edición.

Se normalizaría solo si el producto creciera a multiusuario, métricas por tecnología,
catálogo de media con selector, o miles de proyectos. No es este producto.

## Cambios aplicados a la documentación (cero código)

- `docs/REQUIREMENTS.md` → §6 reescrito (modelo mínimo + rationale); RF-05 actualizado
  ("gestión de media" → subida de archivos que devuelve URL + activar CV es/en).
- `docs/ARCHITECTURE.md` → fila "Modelo de datos: mínimo (4 tablas + JSONB)" en la tabla
  de decisiones; módulo `media/` renombrado a `storage/`.
- `docs/ROADMAP.md` → Fase 1 crea `profile` + `projects`; Fase 3 alineada al flujo de URLs.
- `README.md` → sin cambios (aún coherente).

## Puntos abiertos / pendientes (antes de codear)

1. ¿Ajustar README para enlazar el modelo de datos de REQUIREMENTS §6? (pequeño)
2. Confirmar si Fase 0 ya fue al menos parcialmente hecha por el usuario (repo/estructura/API base).
3. Definir el orden de código concreto: Flyway `V1__init` (4 tablas) → entidades JPA → endpoints públicos → auth CRM.

---

## Prompt para la siguiente sesión (copiar/pegar)

```
Retomamos el proyecto pepedev-portfolio (docs en /home/pepe-dev/Projects/pepedev-portfolio).

Contexto cerrado en la SESIÓN 1 (lee docs/SESION1.md y docs/REQUIREMENTS.md §6): 
mantenemos Spring Boot 3 + Nuxt 3 (Supabase descartado) y usamos un modelo de datos 
MÍNIMO: tabla = entidad con página/filtro propio; JSONB embebido para listas de 
presentación. Tablas: users (auth), profile (singleton, skills[] y experiences[] en 
JSONB, cv_url_es/cv_url_en), projects (slug, stack[] y gallery[] JSONB, is_featured, 
sort_order, status draft/published), certificates (kind/issuer, credential_url, 
image_url, sort_order), messages (bandeja, new/read/archived). Sin FKs entre tablas de 
contenido. Ya no existen technologies/project_technologies/media/cvs/skills/experiences 
como tablas.

Reglas del proyecto: NO docker en desarrollo; BD/Redis remotos en Terramount (VPS miniOS); 
Flyway maneja el schema (nunca Hibernate ddl-auto); secrets solo por variables de 
entorno; contenido bilingüe ES/EN en JSONB {es,en}.

Tarea de esta sesión: [DEFINIR AQUÍ LO QUE TOQUE: p.ej. redactar migración Flyway V1__init
con las 4 tablas + enums, definir contrato de endpoints públicos/CRM, o lo que decidas].
Antes de escribir código, muéstrame el plan y los puntos conflictivos.
```