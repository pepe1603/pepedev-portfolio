// ======================================================================
// OPCIONES DEL TOASTER
// El prop `toaster` de <UApp> NO es reactivo por si mismo: se lee una vez
// al montar. Para poder cambiar la posicion o el limite de toasts en
// caliente hace falta un estado compartido, y ese estado es lo que vive
// aqui.
//
// Vive en utils y no dentro de ToastSection.vue porque lo consumen dos
// sitios a la vez: app.vue lo pasa a <UApp> y la seccion de documentacion
// lo escribe. Si declaras el useState dentro de la seccion, las dos
// copias tendrian claves distintas y el selector de abajo no moveria
// nada.
//
// AVISO: los valores de abajo son los que Nuxt UI usa por defecto. estan
// escritos a mano para que la pagina pueda enumerarlos, no porque sean
// obligatorios. Borra el estado y el binding de app.vue si no vas a
// cambiar nada en caliente: <UApp> sin :toaster funciona igual.
// ======================================================================

export type ToasterPosition = 'top-left' | 'top-center' | 'top-right' | 'bottom-left' | 'bottom-center' | 'bottom-right'

export interface ToasterOptions {
  /** Esquina donde se apila la pila. De aqui sale tambien el sentido del
   *  gesto de desplazar para cerrar: en top-* se arrastra hacia arriba, en
   *  bottom-* hacia abajo, y en left/right hacia el borde. */
  position?: ToasterPosition
  /** Milisegundos por defecto antes de cerrarse. Cada toast lo puede
   *  pisar con su propio `duration`. */
  duration?: number
  /** Cuantos toasts caben a la vez. Al pasar de este numero el mas
   *  antiguo desaparece del estado sin animacion de salida. */
  max?: number
  /** false apila los toasts detras del ultimo, escalados. Al pasar el
   *  raton por encima se despliegan y se pausan los temporizadores. */
  expand?: boolean
  /** false quita la barra de tiempo restante de todos los toasts. */
  progress?: boolean
  /** Donde se monta el contenedor. Por defecto 'body'. */
  portal?: boolean | string
}

/** Los mismos numeros que los defaultProps de Toaster.vue. */
export const defaultToasterOptions: Required<Pick<ToasterOptions, 'position' | 'duration' | 'max' | 'expand' | 'progress' | 'portal'>> = {
  position: 'bottom-right',
  duration: 5000,
  max: 5,
  expand: true,
  progress: true,
  portal: true
}

/** Estado compartido. El nombre de la clave tiene que ser el mismo en
 *  todos los puntos que la usen, o habra dos estados distintos. */
export function useToasterOptions() {
  return useState<ToasterOptions>('toaster-options', () => ({ ...defaultToasterOptions }))
}
