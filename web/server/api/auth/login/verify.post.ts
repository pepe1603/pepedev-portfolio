// ======================================================================
// POST /api/auth/login/verify
//
// Segundo paso del login cuando el OTP está activo. Recibe el challengeId
// que devolvió /api/auth/login y el código de 6 dígitos. Si Spring lo
// acepta (200), aquí sí se establece la sesión.
//
// Respuestas:
//   200 -> { user }
//   401 -> ProblemDetail (código incorrecto, caducado o intentos agotados)
// ======================================================================
import * as z from 'zod'
import type { TokenResponse } from '../../../utils/spring'

const verifySchema = z.object({
  challengeId: z.string('Falta el identificador del challenge').min(1, 'Falta el identificador del challenge'),
  code: z.string('Introduce el código').min(1, 'Introduce el código')
})

export default defineEventHandler(async (event) => {
  const parsed = verifySchema.safeParse(await readBody(event))
  if (!parsed.success) {
    return sendApiProblem(event, 400, buildProblem(400, 'Código de verificación inválido', {
      errors: parsed.error.issues.map(issue => ({
        field: issue.path.join('.') || null,
        message: issue.message
      }))
    }))
  }

  const result = await springVerifyOtp(event, parsed.data.challengeId, parsed.data.code)

  if (result.status === 200) {
    await establishUserSession(event, result.data as TokenResponse, result.refreshToken)
    const session = await getUserSession(event)
    return { user: session.user }
  }

  return forwardSpringError(event, result)
})
