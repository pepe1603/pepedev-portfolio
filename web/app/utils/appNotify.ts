// ======================================================================
// useAppNotify
// Un atajo para lanzar un aviso sin escribir el objeto entero cada vez.
//
//   const { notify } = useAppNotify()
//   notify('Guardado')
//   notify('No se pudo guardar', 'error')
//
// Que hace y por que existe:
//
//   1. El 90% de los avisos son "algo paso" o "algo fallo", con un color y
//      nada mas. Escribir el objeto completo cada vez (title, icon, color)
//      es ruido que esconde lo importante: que la llamada necesita un
//      icono, y el icono es lo que hace que un aviso se lea de reojo.
//
//   2. El icono se deduce del color. Sin el, la mitad de los avisos de
//      este proyecto salen con un icono generico que no dice nada.
//
//   3. `success` y `error` son los dos casos que se usan en todas partes.
//      Por eso son los shortcuts. `warning` y `primary` tambien se
//      exponen, pero con menosappeal: el texto suele bastar.
//
// --------------------------------------------------------------------------
// LO QUE ESTE COMPOSABLE NO HACE
//
// No envuelve los toasts en nada propio: llama a useToast() de Nuxt UI
// directamente. Si este archivo guardara el estado, habria dos colas
// distintas y habria que elegir una; al delegar, el toast lanzado desde
// aqui y el lanzado desde un componente se ven igual y se ordenan juntos
// en la misma cola.
//
// Ojo con el nombre: `notify` es el verbo de Nuxt UI v2, que era donde
// `notify()` y `<UNotifications>` iban juntos. En v4 el componente se
// llama Toaster y el composable useToast. Este helper mantiene el nombre
// corto porque se escribe mucho, pero por dentro es useToast.
//
// La cola es un useState compartido, no un ref del componente que llama:
// por eso un aviso lanzado desde un composable, un middleware o un plugin
// se ve igual que uno lanzado desde un boton, y sobrevive a la
// navegacion.
import type { Toast } from '@nuxt/ui/composables/useToast'

// Los colores de UAlert y de useToast son los mismos: primary, secondary,
// success, info, warning, error y neutral. Ver .nuxt/ui/alert.ts, que es
// donde Nuxt UI genera el tema y donde estan escritos.
//
// `secondary` e `info` no estan en la lista de shortcuts aunque sean
// validos: se alcanzan pasando el color a mano. Anadir un atajo por cada
// color que UAlert admite convierte el helper en una copia de la prop.
type NotifyColor = 'primary' | 'secondary' | 'success' | 'info' | 'warning' | 'error' | 'neutral'

const icons = {
  primary: 'i-lucide-info',
  secondary: 'i-lucide-info',
  success: 'i-lucide-circle-check',
  info: 'i-lucide-info',
  warning: 'i-lucide-triangle-alert',
  error: 'i-lucide-circle-x',
  neutral: 'i-lucide-info'
} as const satisfies Record<NotifyColor, string>

// `success` primero y `error` segundo a proposito: son los dos atajos que
// se usan en cada pantalla. El resto, en orden de urgencia.
export function useAppNotify() {
  const toast = useToast()

  /**
   * Lanza un aviso.
   *
   * @param message Lo que se lee. Sale como `title` del toast.
   * @param color   Color del aviso. Sin icono manual, se deduce de aqui.
   *
   * Devuelve lo que devuelve `toast.add`, que es el toast ya creado con su
   * id: util si hay que actualizarlo despues con `toast.update`.
   *
   * OJO con el `satisfies`. El tipo `Toast` es el de la SALIDA, con id y con
   * todos los callbacks (`onEscapeKeyDown`, `onPause`, `onResume`...) que
   * rellena el propio `add`. La ENTRADA es `Partial<Toast>`: ahi nadie esta
   * obligado a nada, y con `satisfies Toast` el codigo pedia a mano campos
   * que el llamante no tiene por que saber.
   *
   * El `satisfies` se queda igualmente: sirve para comprobar que `color` es
   * un color de toast y que `icon` es un nombre de icono valido, que es lo
   * que interesa. Solo cambia el tipo contra el que se comprueba.
   */
  function notify(message: string, color: NotifyColor = 'primary') {
    return toast.add({
      title: message,
      color,
      icon: icons[color]
    } satisfies Partial<Toast>)
  }

  return { notify }
}
