// ======================================================================
// POST /api/auth/reset/request
//
// Solicita el correo de recuperación. Spring responde 202 SIEMPRE (exista o
// no la cuenta y aunque el correo no se pueda entregar) para no filtrar qué
// emails están registrados; el frontend muestra el mismo mensaje en todos
// los casos.
//
// Respuestas:
//   202 -> solicitud aceptada
//   400 -> email inválido
//   429 -> demasiadas solicitudes (Retry-After en segundos)
// ======================================================================
import * as z from 'zod'

const schema = z.object({
  email: z.email('Introduce un email válido')
})

export default defineEventHandler(async (event) => {
  const parsed = schema.safeParse(await readBody(event))
  if (!parsed.success) {
    return sendApiProblem(event, 400, buildProblem(400, 'Revisa el email introducido', {
      errors: parsed.error.issues.map(issue => ({
        field: issue.path.join('.') || null,
        message: issue.message
      }))
    }))
  }

  const result = await springRequestPasswordReset(event, parsed.data.email)

  if (result.status === 202) {
    setResponseStatus(event, 202)
    return { ok: true }
  }

  return forwardSpringError(event, result)
})
