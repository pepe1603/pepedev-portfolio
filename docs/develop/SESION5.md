# Sesión 5 — Rama `develop`: sesiones activas, password reset y OTP por email

> Paquete de gestión de sesiones y acceso. Backlog y acta en `docs/develop/`.
> Estado inicial: auth stateless JWT (access 15 min Bearer + refresh 7 días en cookie
> HttpOnly SameSite=Lax `path=/auth`, rotación y blacklist por `jti` en Redis). **No hay**
> registro de sesiones ni forma de revocarlas globalmente.

## Decisiones de diseño

- **Invalidación global instantánea**: columna `users.token_version` con claim `tv` en los
  JWT. Bump de versión → todos los tokens del usuario dejan de valer. Se valida en el punto de
  rotación (`/auth/refresh`); el access token (15 min) queda cubierto por la vida corta +
  blacklist (`tv` estricto request-a-request queda descartado: rompe la statelessness).
- **Sesión = par**: cada login crea una fila `auth_sessions` con `session_id` (claim `sid`)
  compartido entre el access y refresh de esa sesión, y el `jti` del refresh. Cada refresh
  rota `jti` y actualiza `last_seen_at`/`expires_at` de la misma sesión.
- **Revocación** de una sesión = marcar `revoked_at` + meter su `refresh_jti` en la blacklist.
  **Revocar todas** = bump `token_version` + blacklist de los `jti` activos + `revoked_at`.
- **Limpieza**: purga oportunista de expiradas/revocadas en `GET /auth/sessions` (sin
  scheduler por ahora).
- **Password reset**: token de un solo uso en Redis (`pwreset:<email>`, TTL 30 min) enlazado
  por email; `request` siempre responde lo mismo (no filtra si el email existe); `confirm`
  aplica la nueva password + bump `tv` + revoca sesiones. Plantilla Thymeleaf `mail/reset.html`
  es/en (infra de Sesión 3/4). Nueva property `APP_FRONT_RESET_URL` para armar el enlace.
- **OTP login**: property `APP_AUTH_OTP_ENABLED` (default `false`) + `users.otp_enabled`
  (default false). Login con credenciales OK + 2FA activo → `202` + `challengeId`, se envía
  OTP de 6 dígitos por email (plural single-use, TTL 5 min, máx 5 intentos). `POST
  /auth/login/verify` consume y emite tokens + registra sesión. Plantilla `mail/otp.html`
  es/en. Toggle del propio admin en `PATCH /admin/otp {enabled}`.
- **Rate limits**: patrón REDIS INCR+EXPIRE existente; nuevos contadores para reset y OTP.
- **Auditoría** de revocaciones, logout-all, cambios de 2FA y resets (actor por JWT o SISTEMA);
  se añaden valores Java a `AuditAction`/`AuditResource` (las columnas son varchar sin CHECK,
  no requiere migración).
- **Hardening multi-entorno (P6)**: warnings en arranque (secreto de refresh == secreto de
  access, secretos tipo dev) y documentación: secretos JWT **distintos por entorno**,
  `refreshCookieSecure=true` en prod.
- Regla de la rama: **cada pieza = commit compilable con suite verde**; `./mvnw test -q`.

## Lista de tareas (backlog de la sesión)

| Pieza | Tarea | Estado |
|---|---|---|
| P5-0 | Este documento (backlog + decisiones) | ✅ `18f22c3` |
| P5-1 | **P1 Security stamp**: V4 (users.token_version) + `User.tokenVersion` + claim `tv` en access/refresh + validación en `/auth/refresh` + tests | ✅ `06a4bac` |
| P5-2 | **P2 Registro de sesiones**: V5 `auth_sessions` + entidad/repo + claim `sid` + registro en login/refresh + `last_seen_at` + purga + tests | ✅ `90abdc3` |
| P5-3 | **P3 Control de sesiones**: `GET /auth/sessions`, `POST /auth/sessions/{id}/revoke`, `/revoke-others`, `/auth/logout-all` + auditoría REVOKE + tests | ✅ `443d91f` |
| P5-4 | **P4 Password reset**: `POST /auth/reset/request` + `/confirm` (token Redis single-use, `tv` bump, plantilla `mail/reset.html` es/en, rate limit) + tests | ✅ `2a5caa0` |
| P5-5 | **P5 OTP email login**: login 2 pasos (`/auth/login` 202 + `/auth/login/verify`), plantilla `mail/otp.html` es/en, `PATCH /admin/otp`, properties + tests | ✅ `7c72652` |
| P5-6 | **P6 Hardening + docs**: warnings de arranque, `API.md`, `.env.example`, acta de cierre | ✅ (este commit) |

## Acta de cierre

**Resultado**: 6 piezas, 6 commits, suite en **173 tests / 0 fallos**. La autenticación pasa de
"tokens sin identidad de sesión" a "sesiones registradas y revocables de forma selectiva o global".

**Migraciones**: `V4__users_token_version.sql` (`token_version`), `V5__auth_sessions.sql`
(`auth_sessions` con `refresh_jti`, `expires_at`, `revoked_at`, ip y user-agent) y
`V6__users_otp_enabled.sql` (`otp_enabled`). `audit_log` no necesitó migración: sus columnas son
varchar sin CHECK, así que `REVOKE`/`RESET` y `SESSION`/`USER` son valores Java nuevos
(`AuditAction`, `AuditResource`).

**Endpoints nuevos**: `GET /auth/sessions`, `POST /auth/sessions/{id}/revoke`,
`POST /auth/sessions/revoke-others`, `POST /auth/logout-all`, `POST /auth/reset/request`,
`POST /auth/reset/confirm`, `POST /auth/login/verify`, `PATCH /admin/otp`. Público en
`SecurityConfig`: `/auth/login/verify`, `/auth/reset/request` y `/auth/reset/confirm`.

**Redes**: claves nuevas `pwreset:{token}` (30 min, single-use), `rl:reset:ip:*`,
`auth:otp:{challengeId}` + `auth:otp:{challengeId}:attempts` (5 min, máx 5 intentos); el código
OTP nunca se guarda en claro (SHA-256) ni viaja en la respuesta (solo el `challengeId`).

**Correcciones de diseño sobre lo planificado**:
- Los endpoints de sesiones resuelven el usuario por el `sub` del access token
  (`AccessTokenReader`) en lugar del argumento `Authentication`: en `@WebMvcTest` con la cadena de
  filtros desactivada `request.getUserPrincipal()` es `null`, así que el resolver no llega a
  inyectarlo y los endpoints no eran testeables. En producción el comportamiento es el mismo.
- La purga de `auth_sessions` va en `GET /auth/sessions` (oportunísticamente), no en un scheduler.
- El access token no se revalida contra `token_version` en cada request (rompería la
  statelessness): tras un `logout-all` o un reset sobrevive como máximo `APP_JWT_ACCESS_TTL`
  minutos. Documentado en `API.md`.
- `logout` (el simple) también marca la sesión como revocada en tabla, no solo mete los `jti` en
  la denylist.

**Pendiente para el front** (fuera de este backend): pantalla de reset que consuma
`?token=`, pantalla de introducción del código OTP tras el `202` del login, y panel de sesiones
activas que consuma `GET /auth/sessions` + los `revoke`.

**Pendiente de despliegue**: `APP_FRONT_RESET_URL` por entorno, `APP_AUTH_OTP_ENABLED=false`
hasta que exista la pantalla OTP, secretos JWT distintos por entorno y
`APP_JWT_REFRESH_COOKIE_SECURE=true` en producción (los avisos de `StartupSecurityWarnings`
salen en el log de arranque si no se cumple).

## Conexiones entre piezas

- P5-1 es base de todo (tv). Sin ella, "revocar todas" no es instantáneo.
- P5-2/P5-3 necesitan P5-1 (sid/tv claims) y Son las que dan visibilidad + revocación selectiva.
- P5-4 y P5-5 profundizan sobre P5-1 (bump de `tv` al reset) y son independientes entre sí.
- P5-6 cierra lo que quede en rama.