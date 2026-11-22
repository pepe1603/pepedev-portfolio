# Plan de Docker y despliegue — pepedev-portfolio

Documento de seguimiento para la fase final: empaquetado con Docker, CI/CD con
GitHub Actions y despliegue en el VPS de producción (Hostinger). Las tareas se
marcan con `[ ]` (pendiente) / `[x]` (hecho).

Estado actual: desarrollo de `web/` y `api/` terminado en la rama `develop`
(67 commits por delante de `origin/develop`). El empaquetado se desarrolla en la
rama `feature/docker`. No se ha hecho push todavía.

## 0. Preparación del repositorio (hecho)

- [x] Verificar ramas locales sin merge pendiente (`feature/admin-auth` → `develop`).
- [x] Eliminar ramas feature locales ya integradas.
- [x] Sincronizar `main` local con `origin/main`.
- [x] Crear rama `feature/docker` desde `develop`.
- [x] Limpiar `docs/` (sesiones a `docs-archive/`, gitignored).
- [x] `web` y `api` compilan en local (`pnpm build`, `mvnw package`).

## 1. Empaquetado local con Docker (en progreso)

- [x] `docker/Dockerfile.api` (multi-stage Maven → Temurin JRE alpine).
- [x] `docker/Dockerfile.web` (multi-stage pnpm → node runtime SSR).
- [x] `docker/entrypoint.api.sh` (carga `/etc/portfolio.env` con shell).
- [x] `docker-compose.yml` con `postgres`, `redis`, `minio`, `api`, `web`.
- [x] `.dockerignore` (raíz, `api/`, `web/`).
- [x] Ajuste Nuxt: `runtimeConfig.apiBaseServer` + `NUXT_API_BASE_SERVER` para SSR.
- [ ] Instalar Docker en la máquina de desarrollo y levantar `docker compose up -d --build`.
- [ ] Verificar healthchecks (`/actuator/health`, web, minio).
- [ ] Probar flujo completo: login admin, subida de media, CV, contacto, imágenes.
- [ ] Ajustar MinIO healthcheck (curl vs `mc ready`) si falla.
- [ ] Añadir healthcheck de web validado (respuestas 200).
- [ ] Documentar variables de entorno necesarias para Docker en `.env.example`.

## 2. Entorno de desarrollo reproducible

- [ ] Crear `docker/.env.example` limpio (sin comentarios inline) para Docker.
- [ ] Añadir script `scripts/dev.sh` (o Makefile) para `docker compose up`.
- [ ] Definir volúmenes nombrados y política de backup local (pg_dump, mc mirror).
- [ ] Probar migración Flyway en contenedor limpio (BD vacía).
- [ ] Probar bucket: la API crea `portfolio` al arrancar.

## 3. Proxy TLS y producción local

- [ ] Añadir servicio `caddy` (o nginx) al compose con perfil `prod`.
- [ ] Crear `docker-compose.prod.yml` (sin puertos expuestos de BD/Redis/MinIO).
- [ ] Configurar `APP_JWT_REFRESH_COOKIE_SECURE=true`, CORS de producción.
- [ ] `APP_STORAGE_PUBLIC_URL` y `NUXT_PUBLIC_API_BASE` con dominio real.
- [ ] Probar TLS local con certificados de desarrollo (mkcert) o staging.

## 4. CI con GitHub Actions

- [ ] Crear `.github/workflows/ci.yml` en la raíz.
- [ ] Job `api`: checkout, setup-java 21, `./mvnw -q test` (con Testcontainers o H2).
- [ ] Job `web`: checkout, setup-node, pnpm install, `pnpm lint`, `pnpm typecheck`, `pnpm build`.
- [ ] Job `docker`: build de imágenes (sin push) para validar Dockerfiles.
- [ ] Caché de dependencias (Maven, pnpm).
- [ ] Ejecutar CI en PRs hacia `develop` y `main`.

## 5. CD / despliegue en VPS (Hostinger)

- [ ] Crear `.github/workflows/deploy.yml` (trigger: push a `main` o tag).
- [ ] Build y push de imágenes a un registry (GHCR) con tag por commit.
- [ ] Acceso SSH al VPS desde GitHub Actions (secretos: `SSH_HOST`, `SSH_USER`, `SSH_KEY`).
- [ ] Script de despliegue en el VPS: `docker compose pull && up -d`.
- [ ] Migraciones Flyway automáticas al arrancar la API.
- [ ] Variables de entorno de producción en `/etc/portfolio.env` (permiso 600).
- [ ] Backup automatizado: pg_dump + `mc mirror` del bucket (cron).
- [ ] Healthcheck post-deploy y rollback si falla.

## 6. Merge y release

- [ ] Mergear `feature/docker` → `develop`.
- [ ] Validar en `develop` (CI verde).
- [ ] Mergear `develop` → `main` (release).
- [ ] Etiquetar release (semver) y desplegar.
- [ ] (Opcional) push de `develop` y `main` a `origin`.

## Notas

- No se hace push hasta validar todo localmente.
- `docs-archive/` (sesiones) no se versiona.
- MinIO nunca se expone a Internet; la API hace de proxy en `/files/**`.
