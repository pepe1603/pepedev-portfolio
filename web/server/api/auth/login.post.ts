// ======================================================================
// POST /api/auth/login
//
// Intermediario BFF: valida el cuerpo, llama a Spring y, si el login es
// correcto, sella la sesión (access + refresh token) en la cookie httpOnly
// de Nuxt. El navegador nunca ve el JWT.
//
// Respuestas:
//   200 -> { user }                    sesión iniciada
//   202 -> { challengeId }             OTP activo; hay que ir a /verificar
//   4xx -> ProblemDetail (401 credenciales, 429 rate limit + Retry-After...)
// ======================================================================
import * as z from 'zod'
import type { TokenResponse } from '../../utils/spring'

const loginSchema = z.object({
  email: z.email('Introduce un email válido'),
  password: z.string('La contraseña es obligatoria').min(1, 'La contraseña es obligatoria')
})

export default defineEventHandler(async (event) => {
  const parsed = loginSchema.safeParse(await readBody(event))
  if (!parsed.success) {
    return sendApiProblem(event, 400, buildProblem(400, 'Datos de login inválidos', {
      errors: parsed.error.issues.map(issue => ({
        field: issue.path.join('.') || null,
        message: issue.message
      }))
    }))
  }

  const { email, password } = parsed.data
  const result = await springLogin(event, email, password)

  if (result.status === 200) {
    await establishUserSession(event, result.data as TokenResponse, result.refreshToken)
    const session = await getUserSession(event)
    return { user: session.user }
  }

  // 202: el segundo factor está activo. Se devuelve el challengeId para que
  // el cliente continúe en la verificación; todavía no hay sesión.
  if (result.status === 202) {
    setResponseStatus(event, 202)
    return result.data
  }

  return forwardSpringError(event, result)
})
