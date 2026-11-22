// ======================================================================
// POST /api/auth/logout
//
// Cierra la sesión en los dos extremos: revoca en Spring el access y el
// refresh token (best-effort, siempre responde 204) y borra la cookie de
// sesión sellada de Nuxt. Aunque Spring falle, la sesión local se limpia:
// el usuario que pulsa "salir" debe quedar fuera.
// ======================================================================
export default defineEventHandler(async (event) => {
  const session = await getUserSession(event)
  const accessToken = session.secure?.accessToken ?? null
  const refreshToken = session.secure?.refreshToken ?? null

  if (accessToken || refreshToken) {
    // best-effort: un fallo de red no debe impedir el cierre local.
    await springLogout(event, accessToken, refreshToken).catch(() => undefined)
  }

  await clearUserSession(event)
  return sendNoContent(event)
})
