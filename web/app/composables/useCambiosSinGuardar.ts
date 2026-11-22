// ======================================================================
// CAMBIOS SIN GUARDAR
//
// Los formularios del panel editan varias secciones (perfil, proyectos,
// certificados...) y en todas conviene ver, antes de pulsar Guardar, QUÉ se
// va a cambiar: son PUT que reemplazan la entidad entera, así que un guardado
// a ciegas puede pisar más de lo que se cree. Este composable centraliza esa
// cuenta para que las tres secciones se comporten igual y no se desincronicen.
//
// La idea: por cada campo editable se define una etiqueta legible y un par de
// normalizadores —cómo está AHORA en el formulario y cómo está GUARDADO— que
// devuelven ambos una cadena comparable. El campo está cambiado si las dos
// cadenas difieren. Normalizar es lo que hace que la comparación sea justa:
// "  hola " y "hola" son el mismo valor guardado (el PUT haría trim), y una
// cadena vacía y null también (el PUT lo guarda como null, no como "").
//
// No lleva Pinia ni estado global: es estado local al formulario que lo usa,
// igual que el resto del panel (el global ya va por useState de Nuxt).
// ======================================================================

/** Un campo vigilado: su etiqueta legible y los dos normalizadores. */
export interface CampoVigilado {
  /** Cómo se llama el campo en lenguaje de persona ("nombre completo", "slug"). */
  etiqueta: string
  /** Forma normalizada del valor que hay ahora en el formulario. */
  actual: () => string
  /** Forma normalizada del mismo campo tal como está guardado. */
  guardado: () => string
}

export interface CambiosSinGuardar {
  /** Claves de los campos que van a cambiar si se guarda ahora. Vacío si no hay nada. */
  camposCambiados: ComputedRef<string[]>
  /** true si hay al menos un campo cambiado. Con esto se habilita Guardar. */
  hayCambios: ComputedRef<boolean>
  /** true si ese campo concreto cambiará; para resaltarlo en el formulario. */
  campoCambiado: (campo: string) => boolean
  /**
   * Clase de resalte para una tarjeta si alguno de sus campos cambió. Se usa
   * pasando las claves de los campos que viven en esa tarjeta.
   */
  claseCambio: (...campos: string[]) => string
  /** "3 cambios sin guardar" / "1 cambio sin guardar" / "Sin cambios". */
  textoCambios: ComputedRef<string>
  /** Lista legible ("nombre completo, web") para enseñar qué cambia. */
  listaCambios: ComputedRef<string>
}

/**
 * @param campos Mapa de clave de campo → campo vigilado. El orden del mapa es
 *   el orden en que se listan los cambios.
 * @param hayOriginal Devuelve false mientras la entidad no haya llegado (por
 *   ejemplo, el perfil aún cargando): sin original no hay nada con que comparar
 *   y se prefiere decir "sin cambios" antes que marcar todo como cambiado.
 */
export function useCambiosSinGuardar(
  campos: Record<string, CampoVigilado>,
  hayOriginal: () => boolean
): CambiosSinGuardar {
  const claves = Object.keys(campos)

  const camposCambiados = computed(() =>
    hayOriginal() ? claves.filter(campo => campos[campo]!.actual() !== campos[campo]!.guardado()) : []
  )

  const hayCambios = computed(() => camposCambiados.value.length > 0)

  function campoCambiado(campo: string): boolean {
    return camposCambiados.value.includes(campo)
  }

  function claseCambio(...seleccion: string[]): string {
    return seleccion.some(campoCambiado) ? 'ring-2 ring-warning' : ''
  }

  const textoCambios = computed(() => {
    const n = camposCambiados.value.length
    if (n === 0) return 'Sin cambios'
    return n === 1 ? '1 cambio sin guardar' : `${n} cambios sin guardar`
  })

  const listaCambios = computed(() =>
    camposCambiados.value.map(campo => campos[campo]!.etiqueta).join(', ')
  )

  return { camposCambiados, hayCambios, campoCambiado, claseCambio, textoCambios, listaCambios }
}

/**
 * Normaliza un valor del formulario a la MISMA forma comparable en ambos lados:
 * `null` y `''` son lo mismo (el PUT guarda vacío como null), y se conserva el
 * JSON para los objetos/arrays (texto bilingüe, galería, stack) de modo que el
 * orden de sus claves no afecte.
 */
export function normalizarValor(value: unknown): string {
  if (value === null || value === undefined) return 'null'
  if (typeof value === 'string') {
    const trimmed = value.trim()
    return trimmed === '' ? 'null' : JSON.stringify(trimmed)
  }
  return JSON.stringify(value)
}
