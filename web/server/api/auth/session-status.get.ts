// ======================================================================
// GET /api/auth/session-status
//
// Sondeo de sesión para el cliente: "¿sigo dentro?".
//
// POR QUÉ EXISTE Y NO BASTA CON /api/auth/me
//
// /api/auth/me solo lee la cookie sellada y responde 200 mientras la cookie
// exista (7 días), aunque el refresh token de Spring ya esté caducado o
// revocado. Es decir, /me certifica que HUBO una sesión, no que SIGA viva.
// Para saberlo de verdad hay que ejercitar la cadena real, y eso es lo que
// hace este endpoint vía requireAccessToken.
//
// POR QUÉ NO ES UN REFRESH CIEGO
//
// requireAccessToken solo renueva si el access token está caducado o a
// menos de 30 s de caducar (ACCESS_TOKEN_SKEW_MS). Si todavía vale, la
// respuesta es una lectura de cookie: cero rotaciones.
//
// Esto importa porque Spring ROTA el refresh token y pone el anterior en
// denylist en cada renovación. Un refresh a ciegas en cada vuelta a la
// pestaña quemaría un refresh token por alt-tab, y con dos pestañas
// abiertas dispararía una carrera (una rota, la otra usa el jti ya
// revocada → 401 → ambas expulsadas sin motivo).
//
// POR QUÉ TAMBIÉN LIMPIA LA SESIÓN AL FALLAR
//
// requireAccessToken devuelve null en dos casos: el refresh fue rechazado
// (ya limpia la sesión él) y el caso raro de un access token caducado sin
// refresh token que reintentar. En ese segundo caso la cookie seguiría
// viva pero no serviría para llamar a nadie, y el middleware de /admin/**
// la daría por válida: bucle entre /admin y el 401. Limpiarla aquí deja el
// estado en un único sitio coherente: sin sesión.
//
// Respuestas:
//   200 -> { user }  (sesión viva; el access token pudo haberse renovado)
//   401 -> ProblemDetail (sin sesión, o la sesión ya no puede renovarse)
// ======================================================================
export default defineEventHandler(async (event) => {
  const session = await getUserSession(event)

  if (!session.user) {
    return sendApiProblem(event, 401, buildProblem(401, 'No autenticado'))
  }

  const accessToken = await requireAccessToken(event)
  if (!accessToken) {
    await clearUserSession(event)
    return sendApiProblem(event, 401, buildProblem(401, 'La sesión ha caducado'))
  }

  // Se relee la sesión porque el acceso pudo haber renovado los tokens y
  // con ellos el `user` de la cookie sellada.
  const refreshed = await getUserSession(event)
  return { user: refreshed.user }
})
