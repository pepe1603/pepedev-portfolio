// ======================================================================
// GET /api/search?q=...
// Endpoint de ejemplo para la busqueda asincrona del command palette.
//
// --------------------------------------------------------------------------
// POR QUE ESTE ARCHIVO DEVUELVE DATOS QUE SON DEL PROPIO TEMPLATE
//
// Una busqueda asincrona tiene que responder a ALGO. Hardcodear un
// directorio de productos inventado seria mas realista, pero entonces el
// endpoint mentiria: buscaria cosas que no existen en esta pagina. Con las
// paginas reales del template, lo que aparece al buscar es verdad, y el
// paleta se puede probar de verdad.
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

// El catalogo. En un proyecto de verdad vendria de la base de datos, y este
// array seria una consulta.
const catalog: SearchEntry[] = [
  {
    label: 'Texto',
    suffix: 'Escala de emphasis y tokens',
    icon: 'i-lucide-type',
    to: '/#texto',
    keywords: ['tipografia', 'typography', 'tokens', 'fuente', 'color']
  },
  {
    label: 'Superficies',
    suffix: 'Fondos, bordes y sombras',
    icon: 'i-lucide-layers',
    to: '/#superficies',
    keywords: ['surface', 'fondo', 'border', 'sombra', 'shadow']
  },
  {
    label: 'Componentes',
    suffix: 'Botones, badges, inputs',
    icon: 'i-lucide-blocks',
    to: '/#componentes',
    keywords: ['button', 'badge', 'input', 'formulario']
  },
  {
    label: 'Animaciones',
    suffix: 'Entradas y secuencias',
    icon: 'i-lucide-clapperboard',
    to: '/#animaciones',
    keywords: ['motion', 'animacion', 'entrada', 'sequence']
  },
  {
    label: 'Imágenes',
    suffix: 'ipx, formatos y srcset',
    icon: 'i-lucide-image',
    to: '/#imagenes',
    keywords: ['image', 'img', 'ipx', 'avif', 'webp', 'foto']
  },
  {
    label: 'Avisos',
    suffix: 'useToast y la cola global',
    icon: 'i-lucide-bell',
    to: '/#avisos',
    keywords: ['toast', 'alert', 'notificacion', 'notify']
  },
  {
    label: 'Modales',
    suffix: 'AppModal sobre UModal',
    icon: 'i-lucide-maximize-2',
    to: '/#modales',
    keywords: ['modal', 'dialogo', 'overlay']
  },
  {
    label: 'Cajones',
    suffix: 'AppDrawer sobre UDrawer',
    icon: 'i-lucide-panel-right',
    to: '/#cajones',
    keywords: ['drawer', 'cajon', 'panel', 'lateral']
  },
  {
    label: 'Tooltips',
    suffix: 'Retardo, lado y contenido',
    icon: 'i-lucide-message-square',
    to: '/#tooltips',
    keywords: ['tooltip', 'hover', 'globo']
  },
  {
    label: 'Formulario',
    suffix: 'UForm con validación de Zod',
    icon: 'i-lucide-text-cursor-input',
    to: '/formulario',
    keywords: ['form', 'formulario', 'zod', 'validacion', 'schema', 'input']
  },
  {
    label: 'Carrusel',
    suffix: 'Tarjetas visibles por breakpoint',
    icon: 'i-lucide-gallery-horizontal-end',
    to: '/carrusel',
    keywords: ['carousel', 'carrusel', 'slide', 'embla']
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
