// ======================================================================
// GET /api/auth/sessions
//
// Sesiones activas del usuario (versión BFF de GET /auth/sessions de
// Spring). Reenvía la lista tal cual, incluido el flag `current` que marca
// cuál es la sesión de este navegador.
//
// Se llama con springAuthenticatedRequest y no con springRequest a secas
// porque este endpoint es de /auth/** pero NO es anónimo: Spring exige el
// Bearer, y de paso la llamada renueva el access token si está caducado.
// ======================================================================
import type { AuthSession } from '#shared/types/auth'

export default defineEventHandler(async (event) => {
  const result = await springAuthenticatedRequest<AuthSession[]>(event, '/auth/sessions')

  setResponseStatus(event, result.status)

  if (result.status === 204) {
    return sendNoContent(event)
  }

  if (result.status >= 400) {
    return sendApiProblem(event, result.status, result.data as unknown as ApiProblem)
  }

  return result.data
})
