# Sesión 2 — Rama `develop`: auditoría admin + contador de mensajes no leídos

> Fase de mejoras sobre `develop`. Continuación de la Sesión 1 (organización del código).
> Backlog y acta en `docs/develop/`.

## Contexto

- Rama: `develop`; Sesión 1 cerrada (4 commits, 86 tests verdes).
- Mejoras acordadas para esta sesión (decisiones del usuario):
  1. **Auditoría de cambios admin**: registrar quién y cuándo modifica/borra recursos
     (proyectos, certificados, perfil, mensajes, storage).
  2. **Endpoint de mensajes no leídos** para el panel (badge de `NEW`).

## Reglas de trabajo (heredadas)

- Cada pieza es un commit independiente y compilable, suite verde antes de cada cierre.
- `./mvnw test` completo (86+ tests, BUILD SUCCESS). Working tree limpio al terminar.

## Decisiones de diseño (auditoría)

- **Tabla `audit_log`** gestionada por **Flyway (V3)**; entidad `AuditEntry` con UUID v7 de
  Hibernate (`@UuidGenerator(Style.VERSION_7)`, igual que el resto de entidades). Como
  `ddl-auto: validate`, la migración debe coincidir exactamente con el mapeo.
- **Columnas**: `id` (uuid), `actor_id` (uuid), `actor_email` (varchar 255), `action`
  (enum STRING, 32), `resource_type` (enum STRING, 32), `resource_id` (varchar 64: uuid o
  nombre de fichero), `detail` (varchar 1000), `created_at` (timestamptz NOT NULL; Hibernate
  rellena con `Instant.now()` en java, sin default en BD).
- **Enums**: `AuditAction {CREATE, UPDATE, DELETE, READ, ARCHIVE, UPLOAD}` y
  `AuditResource {PROJECT, CERTIFICATE, PROFILE, MESSAGE, STORAGE}`.
- **Actor**: `SecurityContextHolder` → `AppUserDetails.getUser()` (id + email). Sin
  autenticación ⇒ `actor_email = "SISTEMA"`.
- **Transacción**: la del llamador (join tx). Sin endpoint de lectura de auditoría por
  ahora (solo registro; posible endpoint `GET /admin/audit` en futura sesión).
- **Storage**: solo `UPLOAD` (no existe borrado manual de fichero). La limpieza de
  orfanatos es automática y sin actor → no se audita.

## Decisiones de diseño (mensajes no leídos)

- `GET /admin/messages/unread-count` → `200 {"count": N}` (contador de `status=NEW`).
- `MessageRepository.countByStatus(MessageStatus)` (derivada de Spring Data).
- DTO `MessageCountResponse(long count)` en `dto/admin/` (estilo `PageResponse`).
- Rutas: declarar `/unread-count` **antes** de `/{id}` en el controlador (preferencia estática).
- Doc: `@ApiResponse` en swagger (paridad con la Pieza D de la API original) + `API.md`.

## Lista de tareas (backlog de la sesión)

| Pieza | Tarea | Estado |
|---|---|---|
| P2-0 | Este documento (backlog + decisiones) | ⬜ |
| P2-1 | Modelo auditoría: migración `V3__audit_log.sql`, `AuditEntry`, enums, `AuditRepository` | ⬜ |
| P2-2 | `AuditService` + tests (actor, anónimo→SISTEMA, truncado de `detail`) | ⬜ |
| P2-3 | Cablear auditoría en admin services/controller + tests actualizados | ⬜ |
| P2-4 | `unread-count`: repository + service + DTO + endpoint + swagger + slice test | ⬜ |
| P2-5 | Docs: `API.md` (contrato + nota auditoría) + acta de cierre | ⬜ |