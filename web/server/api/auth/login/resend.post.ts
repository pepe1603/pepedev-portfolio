// ======================================================================
// POST /api/auth/login/resend
//
// Reenvía el código OTP de un challenge vivo. No hace falta el código: el
// challengeId sigue siendo el mismo (Spring genera un código nuevo).
//
// Respuestas:
//   202 -> correo reencolado (no garantiza entrega)
//   401 -> challenge inválido o caducado; hay que repetir el login
//   429 -> reenviado hace poco; Retry-After con los segundos de espera
// ======================================================================
import * as z from 'zod'

const resendSchema = z.object({
  challengeId: z.string('Falta el identificador del challenge').min(1, 'Falta el identificador del challenge')
})

export default defineEventHandler(async (event) => {
  const parsed = resendSchema.safeParse(await readBody(event))
  if (!parsed.success) {
    return sendApiProblem(event, 400, buildProblem(400, 'Falta el identificador del challenge'))
  }

  const result = await springResendOtp(event, parsed.data.challengeId)

  if (result.status === 202) {
    setResponseStatus(event, 202)
    return { ok: true }
  }

  return forwardSpringError(event, result)
})
