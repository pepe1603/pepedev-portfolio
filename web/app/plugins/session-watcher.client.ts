// ======================================================================
// WATCHER DE SESIÓN (solo cliente)
//
// Detecta que la sesión murió sin que el usuario tenga que hacer nada, para
// avisarle antes de que escriba a medias un formulario que se va a perder.
// El resultado es el modal de SessionExpiredModal.vue.
//
// --------------------------------------------------------------------------
// QUÉ NO HACE: NO REFRESCA A CIEGAS
//
// El sondeo llama a GET /api/auth/session-status, que renueva el access
// token SOLO si está a menos de 30 s de caducar. Si todavía vale, la
// respuesta es una lectura de cookie: cero rotaciones de refresh token.
//
// Hacer un POST /auth/refresh en cada vuelta a la pestaña quemaría un
// refresh token por alt-tab (Spring lo rota y pone el anterior en
// denylist) y abriría una carrera entre dos pestañas que se enfocan a la
// vez, cuyo resultado es la expulsión injustificada de ambas.
//
// --------------------------------------------------------------------------
// POR QUÉ visibilitychange Y NO UN setInterval
//
// Un temporizador que despierta a la pestaña en reposo paga coste por
// nadie. El momento en el que la caducidad le importa al usuario es cuando
// vuelve, y ahí visibilitychange llega solo. El throttle es la otra mitad:
// sin él, alternar entre ventanas provocaría un sondeo por alt-tab.
//
// --------------------------------------------------------------------------
// POR QUÉ SE SINCRONIZA LA SESIÓN ANTES DE ABRIR EL MODAL
//
// Tras un 401 el servidor ya no tiene sesión, pero el estado del cliente
// (`loggedIn`) está cacheado y sigue en true. Sin sincronizarlo, el sondeo
// volvería a fallar cada vez que se regresa a la pestaña y el modal se
// reabriría justo después de cerrarlo. fetch() lo pone a cero y, de paso,
// hace que los sondeos siguientes ni se lancen.
//
// --------------------------------------------------------------------------
// Y POR QUÉ NO SE ENGANCHA AQUÍ EL 401 DE LAS PÁGINAS
//
// Un 401 dentro del panel dirá lo mismo, pero las páginas del panel aún no
// existen y hoy un 401 en /api/auth/** casi siempre significa algo normal
// (credenciales mal, código OTP erróneo, challenge caducado). Amarigar eso
// con un modal global introduciría falsos positivos. Cuando exista el
// cliente del panel, su flujo de errores llama a mark() y reutiliza este
// mismo modal.
// ======================================================================
import { toApiError } from '~/composables/useApi'

/** Mínimo entre sondeos de la misma pestaña. */
const PROBE_INTERVAL_MS = 60_000

export default defineNuxtPlugin(() => {
  const guard = useSessionGuard()
  const { loggedIn, fetch: fetchSession } = useUserSession()

  let lastProbeAt = 0

  async function probe() {
    if (!loggedIn.value) {
      return
    }

    const now = Date.now()
    if (now - lastProbeAt < PROBE_INTERVAL_MS) {
      return
    }
    lastProbeAt = now

    try {
      await $fetch('/api/auth/session-status')
    } catch (error) {
      const problem = toApiError(error).problem
      if (problem.status !== 401) {
        // Un 500 o una red caída no dicen que la sesión haya muerto: no se
        // avisa para no echar a un usuario que sigue dentro.
        return
      }
      await fetchSession()
      guard.mark(problem.detail)
    }
  }

  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'visible') {
      void probe()
    }
  })
})
