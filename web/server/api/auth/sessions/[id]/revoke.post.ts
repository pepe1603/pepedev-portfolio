// ======================================================================
// POST /api/auth/sessions/[id]/revoke
//
// Revoca una sesión concreta por su id (versión BFF de
// POST /auth/sessions/{sessionId}/revoke). Spring responde 204 sin cuerpo,
// también cuando el id no existe o ya estaba revocado (idempotente): desde
// el panel, revocar algo que ya no está no es un error.
// ======================================================================
export default defineEventHandler(async (event) => {
  const id = getRouterParam(event, 'id')
  if (!id) {
    return sendApiProblem(event, 400, buildProblem(400, 'Falta el id de la sesión'))
  }

  const result = await springAuthenticatedRequest<ApiProblem | undefined>(
    event,
    `/auth/sessions/${encodeURIComponent(id)}/revoke`,
    { method: 'POST' }
  )

  setResponseStatus(event, result.status)

  if (result.status >= 400) {
    return sendApiProblem(event, result.status, result.data as unknown as ApiProblem)
  }

  return sendNoContent(event)
})
