// ======================================================================
// POST /api/auth/reset/confirm
//
// Fija la nueva contraseña con el token de un solo uso del enlace. El enlace
// que manda Spring solo lleva `token`, así que el email y la contraseña los
// aporta el formulario. Al confirmar, Spring invalida todos los tokens y
// sesiones previos: tras esto hay que volver a iniciar sesión.
//
// Respuestas:
//   204 -> contraseña actualizada
//   400 -> email, token o contraseña inválidos
//   401 -> token caducado o ya usado
// ======================================================================
import * as z from 'zod'

const schema = z.object({
  email: z.email('Introduce un email válido'),
  token: z.string('Falta el token del enlace').min(1, 'Falta el token del enlace'),
  password: z.string('La contraseña es obligatoria').min(8, 'La contraseña debe tener al menos 8 caracteres')
})

export default defineEventHandler(async (event) => {
  const parsed = schema.safeParse(await readBody(event))
  if (!parsed.success) {
    return sendApiProblem(event, 400, buildProblem(400, 'Revisa los datos del formulario', {
      errors: parsed.error.issues.map(issue => ({
        field: issue.path.join('.') || null,
        message: issue.message
      }))
    }))
  }

  const { email, token, password } = parsed.data
  const result = await springConfirmPasswordReset(event, email, token, password)

  if (result.status === 204) {
    return sendNoContent(event)
  }

  return forwardSpringError(event, result)
})
