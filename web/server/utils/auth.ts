// ======================================================================
// SESIÓN BFF (nuxt-auth-utils + Spring)
//
// Puente entre la sesión sellada de Nuxt y los tokens de Spring. Todo lo
// que habla con endpoints protegidos de Spring pasa por aquí para que el
// access token nunca salga del servidor y se renueve solo cuando caduca.
//
// Flujo:
//   1. Login OK  -> establishUserSession guarda tokens en la cookie sellada.
//   2. Cada petición protegida -> requireAccessToken mira la caducidad y,
//      si hace falta, renueva con el refresh token.
//   3. Spring responde 401 (token revocado antes de expirar) -> se reintenta
//      una vez tras renovar.
//   4. Refresh 401 -> la sesión se limpia (el refresh caducó o se revocó).
// ======================================================================
import type { NitroEvent } from './event'
import type { AuthRole } from '#shared/types/auth'
import type { ApiProblem, TokenResponse } from './spring'
import { decodeJwtPayload, springRefresh, springRequest } from './spring'

/** Margen para renovar antes de que el access token caduque de verdad. */
const ACCESS_TOKEN_SKEW_MS = 30_000

type ServerSession = Awaited<ReturnType<typeof getUserSession>>

/** Normaliza el claim `role` de Spring a los roles del frontend. */
export function roleFromClaim(claim: string | undefined): AuthRole {
  return claim === 'ADMIN' ? 'ADMIN' : 'USER'
}

/**
 * Guarda los tokens de Spring en la sesión sellada y actualiza `user` a
 * partir de los claims del access token (email y rol).
 */
export async function establishUserSession(
  event: NitroEvent,
  tokens: Pick<TokenResponse, 'accessToken' | 'expiresIn'>,
  refreshToken: string | null
) {
  const claims = decodeJwtPayload(tokens.accessToken)

  await setUserSession(event, {
    user: {
      email: claims.sub,
      role: roleFromClaim(claims.role)
    },
    secure: {
      accessToken: tokens.accessToken,
      accessExpiresAt: Date.now() + tokens.expiresIn * 1000,
      refreshToken
    },
    loggedInAt: Date.now()
  })

  return claims
}

function hasUsableAccessToken(session: ServerSession): boolean {
  const secure = session.secure
  if (!secure?.accessToken) {
    return false
  }
  return Date.now() < secure.accessExpiresAt - ACCESS_TOKEN_SKEW_MS
}

/**
 * Renueva el access token con el refresh token guardado. Si Spring rechaza
 * el refresh (caducado o revocado), limpia la sesión y devuelve null.
 */
export async function refreshUserSession(event: NitroEvent, session: ServerSession): Promise<string | null> {
  const refreshToken = session.secure?.refreshToken
  if (!refreshToken) {
    return null
  }

  const result = await springRefresh(event, refreshToken)
  if (result.status !== 200) {
    await clearUserSession(event)
    return null
  }

  const tokens = result.data as TokenResponse
  // La rotación devuelve un refresh nuevo; si no viniera, se conserva el que
  // ya teníamos (Spring solo lo rota cuando puede).
  await establishUserSession(event, tokens, result.refreshToken ?? refreshToken)
  return tokens.accessToken
}

/**
 * Devuelve un access token utilizable, renovándolo si está caducado o a
 * punto de caducar. Null si no hay sesión válida.
 */
export async function requireAccessToken(event: NitroEvent): Promise<string | null> {
  const session = await getUserSession(event)
  if (hasUsableAccessToken(session)) {
    return session.secure!.accessToken
  }
  return refreshUserSession(event, session)
}

const UNAUTHORIZED: ApiProblem = {
  title: 'Unauthorized',
  status: 401,
  detail: 'Sesión no válida o caducada'
}

export interface AuthenticatedRequestOptions {
  method?: string
  query?: Record<string, unknown>
  body?: unknown
  headers?: Record<string, string>
}

/**
 * Petición autenticada a Spring. Añade el Bearer, renueva si el token está
 * caducado y reintenta una vez si Spring responde 401 (token revocado antes
 * de expirar). Si no hay sesión, devuelve un 401 con envelope ProblemDetail
 * para que el frontend lo trate como cualquier otro error de la API.
 */
export async function springAuthenticatedRequest<T>(
  event: NitroEvent,
  path: string,
  options: AuthenticatedRequestOptions = {}
): Promise<Awaited<ReturnType<typeof springRequest<T>>> | { status: number, data: ApiProblem, refreshToken: null, retryAfter: null }> {
  let accessToken = await requireAccessToken(event)
  if (!accessToken) {
    return { status: 401, data: UNAUTHORIZED, refreshToken: null, retryAfter: null }
  }

  const send = (token: string) => springRequest<T>(event, path, {
    method: options.method,
    query: options.query,
    body: options.body,
    headers: { ...options.headers, authorization: `Bearer ${token}` }
  })

  let result = await send(accessToken)

  if (result.status === 401) {
    const session = await getUserSession(event)
    const renewed = await refreshUserSession(event, session)
    if (renewed) {
      accessToken = renewed
      result = await send(accessToken)
    }
  }

  return result
}
