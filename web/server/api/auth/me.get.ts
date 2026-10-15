// ======================================================================
// GET /api/auth/me
//
// Datos del usuario de la sesión actual (email y rol). Es la versión BFF de
// GET /auth/me de Spring: no hace falta llamar a Spring porque los claims ya
// están en la cookie sellada. Útil para comprobaciones puntuales del cliente.
// ======================================================================
export default defineEventHandler(async (event) => {
  const session = await getUserSession(event)

  if (!session.user) {
    return sendApiProblem(event, 401, buildProblem(401, 'No autenticado'))
  }

  return session.user
})
