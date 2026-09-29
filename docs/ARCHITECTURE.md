# Arquitectura e infraestructura — pepedev-portfolio

> Estado: borrador v2 · Última actualización: 2026-08-24

## 1. Visión general

**Hoy (desarrollo)** — apps en la laptop, datos en Terramount:

```
┌─────────────────────────────┐        URL / credenciales
│  Laptop (sin Docker)        │   ┌───────────────────────────┐
│  ├─ web :3000 (Nuxt dev)    │──▶│ VPS Terramount · miniOS   │
│  └─ api :8080 (Spring Boot) │   │ ├─ PostgreSQL :5432       │
└─────────────┬───────────────┘   │ └─ Redis      :6379       │
              │ release tarball   └───────────────────────────┘
              ▼  (rsync a ambos VPS)
     [ miniOS: artefactos ]  +  [ Hostinger: copia de releases ]
```

**Fase final (producción)** — todo-en-uno en Hostinger:

```
                Internet · HTTPS (dominio)
                          │
        ┌─────────────────▼──────────────────┐
        │      VPS Hostinger (producción)    │
        │  Reverse proxy (Caddy/Nginx, TLS)  │
        │   ├── web : Nuxt SSR       :3000   │
        │   ├── api : Spring Boot    :8080   │
        │   ├── PostgreSQL (solo local)      │
        │   └─ Redis      (solo local)       │
        └───────────┬────────────────────────┘
                    │ backups programados
        ┌───────────▼─────────────┐
        │ VPS Terramount (miniOS) │
        │ artefactos + backups    │
        └─────────────────────────┘
```

Principios:

- **Desarrollo local sin Docker**: `mvnw spring-boot:run` y `pnpm dev`; Postgres y Redis
  se consumen remotos (Terramount) para no saturar la laptop.
- **Docker solo al final**: empaquetado y migración a Hostinger cuando todo funcione local.
- **Configuración por entorno**: URLs, API keys y secretos solo por variables de entorno.

## 2. Decisiones tecnológicas

| Decisión | Elección | Justificación |
|---|---|---|
| Backend | **Spring Boot 4.1 · Java 21 · Maven** | Ya domino Spring (JWT, Hibernate): entrega rápida y el portafolio demuestra mi skill más fuerte ante reclutadores. *NestJS descartado*: curva de aprendizaje innecesaria retrasaría la entrega. *Express descartado*: sin estructura suficiente para un CRM. *Ver SESION2: 4.1.1 es la estable que ofrece Initializr (la serie 3.x ya no aparece)* |
| ORM | JPA / Hibernate | Conocimiento previo directo; equivalente natural en Java |
| Migraciones | Flyway | Versionado de schema reproducible en dev y prod |
| Seguridad | Spring Security + jjwt + BCrypt | Patrón que ya manejo; filtros por endpoint |
| Docs API | springdoc-openapi (Swagger UI) | El contrato OpenAPI es la fuente para consumir desde Nuxt |
| Frontend | Nuxt 3 + TailwindCSS | SSR/SSG → SEO obligatorio para portafolio; i18n integrado |
| DB | PostgreSQL | Relacional, encaja con el modelo |
| Caché | Redis | Rate limiting, caché de listados, refresh tokens revocados |
| Correo | **JavaMailSender + `ThreadPoolExecutor` propio, sin broker** | Best-effort: un SMTP caído nunca tumba una petición, así que el envío sale de la petición a un hilo dedicado con cola acotada. Sin RabbitMQ, reintentos ni outbox: a esta escala el correo perdido se recupera con un reenvío. Detalle y alternativas descartadas en [CORREO.md](CORREO.md) |
| Modelo de datos | **Mínimo: 4 tablas + JSONB** | Un solo dueño y volumen pequeño; lista de presentación embebida, tabla solo si hay página/filtro propio. Detalle en [REQUIREMENTS §6](REQUIREMENTS.md#6-modelo-de-datos) |

Nota: al ser Java ↔ TypeScript no se comparten tipos; el contrato único es el OpenAPI
que genera Spring (`/v3/api-docs`). Si se desea, se generará un cliente TS para el front.

## 3. Estructura del repositorio

```
pepedev-portfolio/
├── portfolio-api/               # Spring Boot (Maven) · generado en start.spring.io
│   └── src/main/java/.../
│       ├── config/           # SecurityConfig, RedisConfig, CORS, OpenAPI
│       ├── module/
│       │   ├── auth/         # login, JWT filter, refresh, user admin
│       │   ├── project/
│       │   ├── certificate/
│       │   ├── message/      # contacto
│       │   ├── storage/      # subida de archivos (devuelve URL: CV, imágenes)
│       │   └── health/
│       ├── common/           # excepciones globales, mappers, utilidades
│       └── PortfolioApplication.java
│   └── src/main/resources/
│       ├── application.yml   # solo defaults neutros; secrets por env
│       └── db/migration/     # Flyway V1__*.sql
├── web/                       # Nuxt 3 (a crear con nuxi)
│   ├── app/                  # pages, components, composables, layouts
│   │   └── pages/
│   │       ├── index.vue         # landing
│   │       ├── proyectos/[slug].vue
│   │       ├── certificados.vue
│   │       └── contacto.vue
│   ├── i18n/                 # locales es/en
│   └── nuxt.config.ts
├── docs/
├── docker/                       # Dockerfile.api, Dockerfile.web, compose.yml (fase final)
├── scripts/                      # release.sh, backup.sh
└── .env.example
```

## 4. Infraestructura

### 4.1 VPS Terramount — entorno de desarrollo (miniOS)

- Aloja **PostgreSQL y Redis de desarrollo**, consumidos por la laptop vía URL.
- Firewall: 5432/6379 abiertos SOLO a la IP de mi conexión (o túnel SSH, preferido).
- Almacén de artefactos: cada release (tarball del build) se archiva aquí vía rsync.
- Destino también de los backups de producción cuando exista (dump PG + uploads).
- Sin dominio necesario; puede vincularse uno si algún día se quiere staging público.

### 4.2 VPS Hostinger — producción oficial

- Se migra SOLO cuando todo funcione correctamente en local (regla del proyecto).
- Todo-en-uno detrás de reverse proxy con TLS automático:
  `/` → web (:3000), `/api` y `/uploads` → api (:8080).
- PostgreSQL y Redis corriendo en el mismo VPS, escuchando solo en localhost/red interna
  (nunca expuestos a Internet).
- Firewall: solo 80/443 públicos; SSH por clave.
- Directorio `/srv/portfolio/releases/` para builds históricos (permite rollback).

## 5. Entornos

| Entorno | Apps | DB / Cache | Docker | Notas |
|---|---|---|---|---|
| dev | Laptop (Nuxt dev :3000, mvnw :8080) | Terramount remoto | No | Datos de desarrollo |
| prod | Hostinger (builds SSR/JAR tras proxy) | Hostinger local | Sí | Solo tras validar en dev |

Variables de entorno principales (plantilla completa en `.env.example`, nunca versionada):

```bash
# api (Spring Boot)
SPRING_DATASOURCE_URL=jdbc:postgresql://<terramount-o-hostinger>:5432/portfolio
SPRING_DATASOURCE_USERNAME=***
SPRING_DATASOURCE_PASSWORD=***
SPRING_DATA_REDIS_HOST=***
SPRING_DATA_REDIS_PORT=6379
SPRING_DATA_REDIS_PASSWORD=***
APP_JWT_SECRET=***
APP_JWT_REFRESH_SECRET=***
APP_CORS_ALLOWED_ORIGINS=http://localhost:3000,https://dominio.com
SPRING_MAIL_HOST=***  SPRING_MAIL_PORT=***  SPRING_MAIL_USERNAME=***  SPRING_MAIL_PASSWORD=***
APP_CONTACT_DEST_EMAIL=***
APP_UPLOAD_DIR=/srv/portfolio/uploads
JAVA_OPTS=-Xms128m -Xmx512m

# web (Nuxt)
NUXT_PUBLIC_API_BASE_URL=http://localhost:8080   # prod: https://dominio.com/api
NUXT_PUBLIC_SITE_URL=http://localhost:3000       # prod: https://dominio.com
```

## 6. Despliegue

**Dev (actual)**: sin Docker. `./mvnw spring-boot:run` + `pnpm --filter web dev`,
migraciones aplicadas por Flyway al arrancar la API contra Terramount.

**Migración a Hostinger (fase final)**:

1. Dockerfiles multi-stage: API sobre `eclipse-temurin:21-jre-alpine`;
   web con build Node y salida standalone.
2. `docker/compose.yml` con perfiles web/api/db/redis/proxy y `.env` del servidor.
3. `scripts/release.sh`: build → tarball versionado → `rsync` a **Hostinger** (desplegar)
   y a **Terramount** (archivar).
4. Health checks (`/actuator/health`) y restart policy `unless-stopped`.
5. Backups programados: `pg_dump` + carpeta uploads → Terramount con retención
   7 diarios / 4 semanales / 3 mensuales.

Rollback: conservar penúltimo tarball/imagen etiquetada; re-desplegar en un comando.

## 7. Seguridad

- Login CRM: BCrypt, JWT access corto (~15 min) + refresh rotativo httpOnly;
  denylist de refresh revocados en Redis; rate limit en `/api/auth/**`.
- Acceso por teclas combinadas = discreción de UI únicamente; la seguridad real es backend.
- Rate limiting global del contacto con Redis (p. ej. Bucket4j).
- CORS whitelist por variable de entorno; headers de seguridad en proxy y API.
- Uploads: validación de mime/tamaño, nombres aleatorios, servidos fuera del classpath.
- Secrets nunca en git; `.env` con permisos 600 en servidores; rotación documentada.
- Auditoría de dependencias periódica (`./mvnw versions:display-dependency-updates`, Dependabot).

## 8. Observabilidad

- Spring Boot Actuator: `/actuator/health` (DB, Redis, disco), métricas básicas.
- Logs estructurados JSON, nivel configurable, rotación con logrotate.
- Monitor externo de uptime opcional (Uptime Kuma) apuntando al health check.

## 9. Pruebas

- Unitarias: JUnit 5 + Mockito (servicios, lógica de negocio).
- Integración liguera en dev sin Docker: H2 en memoria con dialecto compatible;
  Testcontainers (PostgreSQL real) queda para la fase Docker/final.
- E2E smoke del flujo crítico (landing → contacto) con Playwright: opcional, fase 4.
