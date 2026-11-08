// ======================================================================
// useLogout
//
// Cierre de sesión en un sitio: revoca los tokens en Spring (vía el endpoint
// BFF /api/auth/logout), refresca el estado del composable para que
// `loggedIn` pase a false y vuelve al login. Lo usan el botón del sidebar del
// panel y la página 403.
// ======================================================================
import type { ApiError } from './useApi'

export function useLogout() {
  const auth = useAuthApi()
  const { fetch: fetchSession } = useUserSession()
  const { notify } = useAppNotify()

  async function logout(redirectTo = '/login') {
    try {
      await auth.logout()
      await fetchSession()
      notify('Sesión cerrada')
    } catch (error) {
      notify((error as ApiError).problem?.detail ?? 'No se pudo cerrar la sesión', 'error')
    }
    await navigateTo(redirectTo)
  }

  return { logout }
}
