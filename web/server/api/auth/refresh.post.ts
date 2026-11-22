// ======================================================================
// POST /api/auth/refresh
//
// Renueva el access token usando el refresh token guardado en la sesión
// sellada. Normalmente lo llama la app de forma transparente (p. ej. al
// volver tras un rato), pero tenerlo expuesto permite forzar la renovación
// desde el cliente si hiciera falta.
//
// Respuestas:
//   200 -> { user }  (rotación de tokens; la cookie sellada se actualiza)
//   401 -> ProblemDetail (refresh caducado o revocado; se limpia la sesión)
// ======================================================================
import type { TokenResponse } from '../../utils/spring'

export default defineEventHandler(async (event) => {
  const session = await getUserSession(event)
  const refreshToken = session.secure?.refreshToken

  if (!refreshToken) {
    return sendApiProblem(event, 401, buildProblem(401, 'No hay ninguna sesión que renovar'))
  }

  const result = await springRefresh(event, refreshToken)

  if (result.status === 200) {
    await establishUserSession(event, result.data as TokenResponse, result.refreshToken ?? refreshToken)
    const updated = await getUserSession(event)
    return { user: updated.user }
  }

  // El refresh ya no vale: no tiene sentido conservar una sesión muerta.
  await clearUserSession(event)
  return forwardSpringError(event, result)
})
