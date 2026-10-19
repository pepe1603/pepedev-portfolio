// composables/useTechIcons.ts
//
// Composable de conveniencia para las páginas públicas de proyectos: expone
// getTechIcon(tech), que devuelve el icono de una tecnología del stack.
//
// El mapa y la normalización viven en utils/techIcons.ts (compartidos con la
// sección de habilidades y con el diagnóstico de /admin/tecnologias/iconos).
// Aquí solo se envuelve para no romper a quien ya llamaba useTechIcons().
import { techIcon } from '~/utils/techIcons'

export function useTechIcons() {
  return { getTechIcon: techIcon }
}
