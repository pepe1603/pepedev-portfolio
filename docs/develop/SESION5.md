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
| P5-0 | Este documento (backlog + decisiones) | ⬜ |
| P5-1 | **P1 Security stamp**: V4 (users.token_version) + `User.tokenVersion` + claim `tv` en access/refresh + validación en `/auth/refresh` + tests | ⬜ |
| P5-2 | **P2 Registro de sesiones**: V5 `auth_sessions` + entidad/repo + claim `sid` + registro en login/refresh + `last_seen_at` + purga + tests | ⬜ |
| P5-3 | **P3 Control de sesiones**: `GET /auth/sessions`, `POST /auth/sessions/{id}/revoke`, `/revoke-others`, `/auth/logout-all` + auditoría REVOKE + tests | ⬜ |
| P5-4 | **P4 Password reset**: `POST /auth/reset/request` + `/confirm` (token Redis single-use, `tv` bump, plantilla `mail/reset.html` es/en, rate limit) + tests | ⬜ |
| P5-5 | **P5 OTP email login**: login 2 pasos (`/auth/login` 202 + `/auth/login/verify`), plantilla `mail/otp.html` es/en, `PATCH /admin/otp`, properties + tests | ⬜ |
| P5-6 | **P6 Hardening + docs**: warnings de arranque, `API.md`, `.env.example`, acta de cierre | ⬜ |

## Conexiones entre piezas

- P5-1 es base de todo (tv). Sin ella, "revocar todas" no es instantáneo.
- P5-2/P5-3 necesitan P5-1 (sid/tv claims) y Son las que dan visibilidad + revocación selectiva.
- P5-4 y P5-5 profundizan sobre P5-1 (bump de `tv` al reset) y son independientes entre sí.
- P5-6 cierra lo que quede en rama.