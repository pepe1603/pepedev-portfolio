# Guía de producción — pepedev-portfolio (backend API)

Runbook para desplegar `api` (Spring Boot 4.1.1 · Java 21) detrás de un proxy con TLS.
**No hay infraestructura desplegada todavía**: esta es la guía canónica para cuando exista.

## 1. Arquitectura objetivo

```
Internet ── HTTPS:443 ──> proxy (Caddy/nginx) ── HTTP:8080 ──> API (spring-boot:run o jar)
                                                          ├──> PostgreSQL (ya en Terramount)
                                                          └──> Redis (ya en Terramount)
```

- El proxy termina TLS, hace passthrough al puerto 8080 y **re-escribe la cabecera
  `X-Forwarded-*`/`Host`**. La API ya usa `server.forward-headers-strategy: framework`,
  así las cookies, links y el rate limit por IP ven la IP real del cliente.
- Como no hay Docker, el despliegue es: **jar + systemd** en el mismo host del proxy.

## 2. Variables de entorno (`/etc/api.env`, permiso `600`)

Copiar de `.env.example` y **ajustar**:

| Variable | Producción | Notas |
|---|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://<host>:5432/portfolio` | apuntar a la BD real, no al túnel |
| `SPRING_DATASOURCE_USERNAME`/`PASSWORD` | usuario de la BD | `APP_ADMIN_*` **no** sirve para JDBC |
| `SPRING_DATA_REDIS_HOST`/`PORT`/`PASSWORD` | Redis del nodo | |
| `APP_JWT_SECRET` / `APP_JWT_REFRESH_SECRET` | `openssl rand -base64 64` (dos **distintos**) | **rotar antes de exponer**; rotar = invalidar refresh |
| `APP_JWT_REFRESH_COOKIE_SECURE` | **`true`** | imprescindible con HTTPS (la cookie solo viaja por TLS) |
| `APP_CORS_ALLOWED_ORIGINS` | `https://pepe1603.dev` (+ staging si aplica) | origin(s) real(es), separados por coma; **sin** `http://localhost:3000` |
| `APP_ADMIN_EMAIL` / `APP_ADMIN_SECRET` | credenciales admin reales | AdminBootstrap solo crea al admin si `users` está vacía |
| `SPRING_MAIL_*` | SMTP Resend en producción | `MAIL_HOST=smtp.resend.com`, `MAIL_USERNAME=resend` |
| `APP_CONTACT_DEST_EMAIL` / `APP_CONTACT_FROM_EMAIL` | buzón destino + remitente verificado en Resend | si `FROM` vacío: no se notifica por mail (best-effort) |
| `APP_LOGIN_RATE_*` / `APP_CONTACT_RATE_*` | mantener defaults o endurecer | 429 + `Retry-After` |
| `APP_PUBLIC_CACHE_TTL` | `300` (segundos) | caché Redis de `/public/*` + ETag |
| `APP_S3_ENDPOINT` | `http://minio:9000` (MinIO en Docker junto a la API) | si MinIO va en otra máquina, su host; nunca se publica a Internet |
| `APP_S3_ACCESS_KEY` / `APP_S3_SECRET_KEY` | credenciales del bucket | **nunca** las de `minioadmin` |
| `APP_S3_BUCKET` / `APP_S3_REGION` | `portfolio` / `us-east-1` | el bucket lo crea la API al arrancar |
| `APP_STORAGE_PUBLIC_URL` | `https://pepe1603.dev/files` | debe ser la URL pública para construir URLs correctas. **Cambiarla obliga a migrar las URLs ya guardadas** en la BD |

Notas:
- **Nunca** usar los secrets de `.env` local en producción.
- La cookie refresh manda `Secure; HttpOnly; SameSite=Lax; Path=/auth`.
- Los `SPRING_MAIL_*` vacíos/erróneos NO frenan el health salvo que el `MailHealthIndicator`
  los valide (`MANAGEMENT_HEALTH_MAIL_ENABLED=false` para omitirlo durante mantenimiento).

## 3. Build y arranque

```bash
cd api
./mvnw -q -DskipTests package            # genera target/api-*.jar
APP_...=/etc/api.env           # conjunto de vars de producción

# arranque manual (pruebas)
setsid nohup bash -c 'set -a; . /etc/api.env; set +a; \
  exec java -jar target/api-*.jar' > /var/log/api.log 2>&1 &
```

### systemd (recomendado)

```ini
# /etc/systemd/system/api.service
[Unit]
Description=api (Spring Boot)
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=portfolio
WorkingDirectory=/srv/portfolio/api
EnvironmentFile=/etc/api.env
ExecStart=/usr/bin/java -jar /srv/portfolio/api/target/api-*.jar
SuccessExitStatus=143
Restart=on-failure
RestartSec=5

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now api
sudo systemctl restart api && sudo journalctl -u api -f
```

## 4. Proxy TLS (Caddy como ejemplo)

```caddyfile
pepe1603.dev {
    reverse_proxy 127.0.0.1:8080
    request_body { max_size 21MB }   # algo por encima de los 20MB de max-request-size
}
```

- **No** tocar `max-file-size`/`max-request-size` del yaml (15/20 MB); los rechazos por exceso
  ya devuelven `413` ProblemDetail JSON por `ApiExceptionHandler`/`JsonErrorController`.
- Si se usa nginx: `client_max_body_size 21m;` + `X-Forwarded-Proto`/`X-Forwarded-For`
  (el `forward-headers-strategy: framework` ya los respeta).

## 5. Verificación tras el despliegue

```bash
curl -s https://pepe1603.dev/actuator/health                      # {"status":"UP"}
curl -s https://pepe1603.dev/public/profile | jq .                # JSON público + ETag
curl -s -o /dev/null -w '%{http_code}\n' https://pepe1603.dev/swagger-ui.html   # 200
curl -s -X POST https://pepe1603.dev/auth/login -H 'Content-Type: application/json' \
     -d '{"email":"admin@pepe1603.dev","password":"correcta"}'    # 200 + Set-Cookie ...Secure
curl -s -X POST https://pepe1603.dev/contact -H 'Content-Type: application/json' -d '{}'
                                                                  # 400 ProblemDetail + errors[]
```

Checklist de seguridad visible en la respuesta del login:
- `Set-Cookie: ...; Secure; HttpOnly; SameSite=Lax; Path=/auth` ✔
- acceso por `http://` redirigido a `https://` (cortesía del proxy) ✔
- `/swagger-ui.html` expuesto: si no se quiere público en prod, bloquearlo en el proxy
  (la API no lo protege por diseño; está pensado para dev).

## 6. Operación

- **Actualización**: `git pull` → `./mvnw -q -DskipTests package` → `systemctl restart`.
  Flyway aplica migraciones nuevas en arranque (`ddl-auto: validate`; las migraciones
  **son inmutables**: nunca editar una ya aplicada).
- **Ficheros**: viven en el bucket, no en el servidor. El borrado de huérfanos es
  best-effort (tras el cambio en BD). Ya no hay un `find` de ficheros de más de 90 días: lo
  equivalente sería listar objetos del bucket cuya `lastModified` sea antigua y comprobar que
  ninguna URL de la BD los referencia. Está sin hacer a propósito (docs/STORAGE.md §4).
- **Backups**: PostgreSQL (pg_dump) y **el bucket** (`mc mirror` a otra ubicación, o el que
  traiga el despliegue); la caché Redis es reconstruible sola.
- **Monitor**: `/actuator/health` (incluye `db`, `redis`, `mail`).
- **Rotación de JWT**: cambiar `APP_JWT_SECRET`/`APP_JWT_REFRESH_SECRET` invalida todos los
  refresh (session de todos fuera); programarlo en horario de bajo tráfico.