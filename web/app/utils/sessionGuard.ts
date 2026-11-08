// ======================================================================
// ESTADO DEL GUARDIÁN DE SESIÓN (modal de "sesión caducada")
//
// Vive aquí y no dentro de SessionExpiredModal porque lo consumen dos
// sitios a la vez: el propio modal (que lo pinta y lo cierra) y el watcher
// de sesión (plugins/session-watcher.client.ts, que lo abre cuando el
// servidor dice que la sesión ya no puede renovarse). Es el mismo motivo
// que useCommandPalette y AppCommandPalette.
//
// POR QUÉ useState Y NO UN ref
//
// useState serializa su valor al HTML y lo rehidrata en el cliente. Un ref
// normal crearía dos estados (uno por entorno) y el HTML no coincidiría
// con lo que Vue espera al hidratar.
//
// QUIÉN LO LLAMA
//
// mark() solo lo debe invocar quien recibe un 401 del BFF, que es la única
// señal autoritativa de que la sesión murió. Aquí no se decide nada: este
// archivo es estado, no política.
// ======================================================================
import type { Ref } from 'vue'

export interface SessionGuard {
  /** true mientras el modal debe estar visible. */
  open: Ref<boolean>
  /** Motivo concreto que dio el servidor, si lo dio. */
  detail: Ref<string | undefined>
  /** Abre el modal, con el motivo del servidor. */
  mark: (detail?: string) => void
  /** Cierra el modal y olvida el motivo. */
  close: () => void
}

export function useSessionGuard(): SessionGuard {
  const open = useState<boolean>('session-guard:open', () => false)
  const detail = useState<string | undefined>('session-guard:detail', () => undefined)

  return {
    open,
    detail,
    mark: (message) => {
      detail.value = message
      open.value = true
    },
    close: () => {
      open.value = false
      detail.value = undefined
    }
  }
}
