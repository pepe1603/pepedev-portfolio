// ======================================================================
// GET /api/search?q=...
// Busqueda asincrona del command palette sobre las secciones del portfolio.
//
// --------------------------------------------------------------------------
// POR QUE EL CATALOGO ES UN ARRAY ESTATICO
//
// Una busqueda asincrona tiene que responder a ALGO. Hardcodear un
// directorio de productos inventado seria mas realista, pero entonces el
// endpoint mentiria: buscaria cosas que no existen en esta pagina. Con las
// secciones reales del portfolio, lo que aparece al buscar es verdad. En la
// Fase B (CMS), cuando proyectos y certificados sean datos de la API, aqui
// se consultara el listado publico en vez de este array.
//
// --------------------------------------------------------------------------
// POR QUE NO HAY CACHE NI INDICE
//
// Esto corre en el servidor de nitro, en memoria y con una lista de seis
// entradas. Un indice invertido o un cache no tienen sentido a esta
// escala. Lo que SI importa, y por eso se explica:
//
//   1. El debounce lo pone el cliente (300ms), no aqui. El servidor no
//      sabe si la peticion viene de tecleo o de un prefetch.
//
//   2. Se corta la cadena a 10, porque el palette no necesita mas y una
//      respuesta enorme cuesta mas que diez resultados utilizables.
//
//   3. Se pondera cada entrada con score para que coincida con el ORDEN de
//      lo pedido, no con el orden del array.
//
// --------------------------------------------------------------------------
// POR QUE NO SE IMPORTA NADA DE 'h3'
//
// defineEventHandler y getQuery son autoimports de Nitro dentro de server/.
// Nitro trae h3 como dependencia suya, no del proyecto, asi que con el
// layout estricto de pnpm no se puede resolver 'h3' desde la raiz. Importarlo
// aqui resolveria en un build y no en otro. Los autoimports no dependen de
// eso.
interface SearchEntry {
  label: string
  suffix: string
  icon: string
  to: string
  /** Palabras sueltas para buscar. No es lo que se muestra: es lo que se
   *  compara con lo que el usuario escribe. */
  keywords: string[]
}

// El catalogo de secciones. En la Fase B (CMS) vendria de la API publica.
const catalog: SearchEntry[] = [
  {
    label: 'Inicio',
    suffix: 'Perfil, skills y experiencia',
    icon: 'i-lucide-house',
    to: '/',
    keywords: ['inicio', 'home', 'perfil', 'profile', 'sobre mi', 'skills', 'experiencia', 'cv']
  },
  {
    label: 'Proyectos',
    suffix: 'Listado y detalle por slug',
    icon: 'i-lucide-folder-kanban',
    to: '/proyectos',
    keywords: ['proyectos', 'projects', 'stack', 'repositorio', 'demo']
  },
  {
    label: 'Certificados',
    suffix: 'Certificados y cursos',
    icon: 'i-lucide-award',
    to: '/certificados',
    keywords: ['certificados', 'certificates', 'cursos', 'courses', 'emisor']
  },
  {
    label: 'Contacto',
    suffix: 'Formulario en la landing',
    icon: 'i-lucide-mail',
    to: '/#contacto',
    keywords: ['contacto', 'contact', 'email', 'mensaje']
  }
]

export default defineEventHandler((event) => {
  const query = getQuery(event)
  const raw = Array.isArray(query.q) ? query.q[0] : query.q
  const term = String(raw ?? '').trim().toLowerCase()

  // Sin texto no hay busqueda que hacer. Devolver [] en vez de todo el
  // catalogo importa: con el catalogo entero, el palette abriria con todo
  // listado antes de que el usuario escriba una letra.
  if (!term) {
    return []
  }

  const scored = catalog
    .map((entry) => {
      // El label pesa mas que las keywords: si el usuario escribe "modal",
      // el item que se llama "Modales" va delante de uno que solo lo
      // menciona de pasada. Sin pesos, "formulario" empata con tres
      // entradas y el orden es el del array.
      const inLabel = entry.label.toLowerCase().includes(term)
      const inKeywords = entry.keywords.some(keyword => keyword.includes(term))

      if (!inLabel && !inKeywords) {
        return null
      }

      return {
        score: (inLabel ? 2 : 0) + (inKeywords ? 1 : 0),
        entry
      }
    })
    .filter((item): item is { score: number, entry: SearchEntry } => item !== null)
    .sort((a, b) => b.score - a.score)
    .slice(0, 10)
    .map(({ entry }) => ({
      label: entry.label,
      suffix: entry.suffix,
      icon: entry.icon,
      to: entry.to
    }))

  return scored
})
