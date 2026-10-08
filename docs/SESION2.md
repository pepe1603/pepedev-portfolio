# Sesión 2 · Estructura del monorepo, versión de Spring Boot y cierre temporal

> Fecha: 2026-09-13 · Estado: abierta (pausa, se retoma con la implementación de la API)

## Qué se hizo en esta sesión

1. **Estructura corregida a monorepo de raíz simple** (se descarta la estructura `apps/`):
   ```
   pepedev-portfolio/            ← raíz del monorepo
   ├── api/            ← Spring Boot, generado por el usuario en start.spring.io
   ├── web/                      ← Nuxt (futuro)
   ├── docs/                     ← documentación + PLAN-API.md
   ├── .env.example
   ├── .gitignore
   └── README.md
   ```
2. **Commit base creado**: `chore: base del monorepo` (docs + raíz; sin código de API).
3. **Decisión de versión**: se adoptó **Spring Boot 4.1.1** (estable que ofrece Initializr;
   la serie 3.x ya no aparece en el formulario). Esto corrige el «Spring Boot 3» de SESIÓN 1;
   ARCHITECTURE/README/PLAN-API quedan alineados.
4. **docs/PLAN-API.md** es la hoja de ruta commit por commit (ver abajo) y define el modo de trabajo.

## Decisiones cerradas (se aplican en su paso)

- El proyecto `api/` lo genera **el usuario** en start.spring.io y lo coloca en la raíz.
- Metadata: Group `dev.pepe1603` · Artifact/Name `api` · Package `dev.pepe1603.api`
  · Java 21 · Maven · Jar · Spring Boot `4.1.1`.
- Dependencias: Web, Data JPA, Flyway, PostgreSQL, Security, Redis, Mail, Validation, Actuator,
  Lombok, DevTools (Paso 3 de SETUP-FASE0).
- Tras generarlo: añadir a mano al `pom.xml` **springdoc-openapi-starter-webmvc-ui (v3.x, la que
  soporta Spring Boot 4)** + **jjwt (0.12.x)**.
- Modelo de datos mínimo (REQUIREMENTS §6): 5 tablas (`users`, `profile`, `projects`,
  `certificates`, `messages`), JSONB bilingüe `{es,en}`, enums `varchar + CHECK`, sin FKs de contenido.
- Conexión a Terramount por variables de entorno (`.env`), al final del bloque de persistencia
  (paso 2.1 del plan).

## Modo de trabajo (pair programming / mentoría)

El usuario desarrolla la API poco a poco (clase por clase). La IA actúa de mentor:
revisa, dice si está bien o qué corregir y por qué, y **solo escribe código cuando el usuario
lo pide explícitamente**, explicándoselo después para que aprenda. Se avanza commit por commit
siguiendo `docs/PLAN-API.md`, sin adelantarse ni crear cosas por cuenta propia.

## Hoja de ruta viva (detalle en docs/PLAN-API.md)

0. Cimientos ✔ (commit `chore: base del monorepo`)
0.5. Generar `api/` en start.spring.io y colocarlo en la raíz → commit
1.1 Modelo de datos (revisión) → 1.2 Migración V1__init → 2 JPA → 2.1 Conexión Terramount (env)
→ 3 Config/seguridad base → 4 Auth JWT → 5 API pública → 6 API CRM → 7 Contrato OpenAPI verificado.

---

## Prompt para la siguiente sesión (copiar/pegar)

```
Retomamos el proyecto pepedev-portfolio (docs en /home/pepe-dev/Projects/pepedev-portfolio).

Contexto cerrado (lee docs/SESION1.md, docs/SESION2.md, docs/PLAN-API.md y docs/REQUIREMENTS.md §6):
- Monorepo con estructura en la raíz: `api/` (Spring Boot 4.1.1 generado por mí en
  start.spring.io), `web/` (Nuxt, futuro), `docs/`. Ya existe el commit "chore: base del monorepo".
- Modelo de datos MÍNIMO (REQUIREMENTS §6): 5 tablas users/profile/projects/certificates/messages;
  JSONB bilingüe ES/EN {es,en}; enums como varchar+CHECK; sin FKs entre tablas de contenido.
- Reglas: NO docker en desarrollo; BD/Redis remotos en Terramount consumidos por variables de
  entorno (.env); Flyway gestiona el schema (nunca Hibernate ddl-auto diferente de validate);
  secrets solo por variables de entorno; contenido bilingüe ES/EN en JSONB {es,en}.
- Decisión de versión: Spring Boot 4.1.1 (Initializr ya no ofrece la serie 3.x). Al pom habrá que
  añadir springdoc-openapi-starter-webmvc-ui v3.x y jjwt 0.12.x.

Metodo de trabajo (pair programming / mentoría):
- Yo (humano) desarrollo la API poco a poco, clase por clase, siguiendo docs/PLAN-API.md.
- Tú (IA) eres mi mentor: revisas mi código, me dices si está bien y qué corregir y por qué.
- Solo escribes tú el código cuando te lo pida explícitamente, y después me lo explicas para que aprenda.
- Se avanza commit por commit y no te adelantas ni creas nada sin mi aprobación.

Tarea de la próxima sesión:
- Empezar por el Bloque 1.1 (definir el modelo de datos, sin código) siguiendo el plan.
- Luego 1.2 (V1__init.sql), 2 (JPA) y así sucesivamente; los primeros pasos los hago yo.
Antes de escribir código, muéstrame el plan y los puntos conflictivos.
```