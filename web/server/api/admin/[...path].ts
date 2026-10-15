// ======================================================================
// PROXY AUTENTICADO A /admin/** DE SPRING
//
// El panel no llama a Spring directamente: pide a /api/admin/<lo-que-sea> y
// este handler reenvía a /admin/<lo-que-sea> con el Bearer de la sesión
// sellada (ver server/utils/auth.ts). Así el JWT nunca llega al navegador.
//
// Cubre todos los endpoints de admin (projects, certificates, messages,
// profile, storage, otp...) sin replicarlos uno a uno. El rol se comprueba
// aquí además de en el middleware de páginas: aunque alguien llame al proxy a
// mano, sin rol ADMIN no pasa (403), y Spring lo volvería a rechazar.
//
// Métodos y cuerpo se reenvían tal cual (incluido multipart), y el status y
// las cabeceras relevantes (Retry-After) vuelven al cliente sin tocar.
// ======================================================================
export default defineEventHandler(async (event) => {
  const session = await getUserSession(event)

  if (!session.user) {
    return sendApiProblem(event, 401, buildProblem(401, 'No autenticado'))
  }
  if (session.user.role !== 'ADMIN') {
    return sendApiProblem(event, 403, buildProblem(403, 'No tienes permisos de administración'))
  }

  const path = getRouterParam(event, 'path') ?? ''
  const headers: Record<string, string> = {}
  for (const name of ['content-type', 'accept', 'accept-language']) {
    const value = getRequestHeader(event, name)
    if (value) {
      headers[name] = value
    }
  }

  const result = await springAuthenticatedRequest(event, `/admin/${path}`, {
    method: getMethod(event),
    query: getQuery(event),
    body: await readRequestBody(event),
    headers
  })

  setResponseStatus(event, result.status)

  if (result.retryAfter) {
    setResponseHeader(event, 'retry-after', result.retryAfter)
  }

  if (result.status === 204) {
    return sendNoContent(event)
  }

  return result.data
})
