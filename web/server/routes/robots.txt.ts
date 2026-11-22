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
// El panel vive bajo /admin y no aporta nada al índice: es privado, exige
// sesión y no hay enlaces públicos hacia él. Se excluye con Disallow para
// que ningún rastreador lo visite siquiera (ahorrando peticiones que
// acabarían en 401/403). Es una capa de cortesía: lo que de verdad impide
// la indexación es el `noindex` que ponen los propios layouts del panel y
// del acceso (ver app/layouts/dashboard.vue y app/layouts/auth.vue).
//
// Las pantallas de acceso (/login, /recuperar, /verificar, /restablecer)
// también se excluyen: son flujos, no contenido, y ensucian el índice.
// ======================================================================
export default defineEventHandler((event) => {
  const config = useRuntimeConfig(event)
  const base = config.public.siteUrl.replace(/\/$/, '')

  setResponseHeader(event, 'content-type', 'text/plain; charset=UTF-8')

  return [
    'User-agent: *',
    'Allow: /',
    '',
    'Disallow: /admin',
    'Disallow: /login',
    'Disallow: /recuperar',
    'Disallow: /verificar',
    'Disallow: /restablecer',
    '',
    `Sitemap: ${base}/sitemap.xml`,
    ''
  ].join('\n')
})
