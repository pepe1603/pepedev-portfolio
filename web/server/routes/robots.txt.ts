// ======================================================================
// GET /robots.txt
//
// Servido por nitro en vez de un fichero estático en public/: la línea
// del sitemap lleva el dominio, y el dominio vive en
// runtimeConfig.public.siteUrl. Con un fichero estático habría que
// hardcodearlo, y el hardcode rompería el día que cambie el dominio.
//
// --------------------------------------------------------------------------
// LAS RUTAS ADMINISTRATIVAS
//
// Todavía no existe ninguna ruta /admin en el front (la Fase B del CMS
// vendrá después), así que ahora todo es indexable. Cuando esas rutas
// entren, aquí se añade su Disallow y hay que sacarlas también del
// sitemap (ver server/routes/sitemap.xml.ts).
// ======================================================================
export default defineEventHandler((event) => {
  const config = useRuntimeConfig(event)
  const base = config.public.siteUrl.replace(/\/$/, '')

  setResponseHeader(event, 'content-type', 'text/plain; charset=UTF-8')

  return [
    'User-agent: *',
    'Allow: /',
    '',
    `Sitemap: ${base}/sitemap.xml`,
    ''
  ].join('\n')
})
