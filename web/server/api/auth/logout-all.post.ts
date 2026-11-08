// ======================================================================
// POST /api/auth/logout-all
//
// Revoca TODAS las sesiones del usuario en Spring, incluida la actual, y
// borra la cookie sellada de Nuxt (versión BFF de POST /auth/logout-all).
//
// A diferencia del resto de endpoints de sesiones, aquí sí se limpia la
// sesión local: si Spring revoca la sesión de este navegador pero la cookie
// siguiera viva, el middleware de /admin/** la daría por válida y cada
// petición protegida acabaría en 401 hasta que el usuario saliera a mano.
// Es exactamente el mismo motivo por el que lo hace /api/auth/logout.
// ======================================================================
export default defineEventHandler(async (event) => {
  const result = await springAuthenticatedRequest<ApiProblem | undefined>(
    event,
    '/auth/logout-all',
    { method: 'POST' }
  )

  await clearUserSession(event)

  if (result.status >= 400) {
    return sendApiProblem(event, result.status, result.data as unknown as ApiProblem)
  }

  return sendNoContent(event)
})
