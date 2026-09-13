# Requisitos del producto — pepedev-portfolio

> Estado: borrador v2 · Última actualización: 2026-08-24

## 1. Contexto y objetivo

Crear un portafolio web personal, público y de alto rendimiento que presente mi perfil,
proyectos y certificaciones a **reclutadores y empresas**, con un panel CRM privado para
gestionar todo el contenido. El sitio debe demostrar, por sí mismo, buenas prácticas de
ingeniería (SEO, rendimiento, seguridad, i18n).

- Perfil GitHub: <https://github.com/pepe1603>
- Producción final: VPS Hostinger (migrar cuando todo funcione localmente).

Objetivos medibles:

- Que un reclutador entienda quién soy, qué hago y cómo contactarme en menos de 30 segundos.
- Lighthouse >= 90 en Performance, SEO, Accessibility y Best Practices.
- Contenido gestionable sin tocar código (CRM propio).

## 2. Audiencia

| Perfil | Qué busca | Implicación de diseño |
|---|---|---|
| Reclutadores / RRHH | Perfil rápido, stack, certificados, CV, contacto | Hero claro, CV descargable, CTA visible |
| Tech leads / empresas | Calidad técnica, proyectos reales, código | Detalle de proyectos, links a repos, perfil GitHub |
| Otros devs / comunidad | Mi trabajo y aprendizaje | Secciones claras y navegación simple |

## 3. Alcance

### Dentro del alcance (v1)

- Vista landing page (home completa).
- Proyectos con slug y página de detalle.
- Sección de certificados y cursos.
- Contacto + descarga de CV (ES/EN).
- CRM privado con auth JWT y acceso mediante teclas combinadas.
- Multiidioma español e inglés.
- Enlace/integración con el perfil de GitHub.

### Fuera del alcance (v1)

- **Blog** (descartado explícitamente por decisión propia).
- Comentarios, newsletter, app móvil, multiusuario con roles avanzados.

## 4. Requisitos funcionales

Prioridad MoSCoW: `M` = Must, `S` = Should, `C` = Could.

### RF-01 · Landing page (`M`)

- Hero con nombre, rol/título y CTA ("Ver proyectos", "Descargar CV", "Contacto").
- Sección "Sobre mí" breve con foto/avatar.
- Skills agrupadas por categoría (lenguajes, frameworks, herramientas).
- Experiencia/línea de tiempo.
- Destacados: máx. 3 proyectos y/o certificados recientes.
- Enlaces sociales visibles (GitHub, LinkedIn, email).

### RF-02 · Proyectos (`M`)

- URLs amigables por slug (`/proyectos/{slug}`).
- Listado paginado o scroll infinito con filtros por stack/tecnología.
- Tarjeta: título, resumen, tags de stack, miniatura.
- Detalle: descripción completa, galería de imágenes, rol propio, problemas resueltos
  y decisiones técnicas, link a repo y demo, periodo.
- Estados borrador/publicado; solo lo publicado es visible públicamente.

### RF-03 · Certificados y cursos (`M`)

- Listado filtrable por tipo (certificado / curso) y emisor.
- Tarjeta: título, emisor, fecha, imagen, enlace a credencial verificable
  (p. ej. Platzi, freeCodeCamp, AWS, Cisco).
- Orden configurable desde el CRM.

### RF-04 · Contacto + CV (`M`)

- Formulario: nombre, email, asunto, mensaje.
- Anti-spam: honeypot + rate limiting por IP (Redis) + validación server-side.
- Mensaje almacenado en DB y notificado a mi email vía SMTP configurable.
- CV en PDF descargable (versiones ES y EN) con botón siempre accesible.

### RF-05 · CRM / Panel admin (`M`)

- Acceso discreto: combinación de teclas en el sitio público (p. ej. `Ctrl+Shift+A`)
  abre un modal de login o navega a una ruta oculta no indexada.
- Auth real en backend: email + password, JWT access (~15 min) + refresh rotativo,
  BCrypt, rate limit en login.
  > Nota: el atajo de teclado es discreción, NO seguridad. La seguridad vive en el backend.
- CRUD de proyectos: crear/editar/publicar/despublicar/reordenar destacados.
- CRUD de certificados y cursos.
- Bandeja de mensajes de contacto (marcar leído/archivado/borrar).
- Subida de archivos (imágenes, PDFs, CV): el upload devuelve una URL que se guarda en
  la columna/array correspondiente de la entidad (en `profile`, `projects` o `certificates`).
- Gestión del CV: subir el PDF y activar `cv_url_es` / `cv_url_en` en `profile`.

### RF-06 · Internacionalización (`S`)

- Sitio completo ES/EN con selector persistente.
- Contenido dinámico bilingüe; si falta traducción se sirve la disponible con aviso sutil.

### RF-07 · SEO y compartir (`M`)

- SSR/SSG en Nuxt; meta tags por página (title, description, Open Graph, Twitter Card).
- `sitemap.xml` autogenerado y `robots.txt` (con `/admin` deshabilitado).
- Datos estructurados JSON-LD (Person, CreativeWork).
- Rutas ocultas del CRM excluidas de indexación.

### RF-08 · Integración GitHub (`S`)

- Botones/enlaces al perfil <https://github.com/pepe1603> en landing y proyectos.
- Opcional: mostrar repos fijados vía GitHub API con caché en Redis (evita límites de rate).

### RF-09 · Métricas (`C`)

- Analítica respetuosa con la privacidad (Umami autoalojado o similar), sin cookies invasivas.

## 5. Requisitos no funcionales

| ID | Categoría | Requisito |
|---|---|---|
| RNF-01 | Rendimiento | TTFB < 500 ms en producción; LCP < 2.5 s; imágenes WebP/AVIF con lazy loading |
| RNF-02 | SEO | SSR, HTML semántico, JSON-LD, URLs por slug estables |
| RNF-03 | Accesibilidad | WCAG 2.1 AA: contraste, teclado, alt, focus visible |
| RNF-04 | Responsive | Mobile-first, usable de 320 px a 4K |
| RNF-05 | Seguridad | HTTPS forzado; BCrypt; JWT con expiración y rotación; rate limiting; validación server-side; headers de seguridad; secrets solo por entorno |
| RNF-06 | Privacidad | Consentimiento en formulario; política de privacidad |
| RNF-07 | Disponibilidad | Health checks (API, DB, Redis); restart automático |
| RNF-08 | Mantenibilidad | Código limpio; ESLint/Prettier (web); Spotless/Checkstyle opcional (api); tests en lógica crítica |
| RNF-09 | Portabilidad | Todo configurable por variables de entorno documentadas en `.env.example` |
| RNF-10 | Recursos | JVM ajustada (-Xmx256m–512m); adecuado para VPS ~2 GB junto a Nuxt, PostgreSQL y Redis |

## 6. Modelo de datos

Decisión de diseño: un solo dueño y volumen pequeño (≤15 proyectos, ≤10 certificados)
→ **modelo mínimo de 3 tablas de contenido + usuarios de auth**. Regla de oro:

> **Tabla** = entidad con página propia o filtro propio (slug/detalle/SEO, o consulta
> por emisor/tipo). **JSONB embebido** = lista de presentación sin detalle ni filtro.

No hay tablas puente, ni tabla de media, ni catálogo de tecnologías: skills, experiencia,
stack y galerías viven embebidas en su entidad. Contenido siempre bilingüe ES/EN en columnas
JSONB `{ "es": "...", "en": "..." }`; si falta un idioma, se sirve el disponible.

### users — auth, no contenido (Spring Security)
1 fila. Credenciales + rol admin. Sin relación con las tablas de contenido (un único dueño).
Contiene `email, password_hash, role, created_at, last_login_at`.

### profile — 1 fila (singleton: quién soy)
| Grupo | Campos |
|---|---|
| Identidad | `full_name`, `headline`, `bio{es,en}`, `location` |
| Social | `github_url`, `linkedin_url`, `email_public`, `website_url` |
| CV | `cv_url_es`, `cv_url_en` (el "CV activo"; sin tabla `cvs`) |
| Imagen | `avatar_url` |
| Skills (JSONB) | `[{name, category, level}]` |
| Experiencias (JSONB) | `[{title, company, period, type, description}]` |
| Otros | `views_count`, `created_at`, `updated_at` |

### projects — 1 fila por proyecto (única entidad con SEO propio)
| Grupo | Campos |
|---|---|
| Identidad/SEO | `slug` (único), `title{es,en}`, `subtitle`, `summary{es,en}`, `description_md{es,en}` |
| Visuales | `thumbnail_url`, `gallery[] (JSONB)` = `[{url, alt, caption}]` |
| Enlaces | `repo_url`, `demo_url` |
| Stack (JSONB) | `["Spring Boot", "Nuxt", …]` — el filtro público es un `contains` |
| Periodo | `period_start`, `period_end` |
| Presentación | `is_featured`, `sort_order` |
| Ciclo | `status(draft/published)`, `published_at`, `created_at`, `updated_at` |

### certificates — 1 fila por certificado/curso (filtro propio)
| Grupo | Campos |
|---|---|
| Datos | `title{es,en}`, `issuer`, `kind(certificate\|course)`, `issue_date`, `expiry_date(null)` |
| Credencial | `credential_url`, `image_url` |
| Presentación | `is_featured`, `sort_order`, `status`, `created_at` |

### messages — 1 fila por mensaje (inbox)
`name, email, subject, body, ip, user_agent, status(new/read/archived), created_at`.

### Relaciones
**No hay claves foráneas entre tablas de contenido.** Cada página pública se resuelve con
una sola lectura (sin joins, sin N+1); `users` solo autentica el CRM.

### ¿Por qué este modelo y por qué es suficiente?
- `technologies` + `project_technologies` (join) → **`stack[]`**: sin catálogo compartido
  que evolucione solo; el filtro es un `contains` sobre un array.
- `skills` tabla → **JSONB en `profile`**: presentación pura en la landing.
- `experiences` tabla → **JSONB en `profile`**: timeline de presentación.
- `media` tabla polimórfica → **URLs en columnas/arrays** de cada entidad; los archivos
  viven en Storage/upload y la URL se guarda donde se usa.
- `cvs` tabla → **dos URLs en `profile`**.

Se normalizaría solo si el producto creciera a: multiusuario con autores, métricas por
tecnología, catálogo de media con selector, o miles de proyectos. No es este producto.

Redis (uso previsto): rate limiting (contacto/login), caché de respuestas públicas
(invalidar en cada edición del CRM), lista de refresh tokens revocados, caché opcional
de la GitHub API (RF-08).

## 7. Definición de hecho (DoD)

Una feature se considera terminada cuando:

- Funciona en local (sin Docker) contra Postgres/Redis remotos en Terramount.
- Pasa lint/typecheck (web) y compilación + tests (api).
- Cubre versión ES y EN si aplica.
- Cumple accesibilidad básica (teclado + labels) y responsive.
- No introduce secretos en código; usa variables de entorno documentadas.
