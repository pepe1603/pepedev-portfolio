// ======================================================================
// IDIOMA DE LA API
//
// El contrato de la API trabaja EXCLUSIVAMENTE con las claves 'es' y 'en'
// (docs/API.md); cualquier otro valor no tiene significado alla arriba. El
// tipo lo refleja para que TS impida pasarle otra cosa.
//
// Vive en un useState y no en un ref suelto por el mismo motivo que el
// command palette: el valor viaja serializado al HTML en el SSR, y un ref
// normal crearia dos estados distintos (servidor y cliente) con un markup
// que no coincide. Ademas, al ser estado compartido, cambiar el idioma en
// la cabecera re-lanza el useAsyncData de las paginas cuya key incluya lang.
//
// Solo cubre el CONTENIDO que viene de la API. La interfaz (etiquetas de la
// navegación, toasts) sigue en español hasta la Fase 4 del roadmap, que es
// donde entra la traduccion completa ES/EN.
// ======================================================================

export type Lang = 'es' | 'en'

export function useLang(): Ref<Lang> {
  return useState<Lang>('lang', () => 'es')
}
