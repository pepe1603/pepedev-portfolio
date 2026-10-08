# Roadmap — pepedev-portfolio

> Regla del proyecto: fases 0–4 se desarrollan y validan 100% en local (apps en laptop,
> DB/Redis remotos en Terramount, sin Docker). La fase 5 empaqueta con Docker y migra a
> Hostinger solo cuando todo funcione.

## Fase 0 · Cimientos

- [ ] Repo git y push a <https://github.com/pepe1603> (repo `pepedev-portfolio`)
- [ ] Estructura: `api/` (Spring Boot Maven, generado en spring.io), `web/` (Nuxt), `docs`, `scripts`
- [ ] Spring Boot base: web, data-jpa, security, redis, mail, actuator, flyway, springdoc
- [ ] Nuxt base: Tailwind, i18n es/en, layout principal
- [ ] `.env.example` completo; `.gitignore` con `.env*`
- [ ] Linters: ESLint + Prettier (web); Spotless (api, opcional)
- [ ] Verificar conexión laptop → PostgreSQL y Redis de Terramount (ping desde API)

## Fase 1 · Landing + Proyectos (MVP público)

- [ ] Entidades + migraciones Flyway: profile, projects (modelo mínimo de datos)
- [ ] Seed inicial de proyectos
- [ ] API pública: listado y detalle de proyectos por slug (caché Redis)
- [ ] Landing: hero, sobre mí, skills, experiencia, destacados
- [ ] Páginas /proyectos: listado + filtro por stack + detalle `[slug]`
- [ ] SEO base: meta tags, sitemap.xml, robots.txt, JSON-LD Person

## Fase 2 · Certificados + CV + Contacto

- [ ] Entidad certificates + migración + seed
- [ ] API pública y página /certificados (filtro por tipo/emisor)
- [ ] Subida y servido de CV PDF (ES/EN) con botón global de descarga
- [ ] Formulario contacto: honeypot + rate limit Redis + envío SMTP + persistencia
- [ ] Página de política de privacidad

## Fase 3 · CRM privado

- [ ] Ruta oculta + combinación de teclas (p. ej. `Ctrl+Shift+A`) que abre el login
- [ ] Auth: login email/password, JWT access + refresh rotativo, guard de rutas
- [ ] CRUD proyectos (borrador/publicado, destacados, orden)
- [ ] CRUD certificados/cursos
- [ ] Bandeja de mensajes (leído/archivado/borrar)
- [ ] Gestión de media (subida de archivos → URL) y activación de CV es/en en profile
- [ ] Exclusión del CRM en sitemap/robots

## Fase 4 · Pulido

- [ ] Traducciones completas ES/EN de todo el contenido sembrado
- [ ] Accesibilidad: auditoría teclado/contraste/screen reader
- [ ] Rendimiento: imágenes optimizadas, Lighthouse >= 90 en las 4 categorías
- [ ] Tests: unitarios (JUnit 5) en auth/contact/servicios clave; smoke E2E opcional
- [ ] Contenido real: >= 3 proyectos documentados, >= 5 certificados, CV actualizado
- [ ] Sección/enlaces GitHub (<https://github.com/pepe1603>); opcional repos fijados vía API con caché

## Fase 5 · Empaquetado y migración a Hostinger

- [ ] Dockerfiles multi-stage (API temurin JRE, web standalone) + compose.yml
- [ ] PostgreSQL + Redis productivos EN Hostinger (solo escucha local, sin exposición)
- [ ] Proxy TLS automático (Caddy o Nginx + certbot); firewall cerrado (solo 80/443)
- [ ] `scripts/release.sh`: build → tarball → rsync a Hostinger y a Terramount
- [ ] Backups programados (pg_dump + uploads) hacia Terramount/miniOS con retención
- [ ] Health checks + monitor externo + restart policies
- [ ] Simulacro completo de rollback antes de apuntar el dominio

## Post-lanzamiento (continuo)

- Añadir proyectos/certificados nuevos vía CRM
- Revisar mensajes y métricas cada semana
- Actualizar dependencias y CV trimestralmente
