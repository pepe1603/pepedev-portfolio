# Sesión 7 · API: Bloque 5.3 — API pública contacto + CV

> Fecha: 2026-09-17 · Estado: **implementación cerrada · DoD VERIFICADO manualmente en la Sesión 8**
> (la realiza el humano con Postman usando la guía de esta misma sesión). Bloque 5.3 código
> completo: `POST /contact` (honeypot + rate limit + validación + SMTP + persistencia) e
> `GET /public/cv/{lang}` (redirect 302).

## Qué se hizo en esta sesión

El humano aprobó el desglose y las decisiones; **la IA escribió el bloque completo** (lo pidió
explícitamente) y se avanzó commit por commit:

| Commit | Pieza |
|---|---|
| `62ce705` | `dto/contact/ContactRequest` (record: name, email, subject, body, `website` honeypot + validaciones) |
| `37a923a` | `ContactRateLimiter` (Redis `rl:contact:ip:{ip}`, solo IP) + excepción |
| `9d3c9c8` | `ContactService` (anonimiza IP, persiste `Message`, mail best-effort async) |
| `8c0a038` | `ContactController` `POST /contact` (honeypot → 429 → persistencia → 201) |
| `f913083` | `CvController` `GET /public/cv/{lang}` → 302 reutilizando `pub:profile:{lang}` |
| `3136418` | docs + `.env.example` (`APP_CONTACT_FROM_EMAIL`, `APP_CONTACT_RATE_*`) |

## Decisiones cerradas en esta sesión (Bloque 5.3)

- **Orden de `POST /contact`**: `@Valid` (name≤120, email≤320 @Email, subject≤160, body 10..5000)
  → **honeypot** `website` relleno = **éxito falso idéntico** (201, sin persistir ni enviar)
  → **rate limit solo IP** (`APP_CONTACT_RATE_MAX_IP` 5 / `APP_CONTACT_RATE_WINDOW` 900 s,
  429 + `Retry-After`, body `Map` igual que el login) → persistir `Message` → mail → **201**.
- **Honeypot**: campo `website` sin anotaciones, no se persiste; ante contenido → success falso
  201 idéntico para no enseñar al bot.
- **IP anonimizada**: IPv4 `/24` (último octeto a 0), IPv6 `/64` (8 bytes a 0), IPv4-mapped
  `::ffff:a.b.c.d` normalizada antes del parseo. `ua` truncado a 255. Confianza en
  `X-Forwarded-For` igual que el login (proxy en prod).
- **SMTP best-effort async** (`CompletableFuture.runAsync`, sin `@EnableAsync`): persiste primero
  (la BD es la fuente de verdad); si falta `APP_CONTACT_FROM_EMAIL` o el SMTP falla → log, la
  respuesta sigue siendo 201. `Reply-To` = email del remitente. Sin `@Transactional` alrededor
  del envío.
- **CV**: `GET /public/cv/{lang}` (es|en) → **302 Found** (nunca 301/308, la URL cambia al
  re-subir en el CRM) **reutilizando la caché `pub:profile:{lang}`** (sin claves/evicts propios;
  el CV nunca diverge de `/public/profile`). Fallback ES↔EN; 404 si ambos nulos. Sin ETag/304
  en el redirect.
- **Validación 400** sigue saliendo por `BasicErrorController` (devtools trace en dev); el formato
  unificado de errores → Bloque 7.

## Verificación (HECHA — humano con Postman, Sesión 8)

Todos los pasos de la lista siguiente se probaron el 19/09/2026 y dieron el resultado esperado
(la única incidencia fue la URL de `PATCH /admin/projects/{id}` con llaves de Postman, no un bug
de código): 201 válido, 201 falso honeypot, 400 de validación, 429 + `Retry-After`, 302 del CV
(con URLs de prueba en `profile`) y 404 con placeholder, IP anonimizada `/24`.

La guía completa está en el historial de la sesión 7. Resumen: base `http://localhost:8080`.

1. `POST /contact` body válido (con `"website": ""`) → **201** (persiste fila).
2. `"website": "http://spam.com"` → **201 falso** (sin fila).
3. Email inválido o body corto → **400**.
4. 6 envíos válidos seguidos → 6º **429** + `Retry-After`.
5. `GET /public/cv/es` con *follow redirects OFF* → **302** + `Location`.
6. (Opcional) fila en BD: SELECT en `messages` y ver el octeto de IP a 0.

Necesario en `.env` real: `APP_CONTACT_FROM_EMAIL=` (vacío = no envía, solo persiste) y, si se
quiere cambiar el límite, `APP_CONTACT_RATE_MAX_IP` / `APP_CONTACT_RATE_WINDOW`.
El `.env` real ya está limpio (25 líneas, ya incluye `APP_CONTACT_DEST_EMAIL`); el problema de
la "línea 47" de sesiones anteriores desapareció.

## Notas de operación

- Túnel: `ssh -L 5432:localhost:5432 -L 6379:localhost:6379 teramont-dev`.
- Arranque dev: `setsid nohup bash -c 'MANAGEMENT_HEALTH_MAIL_ENABLED=false ./mvnw spring-boot:run'`
  desde `api/`; detener con `pkill -f spring-boot:run`.
- El health de SMTP puede bajar a DOWN en dev; el contacto no depende de él para persistir.
- Boot 4: recordatorio de las APIs rotas (`Sort.Order`, `redis.delete(Collection)`, `CacheControl`,
  `nullsFirst/last`) — ya absorbidas en bloques 5.x.

## Hoja de ruta viva (detalle en docs/PLAN-API.md)

0-3 ✔ · 4 Auth JWT ✔ · 5.1 profile+projects ✔ · 5.2 certificates ✔ · 5.3 contacto+CV ✔ · 6 CRM CRUD (siguiente) · 7 Contrato OpenAPI.

---

## Prompt para la siguiente sesión (copiar/pegar)

```
Retomamos el proyecto pepedev-portfolio (docs en /home/pepe-dev/Projects/pepedev-portfolio).

Contexto cerrado (lee en orden docs/SESION7.md, docs/PLAN-API.md, docs/MODELO-DATOS.md y docs/REQUIREMENTS.md §6):
- Monorepo: api/ (Spring Boot 4.1.1, Java 21, Maven, dev.pepe1603.api) compilando;
  web/ (Nuxt, futuro); docs/. Bloques 0-3, 4 (auth JWT), 5.1 y 5.2 COMPLETADOS. Bloque 5.3
  (contacto+CV) IMPLEMENTADO en 6 commits 62ce705→3136418; DoD PENDIENTE de verificación manual
  (guía Postman en SESION7).
- API pública completa: /public/profile, /public/projects[{/slug}], /public/certificates (?kind|issuer),
  POST /contact (honeypot website + rate limit solo IP rl:contact:ip + validación + persistencia en
  messages con IP anonimizada /24 o /64 + SMTP best-effort async → 201; 429 con Retry-After) y
  GET /public/cv/{lang} (302 desde profile.cvUrl*, reusa pub:profile). Solo PUBLISHED; ?lang=es|en +
  fallback bilingüe (LocalizedText); caché Redis pub:* con body JSON (TTL APP_PUBLIC_CACHE_TTL=300s);
  filtros de certificates en memoria; ETag fuerte SHA-256 → 304; evicts ES+EN listos para CRM.
- Auth JWT intacta: login → /auth/me → refresh rotativo → logout, rate limit 5/IP + 10/email por
  15 min. Admin en users = 000316jose@gmail.com. /contact ya permitAll en SecurityConfig.
- Reglas: sin Docker en dev; secrets solo por variables de entorno (.env gitignored, .env.example
  versionado, ya incluye APP_CONTACT_*); Flyway (ddl-auto: validate); bilingüe ES/EN en JSONB {es,en};
  conectar BD/Redis vía túnel sin exponer contraseñas en el chat.

Notas de operación importantes:
- El .env real ya está limpio (25 líneas) y `source .env` funciona (el problema de la línea 47
  desapareció). APP_CONTACT_DEST_EMAIL presente; APP_CONTACT_FROM_EMAIL y rate por defecto (5/900).
- Health SMTP puede bajar a DOWN en dev → arrancar con MANAGEMENT_HEALTH_MAIL_ENABLED=false.
- background: `setsid nohup bash -c 'MANAGEMENT_HEALTH_MAIL_ENABLED=false ./mvnw spring-boot:run'`
  desde api/; detener con pkill -f spring-boot:run.
- Túnel: ssh -L 5432:localhost:5432 -L 6379:localhost:6379 teramont-dev.
- Boot 4 rompe APIs de Boot 3 (Sort.Order, redis.delete(Collection), CacheControl no estático,
  nullsFirst/Last). devtools re-reinicia al recompilar; en dev los errores salen con trace.
- El formato unificado de errores (400/401/403/404/429) se hará en el Bloque 7; hoy hay un Map
  propio para 429 (login/contacto) y BasicErrorController para el resto.

Método de trabajo (pair programming / mentoría):
- Yo (humano) desarrollo la API poco a poco, clase por clase, siguiendo docs/PLAN-API.md.
- Tú (IA) eres mi mentor: revisas mi código, me dices si está bien y qué corregir y por qué.
- Solo escribes tú el código cuando te lo pida explícitamente, y después me lo explicas para que aprenda.
- Se avanza commit por commit (UN commit por pieza para más control).

Tarea de la próxima sesión:
- Bloque 6.1 (CRM: CRUD admin de projects + certificates) siguiendo el plan. PRIMERO preséntame el
  desglose y los puntos abiertos (auth exigida, endpoints bajo /admin, crear/editar/publicar/
  despublicar/ordenar/borrar, evicts de caché pública tras cada mutación, validación y 404s,
  quién gestiona slug/published_at destacados, etc.) ANTES de escribir código.
```

---

*Fin de la sesión 7.*