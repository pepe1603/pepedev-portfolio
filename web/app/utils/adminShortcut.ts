// ======================================================================
// ATAJO DE ACCESO AL PANEL
//
// Atajo oculto para llegar al panel desde el portfolio público:
// Ctrl+Shift+U en Windows/Linux y ⌘+Shift+U en Mac (la "U" de Usuario).
//
// No hay ningún enlace visible hacia /admin en la web pública: esta es la
// vía de acceso. Navega a /admin y deja que el middleware global decida:
// si hay sesión de ADMIN entra al panel, si no, redirige a /login con el
// destino guardado (ver app/middleware/auth.global.ts).
//
// --------------------------------------------------------------------------
// POR QUE NO Ctrl+Shift+A, QUE ES LO QUE SUGERIA EL ROADMAP
//
// En Chrome, Ctrl+Shift+A abre la búsqueda de pestañas: el navegador se
// come la combinación antes de que llegue a la página, así que el atajo
// nunca se dispararía. Ctrl+Shift+U no tiene uso asignado en los
// navegadores habituales.
//
// Ctrl+Shift+L (tema) y Ctrl+Shift+C (copiar URL) ya están cogidas por
// AppCommandPalette; por eso este atajo no las reutiliza.
//
// --------------------------------------------------------------------------
// POR QUE VA EN EL LAYOUT PUBLICO Y NO EN TODA LA APP
//
// El atajo tiene sentido estando en el portfolio público, que es donde el
// administrador está y desde donde no tiene ningún botón que lo lleve al
// panel. Dentro del panel (layout dashboard) no se monta: allí ya se está.
//
// --------------------------------------------------------------------------
// POR QUE REUTILIZA EL PATRON DE commandPalette.ts
//
// useMagicKeys no acepta handlers en su objeto de opciones en @vueuse/core
// v14 (solo reactive, target, aliasMap, passive y onEventFired); devuelve
// refs por combinación. Se reacciona con un watch sobre el ref y se filtra
// por el `if (pressed)` de dentro. Todo el detalle está documentado en
// app/utils/commandPalette.ts.
//
// `passive: false` es lo único que permite llamar a preventDefault, que
// aquí importa: sin él, Ctrl+Shift+U podría activar algún atajo del propio
// navegador mientras el evento sigue su curso.
// ======================================================================
export function useAdminShortcut() {
  const magic = useMagicKeys({ passive: false })

  const openAdmin = () => {
    // Navega a /admin; el middleware global resuelve sesión o login.
    // navigateTo se importa implícitamente (auto-import de Nuxt).
    void navigateTo('/admin')
  }

  // El tsconfig activa noUncheckedIndexedAccess y el objeto de useMagicKeys
  // es un Record<string, ComputedRef<boolean>>, así que la clave puede ser
  // undefined pese a escribirse como propiedad. Mismo guardia que en
  // commandPalette.ts.
  function watchKey(source: ComputedRef<boolean> | undefined) {
    if (!source) {
      return
    }

    watch(source, (pressed) => {
      if (pressed) {
        openAdmin()
      }
    })
  }

  watchKey(magic.meta_shift_u)
  watchKey(magic.ctrl_shift_u)
}
