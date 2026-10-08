# Sesión 4 · API: Bloque 4 — Auth JWT completo y verificado

> Fecha: 2026-09-16 · Estado: cerrada. Bloque 4 (auth JWT) DoD completado: `login → /auth/me → refresh rotativo → logout` verificado por curl, con rate limit de login funcionando.

## Qué se hizo en esta sesión

Se construyó el Bloque 4 **commit por commit** (decisión del usuario: granularidad para localizar fallos):

| Commit | Pieza |
|---|---|
| `525d603` | `JwtProperties` (env, TTLs, cookie) + `JwtTokenService` (firmar/parsear access y refresh, jjwt 0.12.7, claims `sub`/`role`/`jti`/`typ`, 2 secretos) |
| `74b4db5` | `AppUserDetails` (adapter) + `UserDetailsServiceImpl` (elimina el aviso *generated password*) + `UserRepository.findByEmail` |
| `1e11c54` | `JwtAuthenticationFilter` (valida Bearer + denylist por `jti`) + `TokenBlacklist` (Redis, `jwt:revoked:{jti}`) + `JwtAuthEntryPoint`/`JwtAccessDeniedHandler` (401/403 JSON) + wiring en `SecurityConfig` |
| `4bcdc1d` | `LoginRateLimiter` (INCR+EXPIRE, IP 5 + email 10 / 15 min, reset en éxito) + `RateLimitProperties` + `server.forward-headers-strategy: framework` |
| `60ae5a5` | DTOs (`LoginRequest`, `TokenResponse`, `MeResponse`) + `AuthController` (login/refresh/logout/me con cookie httpOnly) + `AuthenticationManager` bean + PATH públicos `/auth/*` |
| `ac47b8c` | Mensaje de `UsernameNotFoundException` genérico y uniforme |
| `6a7eb06` | `PLAN-API.md`: Bloque 4 ✔ + decisiones cerradas |

## Decisiones cerradas en la sesión

- **Refresh token**: cookie **httpOnly**, `SameSite=Lax`, `Path=/auth`, `Secure` por env
  (`APP_JWT_REFRESH_COOKIE_SECURE`, false en dev) — la llave maestra fuera del alcance de XSS.
  Access token en `Authorization: Bearer`. Juguete: el front debe usar `credentials: 'include'`.
- **Rate limit de login**: `INCR`+`EXPIRE` en Redis, ventana fija, **5 fallos/IP + 10 fallos/email
  por 15 min**, reset al login OK → `429` + `Retry-After`. `APP_LOGIN_RATE_*`.
- **Rotación**: cada `refresh` emite par nuevo y revoca el anterior (denylist con TTL = vida
  restante); `logout` revoca refresh (cookie) y access (Bearer) → la sesión muere al instante.
- **TTLs**: access 15 min (`APP_JWT_ACCESS_TTL`) · refresh 7 días (`APP_JWT_REFRESH_TTL`), en segundos.
- **Admin row**: se borró la fila del primer arranque (`DELETE FROM users`) y `AdminBootstrap`
  la recreó al arrancar con el `.env` actual → `users` tiene `000316jose@gmail.com` + hash BCrypt
  del `APP_ADMIN_SECRET` vigente. BD y `.env` alineados.
- **Commits**: un commit por pieza (antes el plan decía uno por bloque).

## Verificaciones (Terramount vía túnel)

- `/actuator/health` → `{"groups":["liveness","readiness"],"status":"UP"}`.
- `POST /auth/login` (credenciales del `.env`) → 200 `{tokenType:"Bearer", expiresIn:900}`, cookie `refresh_token` seteada.
- `GET /auth/me` con access → 200 `{"email":"000316jose@gmail.com","role":"ADMIN"}`.
- `POST /auth/refresh` → 200 (nueva cookie); **reusar el refresh viejo → 401** (rotación OK); el nuevo sigue sirviendo → 200.
- `POST /auth/logout` → 204; `GET /auth/me` tras logout → 401 (access revocado).
- Rate limit: 5×401 con contraseña mala y 6º intento → `429` con `Retry-After: 900`. Claves Redis `jwt:revoked:*` (6) y `rl:login:ip/email` generadas; contadores de login limpiados manualmente tras la demo.
- `users` pasa de 1 fila placeholder a 1 fila con el `.env` actual.

## Notas de operación para la siguiente sesión

- **Túnel obligatorio** para arrancar: `ssh -L 5432:localhost:5432 -L 6379:localhost:6379 teramont-dev`.
- **SMTP (MailHealthIndicator)**: desde esta máquina `smtp.resend.com:587` NO es alcanzable hoy;
  el health baja a `DOWN` y tarda ~134 s (conectividad transitoria, no del código). Para
  verificar sin que el health se cuegue en dev: arrancar con `MANAGEMENT_HEALTH_MAIL_ENABLED=false`
  (override de entorno, NO es un cambio de código; en `.env` queda el health OK cuando haya salida SMTP).
- **Redis**: para claves simples (denylist, rate limit) se usa `StringRedisTemplate`
  (auto-configurado por Boot); el `RedisTemplate<String,Object>` (sessión 3) queda para cachear
  JSON en el Bloque 5.
- **`server.forward-headers-strategy: framework`**: detrás de Nginx la IP de `getRemoteAddr()`
  ya es la del cliente vía `X-Forwarded-For` (clave para el rate limit por IP en producción).
- **Cuerpo de errores**: los 401 lanzados con `ResponseStatusException` desde el controlador
  llegan con el body del `JwtAuthEntryPoint` (viajan por `/error`, que no es público). Funciona,
  pero unificar la forma de los errores (401/403/429/400) queda para el **Bloque 7**.
- `jq` y las contraseñas por variable/stdin (`docker exec -e PGPASSWORD/REDISCLI_AUTH`) evitan
  exponer secrets en el chat: mantener esa costumbre.

## Hoja de ruta viva (detalle en docs/PLAN-API.md)

0-3 ✔ · **4 Auth JWT ✔** · 5.1 API pública profile+projects (siguiente) · 5.2 certificates · 5.3 contacto+CV ·
6 CRUD admin · 7 Contrato OpenAPI.

---

## Prompt para la siguiente sesión (copiar/pegar)

```
Retomamos el proyecto pepedev-portfolio (docs en /home/pepe-dev/Projects/pepedev-portfolio).

Contexto cerrado (lee en orden docs/SESION4.md, docs/PLAN-API.md, docs/MODELO-DATOS.md y docs/REQUIREMENTS.md §6):
- Monorepo: api/ (Spring Boot 4.1.1, Java 21, Maven, dev.pepe1603.api) compilando;
  web/ (Nuxt, futuro); docs/. Bloques 0-3 y **4 (auth JWT) COMPLETADOS** y commiteados.
- BD/Redis remotos en Terramount POR TÚNEL SSH (ssh -L 5432:localhost:5432 -L 6379:localhost:6379 teramont-dev)
  como contenedores Docker (postgres:18, redis:7-alpine).
- Seguridad/auth: ADMIN en users = 000316jose@gmail.com (recreado por AdminBootstrap con el .env actual,
  hash BCrypt del APP_ADMIN_SECRET vigente). Login JWT OK: access Bearer 15 min + refresh cookie httpOnly
  (SameSite=Lax, Path=/auth, Secure por env), rotación con denylist jwt:{jti} en Redis, logout revoca ambos,
  rate limit de login 5/IP + 10/email por 15 min (INCR+EXPIRE, reset en éxito, 429+Retry-After).
- Reglas: sin Docker en desarrollo; secrets solo por variables de entorno (.env, gitignored; .env.example
  versionado); Flyway gestiona el schema (ddl-auto: validate); bilingüe ES/EN en JSONB {es,en};
  conectar a BD/Redis vía túnel sin exponer contraseñas en el chat (PGPASSWORD/REDISCLI_AUTH por docker exec).

Notas de operación importantes:
- El health /actuator/health depende del login SMTP real (MailHealthIndicator). Desde esta máquina
  smtp.resend.com:587 puede NO ser alcanzable (health DOWN y lento ~134 s). Para verificar en dev usa
  el arranque con MANAGEMENT_HEALTH_MAIL_ENABLED=false (solo override de entorno, no de código).
- Para claves Redis simples (denylist, rate limit) usar StringRedisTemplate; el RedisTemplate<String,Object>
  JSON queda para la caché del Bloque 5.
- Los 401 desde controladores (ResponseStatusException) salen con el body del JwtAuthEntryPoint:
  unificar el formato de errores se hará en el Bloque 7.

Método de trabajo (pair programming / mentoría):
- Yo (humano) desarrollo la API poco a poco, clase por clase, siguiendo docs/PLAN-API.md.
- Tú (IA) eres mi mentor: revisas mi código, me dices si está bien y qué corregir y por qué.
- Solo escribes tú el código cuando te lo pida explícitamente, y después me lo explicas para que aprenda.
- Se avanza commit por commit (en el Bloque 4 decidimos UN commit por pieza para más control).

Tarea de la próxima sesión:
- Empezar el Bloque 5.1 (API pública: profile singleton + projects listado/detalle por slug) siguiendo
  el plan. PRIMERO preséntame el desglose y los puntos abiertos (caché Redis + ETag: claves, TTL,
  invalidación ante ediciones CRM futuras; DTO/mapeo del JSONB {es,en} con fallback de idioma; qué campos
  expone el DTO público) ANTES de escribir código.
- Objetivo del bloque: GET /public/profile y GET /public/projects (+ /public/projects/{slug}) con caché Redis
  ligera, listo para el front Nuxt.
```

---

*Fin de la sesión 4.*