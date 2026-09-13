# pepedev-portfolio

Portafolio web personal orientado a reclutadores y empresas.
GitHub: <https://github.com/pepe1603>

Proyecto en fase de **planificación**. El desarrollo se hace localmente (sin Docker,
para no saturar la laptop) consumiendo PostgreSQL y Redis remotos; el empaquetado con
Docker y la migración al VPS de producción llegan solo cuando todo funcione en local.

## Stack

| Capa | Tecnología |
|---|---|
| Frontend | Nuxt 3 + TailwindCSS (SSR/SSG híbrido, i18n ES/EN) |
| Backend | Spring Boot 4.1 · Java 21 · Maven |
| Seguridad | Spring Security + JWT (access/refresh), BCrypt |
| Persistencia | JPA / Hibernate + Flyway (migraciones versionadas) |
| Base de datos | PostgreSQL |
| Caché / rate limiting | Redis |
| Docs API | springdoc-openapi (Swagger UI) |
| Empaquetado | Docker + docker compose (solo fase final) |

> Decisión: Spring Boot sobre NestJS/Express porque ya domino Spring (JWT, Hibernate).
> Detalles en [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Secciones del sitio

- **Landing**: hero, sobre mí, skills, experiencia, destacados.
- **Proyectos**: listado con filtros + detalle por slug (galería, rol, retos, repo/demo).
- **Certificados y cursos**: emisor, fecha, credencial verificable.
- **Contacto + CV**: formulario protegido y descarga de CV en PDF (ES/EN).
- **CRM privado**: acceso discreto por combinación de teclas + auth JWT.

## Infraestructura

| Nodo | Rol |
|---|---|
| Laptop | Desarrollo de `web` y `api` sin Docker |
| VPS Terramount (miniOS) | Entorno de desarrollo: aloja **PostgreSQL y Redis** que consume la laptop; además almacén de artefactos y backups |
| VPS Hostinger | Producción oficial: front + API + proxy TLS + PostgreSQL + Redis, cuando todo funcione localmente |

## Documentación

| Documento | Contenido |
|---|---|
| [docs/REQUIREMENTS.md](docs/REQUIREMENTS.md) | Objetivos, audiencia, requisitos funcionales y no funcionales, modelo de datos |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Decisiones técnicas, arquitectura, infraestructura, entornos, seguridad, despliegue |
| [docs/ROADMAP.md](docs/ROADMAP.md) | Fases del desarrollo con checklists |
| [docs/SETUP-FASE0-API.md](docs/SETUP-FASE0-API.md) | Guía paso a paso: Initializr, dependencias, secretos y primer arranque |

## Estructura

```
pepedev-portfolio/
├── portfolio-api/   # Spring Boot (generado en start.spring.io)
├── web/             # Nuxt 3 + TailwindCSS (a crear con nuxi)
├── docs/
├── docker/          # Dockerfiles y compose (fase final)
├── scripts/         # release.sh, backup.sh
├── .env.example
├── .gitignore
└── README.md
```

## Comandos de desarrollo (sin Docker)

```bash
# API (puerto 8080) — requiere DATABASE_URL apuntando a Terramount
cd portfolio-api && ./mvnw spring-boot:run

# Web (puerto 3000)
cd web && pnpm install && pnpm dev
```
