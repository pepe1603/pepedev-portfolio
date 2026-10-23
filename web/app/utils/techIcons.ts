// ======================================================================
// ICONOS DE TECNOLOGÍA Y DE CATEGORÍA
//
// Dos mapas distintos que pintan iconos en sitios distintos:
//
//   1. techIcon(name)  -> icono de UNA tecnología concreta por su nombre.
//      Se usa en las tarjetas y fichas de proyecto (project.stack), que es
//      texto libre. La resolución es tolerante (nombre exacto, sin versión,
//      por partes de un nombre compuesto y por palabra completa); si nada
//      encaja, cae al icono genérico. El orden está explicado en techIcon.
//
//   2. categoryIcon(category) -> icono de una CATEGORÍA del stack (Backend,
//      Frontend...). Se usa en la sección de habilidades de la web pública,
//      que agrupa por el texto exacto de `category`.
//
// Vivían separados (el primero en composables/useTechIcons.ts, el segundo
// dentro de SkillsSection.vue). Se reúnen aquí para que la página de admin
// /admin/tecnologias/iconos pueda diagnosticar exactamente lo mismo que
// pinta la web: una copia en la página se desincronizaría a la primera de
// cambio. `useTechIcons` y `SkillsSection` siguen siendo los consumidores.
//
// Los nombres de icono salen de dos colecciones instaladas:
// @iconify-json/simple-icons (marcas) y @iconify-json/lucide (genéricos).
// ======================================================================

/**
 * Icono por nombre de tecnología (en minúsculas y sin espacios sobrantes).
 * Es una tabla de marcas: no pretende cubrir todo, solo lo que se usa. Alguna
 * entrada es un genérico a propósito (SQL, que es un estándar y no un producto).
 */
export const TECH_ICON_MAP: Record<string, string> = {
  'vue': 'i-simple-icons-vuedotjs',
  'vue.js': 'i-simple-icons-vuedotjs',
  'nuxt': 'i-simple-icons-nuxtdotjs',
  'nuxt.js': 'i-simple-icons-nuxtdotjs',
  'typescript': 'i-simple-icons-typescript',
  'javascript': 'i-simple-icons-javascript',
  'tailwind': 'i-simple-icons-tailwindcss',
  'tailwind css': 'i-simple-icons-tailwindcss',
  'tailwindcss': 'i-simple-icons-tailwindcss',
  'node': 'i-simple-icons-nodedotjs',
  'node.js': 'i-simple-icons-nodedotjs',
  'python': 'i-simple-icons-python',
  'docker': 'i-simple-icons-docker',
  'git': 'i-simple-icons-git',
  'github': 'i-simple-icons-github',
  'postman': 'i-simple-icons-postman',
  'figma': 'i-simple-icons-figma',
  'react': 'i-simple-icons-react',
  'next.js': 'i-simple-icons-nextdotjs',
  'nextjs': 'i-simple-icons-nextdotjs',
  'postgresql': 'i-simple-icons-postgresql',
  'postgres': 'i-simple-icons-postgresql',
  // SQL no tiene marca propia (es un lenguaje estándar, no un producto): se
  // pinta el icono genérico de base de datos de lucide.
  'sql': 'i-lucide-database',
  'mysql': 'i-simple-icons-mysql',
  'mongodb': 'i-simple-icons-mongodb',
  'aws': 'i-simple-icons-amazonaws',
  'vercel': 'i-simple-icons-vercel',
  'vite': 'i-simple-icons-vite',
  'pinia': 'i-simple-icons-pinia',
  'prisma': 'i-simple-icons-prisma',
  'html': 'i-simple-icons-html5',
  'css': 'i-simple-icons-css3',
  'sass': 'i-simple-icons-sass',
  'graphql': 'i-simple-icons-graphql',
  'redis': 'i-simple-icons-redis',
  'linux': 'i-simple-icons-linux',
  'go': 'i-simple-icons-go',
  'golang': 'i-simple-icons-go',
  'rust': 'i-simple-icons-rust',
  'java': 'i-simple-icons-openjdk',
  'php': 'i-simple-icons-php',
  'laravel': 'i-simple-icons-laravel',
  'django': 'i-simple-icons-django',
  'express': 'i-simple-icons-express',
  'nestjs': 'i-simple-icons-nestjs',
  'readme': 'i-simple-icons-readme',
  'angular': 'i-simple-icons-angular',
  'svelte': 'i-simple-icons-svelte',
  'astro': 'i-simple-icons-astro',
  'firebase': 'i-simple-icons-firebase',
  'supabase': 'i-simple-icons-supabase',
  'cloudflare': 'i-simple-icons-cloudflare',
  'nginx': 'i-simple-icons-nginx',
  'jenkins': 'i-simple-icons-jenkins',
  'stripe': 'i-simple-icons-stripe',
  'shopify': 'i-simple-icons-shopify',
  'wordpress': 'i-simple-icons-wordpress',
  'webflow': 'i-simple-icons-webflow',
  'framer': 'i-simple-icons-framer',
  'spring': 'i-simple-icons-spring',
  'spring boot': 'i-simple-icons-springboot',
  'springboot': 'i-simple-icons-springboot',
  'spring security': 'i-simple-icons-springsecurity'
}

/** Icono que se pinta cuando la tecnología no está en el mapa. */
export const TECH_ICON_FALLBACK = 'i-lucide-code-2'

/**
 * Cómo se resuelve el icono de una tecnología, en este orden:
 *
 *  1. NOMBRE EXACTO. Es lo que se hacía siempre y no cambia: "java" -> Java.
 *  2. SIN VERSIÓN Y POR PARTES. Se quitan los números de versión ("Vue 3",
 *     "Next.js 15", "HTML5") y se parte el nombre por los separadores con que
 *     la gente une dos tecnologías ("/", "|", ",", "+", "&", " y ", " and ").
 *     Así "Vue / Nuxt" resuelve por "Vue" y "Git / GitHub" por "Git". Gana la
 *     primera parte reconocida, en el orden en que se escribió.
 *  3. PALABRA COMPLETA MÁS LARGA. Busca dentro del nombre alguna clave del mapa
 *     que aparezca como palabra completa (nada de subcadenas) y se queda con la
 *     más larga, para que "Spring Boot" gane a "Spring" en "Spring Boot
 *     Framework". Así "React Native" resuelve por React y "MongoDB Atlas" por
 *     MongoDB.
 *
 * No se hace coincidencia por subcadena a propósito: "Mongo" acabaría en el
 * icono de Go y "JavaScript" en el de Java. Solo palabra completa. Si nada
 * encaja, se devuelve el genérico.
 */
export function techIcon(tech: string): string {
  const nombre = normalizarNombre(tech)
  if (!nombre) return TECH_ICON_FALLBACK

  // 1. Exacta: el caso de siempre, primero y sin tocar nada.
  const exacta = TECH_ICON_MAP[nombre]
  if (exacta) return exacta

  // 2. Sin versión y por partes (el nombre entero sin versión, y cada trozo).
  const variantes = [
    sinVersion(nombre),
    ...partesDeNombre(nombre).flatMap(parte => [parte, sinVersion(parte)])
  ]
  for (const variante of variantes) {
    const icon = TECH_ICON_MAP[variante]
    if (icon) return icon
  }

  // 3. Palabra completa más larga dentro del nombre.
  const porPalabra = porPalabraCompleta(nombre)
  if (porPalabra) return porPalabra

  return TECH_ICON_FALLBACK
}

/** Minúsculas, sin espacios en los extremos y con los intermedios colapsados. */
function normalizarNombre(tech: string): string {
  return tech.toLowerCase().trim().replace(/\s+/g, ' ')
}

/**
 * Quita números de versión, pegados o no: "Vue 3" -> "vue", "Next.js 15" ->
 * "next.js", "HTML5" -> "html", "Python 3.12" -> "python". Los números no
 * forman parte del nombre de la marca y sin esto "Next.js 15" no resolvería.
 */
function sinVersion(nombre: string): string {
  return nombre.replace(/\d+(?:\.\d+)*/g, '').replace(/\s+/g, ' ').trim()
}

/**
 * Trozos de un nombre compuesto. Los separadores son los que se usan de verdad
 * para unir dos tecnologías: "/", "|", ",", ";", "+", "&", " y " y " and ".
 * "C++" da ["c"] (el "+" es separador y el trozo vacío se descarta).
 */
function partesDeNombre(nombre: string): string[] {
  return nombre
    .split(/\s*(?:\/|\||,|;|\+|&|\by\b|\band\b)\s*/)
    .map(parte => parte.trim())
    .filter(Boolean)
}

/** Escapa un texto para poder meterlo literal dentro de una RegExp. */
function escaparRegExp(texto: string): string {
  return texto.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

/**
 * Claves del mapa preparadas para buscar como palabra completa, de la más larga
 * a la más corta (así la primera que encaje es la mejor). Se calculan una vez al
 * cargar el módulo: techIcon se llama varias veces por página y no tiene sentido
 * reconstruir ~60 expresiones regulares en cada llamada.
 *
 * Se descartan las claves de menos de 3 letras en esta capa: son demasiado
 * cortas para buscarlas dentro de otra palabra sin arriesgar falsos positivos
 * ("go" dentro de "Mongo", por ejemplo). Siguen funcionando por nombre exacto.
 */
const CLAVES_POR_PALABRA = Object.entries(TECH_ICON_MAP)
  .filter(([clave]) => clave.length >= 3)
  .sort(([a], [b]) => b.length - a.length)
  .map(([clave, icon]) => ({
    icon,
    patron: new RegExp(`(^|[^a-z0-9])${escaparRegExp(clave)}([^a-z0-9]|$)`)
  }))

function porPalabraCompleta(nombre: string): string | null {
  for (const { icon, patron } of CLAVES_POR_PALABRA) {
    if (patron.test(nombre)) return icon
  }
  return null
}

/** Icono que se pinta cuando la categoría no encaja con ninguna regla. */
export const CATEGORY_ICON_FALLBACK = 'i-lucide-code-2'

/**
 * Devuelve el icono de una categoría del stack. A diferencia del mapa de
 * tecnologías, aquí no hay tabla: son reglas por subcadena, porque las
 * categorías son texto libre ("Backend", "backend", "Base de datos"...).
 *
 * Las reglas son tolerantes al plural a propósito: la categoría real del
 * perfil es "Bases de datos", y una regla que buscara solo "base de datos"
 * no la cogería (fue justo el hueco que señaló /admin/tecnologias/iconos).
 */
export function categoryIcon(category: string): string {
  const value = category.toLowerCase()

  if (value.includes('backend') || value.includes('servidor')) {
    return 'i-lucide-server'
  }

  if (value.includes('base') || value.includes('database')) {
    return 'i-lucide-database'
  }

  if (value.includes('frontend') || value.includes('interfaz')) {
    return 'i-lucide-monitor'
  }

  if (value.includes('herramienta') || value.includes('tool')) {
    return 'i-lucide-wrench'
  }

  if (value.includes('devops') || value.includes('infraestructura')) {
    return 'i-lucide-container'
  }

  return CATEGORY_ICON_FALLBACK
}
