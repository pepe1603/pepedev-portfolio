// ======================================================================
// ICONOS DE TECNOLOGÍA Y DE CATEGORÍA
//
// Dos mapas distintos que pintan iconos en sitios distintos:
//
//   1. techIcon(name)  -> icono de UNA tecnología concreta por su nombre.
//      Se usa en las tarjetas y fichas de proyecto (project.stack), que es
//      texto libre. Lo que no está en el mapa cae al icono genérico.
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
 * Es una tabla de marcas: no pretende cubrir todo, solo lo que se usa.
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
  'node': 'i-simple-icons-nodedotjs',
  'node.js': 'i-simple-icons-nodedotjs',
  'python': 'i-simple-icons-python',
  'docker': 'i-simple-icons-docker',
  'git': 'i-simple-icons-git',
  'github': 'i-simple-icons-github',
  'figma': 'i-simple-icons-figma',
  'react': 'i-simple-icons-react',
  'next.js': 'i-simple-icons-nextdotjs',
  'nextjs': 'i-simple-icons-nextdotjs',
  'postgresql': 'i-simple-icons-postgresql',
  'postgres': 'i-simple-icons-postgresql',
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
 * Devuelve el icono de una tecnología por su nombre, o el genérico si no
 * hay coincidencia. La normalización (minúsculas + trim) es la misma que
 * usa la web al pintar el stack de un proyecto.
 */
export function techIcon(tech: string): string {
  return TECH_ICON_MAP[tech.toLowerCase().trim()] || TECH_ICON_FALLBACK
}

/** Icono que se pinta cuando la categoría no encaja con ninguna regla. */
export const CATEGORY_ICON_FALLBACK = 'i-lucide-code-2'

/**
 * Devuelve el icono de una categoría del stack. A diferencia del mapa de
 * tecnologías, aquí no hay tabla: son reglas por subcadena, porque las
 * categorías son texto libre ("Backend", "backend", "Base de datos"...).
 */
export function categoryIcon(category: string): string {
  const value = category.toLowerCase()

  if (value.includes('backend') || value.includes('servidor')) {
    return 'i-lucide-server'
  }

  if (value.includes('base de datos') || value.includes('database')) {
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
