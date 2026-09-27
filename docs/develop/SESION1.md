# Sesión 1 — Rama `develop`: organización del código

> Fase de mejoras sobre `develop` (derivada de `main` en `78efed0`). La numeración de
> sesiones **reinicia desde 1** en esta rama; las actas viven en `docs/develop/` para no
> chocar con las de la API original (`docs/SESION1-12`). Backlog maestro (si crece) en
> `PLAN-DEVELOP.md`.

## Contexto

- Rama: `develop` (nueva, cleana desde `main`). Push a remoto **aparcado** por decisión.
- API original: 86 tests verdes, verificación en vivo (18/18) cerrada.
- Objetivo de la sesión: **mejoras de organización de archivos** (empaquetado).
  Ejemplos planteados por el usuario: excepciones viven en `security/` en vez de una
  carpeta de excepciones; ¿es recomendable "1 controller = 1 service"?

## Reglas de trabajo (heredadas)

- Cada pieza es un commit independiente y compilable, con su suite verde.
- Antes de cerrar: `./mvnw test` completo (BUILD SUCCESS).
- Todo hallazgo se documenta; los docs son fuente de verdad.
- Working tree limpio al terminar la sesión.

## Lista de tareas (backlog de la sesión)

| Pieza | Tarea | Estado |
|---|---|---|
| P0 | Esta acta + backlog (doc de sesión) | ⬜ |
| P1 | Crear paquete `exception/`; mover `ContactRateLimitedException` y `LoginRateLimitedException` fuera de `security/`; actualizar imports | ⬜ |
| P2 | Mover `ApiExceptionHandler` de `config/` a `exception/`; actualizar imports de tests | ⬜ |
| P3 | Veredicto documentado sobre "controller → service 1:1" (sin cambios de código salvo hallazgo) | ⬜ |
| P4 | Acta final: resultados, dificultades, estado de la rama | ⬜ |

## Veredicto de arquitectura (P3, decidido en planificación)

**Sí es recomendable, pero la regla real es "un service por agregado/responsabilidad",
no "un service por controller".**

El proyecto ya cumple: `Admin{Project,Certificate,Message,Profile}Service` (CRUD admin por
recurso), `PublicService`, `ContactService` y cross-cutting `StorageService`/
`PublicCacheService`. Los services no son pasarelas vacías (lógica de orfanatos, mapeo DTO,
transacciones). **No se cambia nada**; se documenta como hallazgo y criterio futuro (si un
service crece, se parte por responsabilidad, no por homología de controller).

## Hallazgos iniciales (exploración)

- `security/` mezcla **excepciones** (`ContactRateLimitedException`,
  `LoginRateLimitedException`) con infraestructura de seguridad legítima
  (`JwtAuthEntryPoint`, `JwtAccessDeniedHandler`, `SecurityErrorWriter`, limiters, filtro...).
- `config/ApiExceptionHandler` (global `@RestControllerAdvice`) vive fuera del
  paquete de excepciones; candidato a `exception/` (el "Exceptions folder").
- Puntos de uso confirmados: `ApiExceptionHandler`, `AuthController`, `ContactController`
  (main) y `AdminMessageListTest`, `ApiErrorContractTest` (test) importan del paquete actual.
- `docs/ARCHITECTURE.md` no menciona paquete `security`/handler → sin cambios de doc a priori.