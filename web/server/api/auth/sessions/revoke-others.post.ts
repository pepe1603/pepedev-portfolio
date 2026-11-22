// ======================================================================
// POST /api/auth/sessions/revoke-others
//
// Revoca todas las sesiones del usuario menos la actual (versión BFF de
// POST /auth/sessions/revoke-others). Spring responde 204 sin cuerpo.
// ======================================================================
export default defineEventHandler(async (event) => {
  const result = await springAuthenticatedRequest<ApiProblem | undefined>(
    event,
    '/auth/sessions/revoke-others',
    { method: 'POST' }
  )

  setResponseStatus(event, result.status)

  if (result.status >= 400) {
    return sendApiProblem(event, result.status, result.data as unknown as ApiProblem)
  }

  return sendNoContent(event)
})
