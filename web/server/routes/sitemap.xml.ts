// ======================================================================
// GET /sitemap.xml
//
// Servido por nitro y no como fichero estático: las tres rutas fijas
// (/ , /proyectos, /certificados) no cambian, pero /proyectos/[slug]
// depende de los slugs publicados, que están en la API. Un sitemap
// escrito a mano se queda viejo con cada proyecto nuevo.
//
// Sin dependencias nuevas: el XML se arma a mano y se escapan los
// caracteres reservados. Si la API no responde, el sitemap sigue
// sirviendo las rutas fijas en vez de devolver 500.
//
// Los /admin futuros (Fase B del CMS) no deben entrar aquí; ver también
// server/routes/robots.txt.ts.
// ======================================================================
import type { ProjectSummaryDTO } from '../../app/utils/types'

export default defineEventHandler(async (event) => {
  const config = useRuntimeConfig(event)
  const base = config.public.siteUrl.replace(/\/$/, '')

  const paths = ['/', '/proyectos', '/certificados']

  try {
    // Solo el listado resumido: el sitemap necesita los slugs, nada más.
    const projects = await $fetch<ProjectSummaryDTO[]>(`${config.public.apiBase}/public/projects`, {
      params: { lang: 'es' }
    })

    for (const project of projects) {
      paths.push(`/proyectos/${project.slug}`)
    }
  } catch {
    // API caída: las rutas fijas siguen siendo válidas y los slugs se
    // recuperan en la próxima petición.
  }

  const escapeXml = (value: string) => value
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll('\'', '&apos;')

  const urls = paths
    .map(path => `  <url><loc>${escapeXml(base + path)}</loc></url>`)
    .join('\n')

  setResponseHeader(event, 'content-type', 'application/xml; charset=UTF-8')

  return `<?xml version="1.0" encoding="UTF-8"?>
<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
${urls}
</urlset>
`
})
