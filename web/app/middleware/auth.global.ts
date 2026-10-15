// ======================================================================
// MIDDLEWARE GLOBAL DE ACCESO AL PANEL
//
// Protege /admin/** con dos puertas distintas:
//   - 401 (sin sesión)  -> redirige a /login guardando el destino.
//   - 403 (rol sin permiso) -> redirige a /403, que es otra cosa: aquí sí
//     hay sesión, pero no es de administración.
//
// El registro es `auth.global`, así que cubre cualquier página futura bajo
// /admin sin tener que añadir definePageMeta({ middleware }) en cada una.
// No toca las rutas públicas ni el layout público.
// ======================================================================
export default defineNuxtRouteMiddleware(async (to) => {
  const isAdminArea = to.path === '/admin' || to.path.startsWith('/admin/')
  const isLogin = to.path === '/login'

  if (!isAdminArea && !isLogin) {
    return
  }

  const session = useUserSession()

  // El composable carga la sesión una vez al arrancar. Si aún no está lista
  // (primer render en cliente), se espera aquí para no redirigir por error.
  if (!session.ready.value) {
    await session.fetch()
  }

  if (isAdminArea) {
    if (!session.loggedIn.value) {
      return navigateTo({ path: '/login', query: { redirect: to.fullPath } })
    }
    if (session.user.value?.role !== 'ADMIN') {
      return navigateTo('/403')
    }
  }

  // Un usuario ya autenticado no necesita ver el formulario de login.
  if (isLogin && session.loggedIn.value) {
    return navigateTo('/admin')
  }
})
