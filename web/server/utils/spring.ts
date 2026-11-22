// ======================================================================
// CLIENTE CRUDO CONTRA LA API DE SPRING
//
// Todo el tráfico a Spring que requiere sesión sale de aquí. Las llamadas
// se hacen con `$fetch.raw(..., { ignoreResponseError: true })` a propósito:
// el flujo de auth tiene respuestas 400/401/429/202 que NO son errores de
// red, sino parte del contrato (ver docs/API.md), y hay que poder leer el
// ProblemDetail y la cabecera Retry-After sin que ofetch lance.
//
// El refresh token viaja como cabecera `Cookie` manual, no como cookie del
// navegador: en modo BFF el navegador no habla con Spring, habla con Nuxt.
// ======================================================================
import type { NitroEvent } from './event'

/** Cuerpo de error uniforme de la API (RFC 9457). */
export interface ApiProblem {
  title: string
  status: number
  detail?: string
  instance?: string
  errors?: Array<{ field: string | null, message: string }>
}

/** Respuesta de login/refresh/verify de Spring. */
export interface TokenResponse {
  accessToken: string
  tokenType: string
  /** Segundos de vida del access token. */
  expiresIn: number
}

/** Respuesta 202 del login cuando el OTP está activo. */
export interface OtpChallenge {
  challengeId: string
}

/** Claims del JWT de Spring que usamos (ver docs/API.md). */
export interface AccessTokenClaims {
  sub: string
  role?: string
  jti?: string
  typ?: string
  tv?: number
  sid?: string
  iat?: number
  exp?: number
}

const API_BASE_FALLBACK = 'http://localhost:8080'

/** URL base de la API, sin barra final. */
export function springBaseUrl(event: NitroEvent): string {
  const runtime = useRuntimeConfig(event) as { apiBaseServer?: string; public?: { apiBase?: string } }
  const configured = runtime.apiBaseServer || runtime.public?.apiBase || API_BASE_FALLBACK
  return configured.replace(/\/+$/, '')
}

/** URL absoluta de un endpoint de la API. */
export function springUrl(event: NitroEvent, path: string): string {
  const suffix = path.startsWith('/') ? path : `/${path}`
  return `${springBaseUrl(event)}${suffix}`
}

/**
 * Decodifica base64url a texto UTF-8. Se usa atob + TextDecoder (globales en
 * Node 18+) en vez de Buffer para no depender de @types/node en el tsconfig
 * del servidor, que no los incluye.
 */
function base64UrlDecode(segment: string): string {
  const base64 = segment.replace(/-/g, '+').replace(/_/g, '/')
  const padded = base64.padEnd(Math.ceil(base64.length / 4) * 4, '=')
  const binary = atob(padded)
  const bytes = Uint8Array.from(binary, char => char.charCodeAt(0))
  return new TextDecoder().decode(bytes)
}

/**
 * Decodifica el payload de un JWT (segunda parte, base64url). No valida la
 * firma: la firma la valida Spring. Aquí solo se leen `sub`, `role` y `exp`
 * para saber a quién pertenece el token y cuándo renueva.
 */
export function decodeJwtPayload<T extends object = AccessTokenClaims>(token: string): T {
  const segment = token.split('.')[1]
  if (!segment) {
    throw new Error('JWT sin payload')
  }
  return JSON.parse(base64UrlDecode(segment)) as T
}

/**
 * Extrae el valor de una cookie de una cabecera Set-Cookie. Spring manda el
 * refresh token así, y en BFF hay que guardarlo para poder renovar.
 */
export function readSetCookie(header: string | null | undefined, name: string): string | null {
  if (!header) {
    return null
  }
  const match = new RegExp(`(?:^|[,\\s])${name}=([^;,]*)`).exec(header)
  const value = match?.[1]
  return value ? decodeURIComponent(value) : null
}

function retryAfterFrom(headers: Headers): number | null {
  const raw = headers.get('retry-after')
  if (!raw) {
    return null
  }
  const seconds = Number(raw)
  return Number.isFinite(seconds) ? seconds : null
}

/**
 * Cabeceras que identifican al cliente REAL para Spring.
 *
 * El navegador no habla con la API: habla con Nitro, y Nitro con Spring. Sin
 * esto, Spring solo ve la IP y el user-agent del propio Nitro (::1 y "node"),
 * y eso es lo que aparecía en la lista de sesiones de Ajustes.
 *
 * - `user-agent`: el del navegador, tal cual llega al borde.
 * - `x-forwarded-for`: la IP del cliente. Se toma de la conexión directa
 *   (`xForwardedFor: false`) y NO de una cabecera que ya viniera, para no
 *   dejar que un cliente invente su propia IP. Si algún día se pone un proxy
 *   delante de Nitro, habría que pasar a leer la cadena por la derecha.
 * - `x-forwarded-proto`: http/https, que ya resuelve Nitro.
 *
 * Spring las consume con `server.forward-headers-strategy=framework`: a partir
 * de ahí, `getRemoteAddr()` devuelve la IP de x-forwarded-for. Por eso la API
 * no debe exponerse directamente: esa cabecera se puede falsificar si alguien
 * llega a Spring sin pasar por Nitro.
 */
export function clientForwardedHeaders(event: NitroEvent): Record<string, string> {
  const headers: Record<string, string> = {}

  const userAgent = getRequestHeader(event, 'user-agent')
  if (userAgent) {
    headers['user-agent'] = userAgent
  }

  const ip = getRequestIP(event, { xForwardedFor: false })
  if (ip) {
    headers['x-forwarded-for'] = ip
  }

  const proto = getRequestProtocol(event, { xForwardedProto: false })
  if (proto) {
    headers['x-forwarded-proto'] = proto
  }

  return headers
}

/** Respuesta cruda de Spring: estado, cuerpo ya parseado y metadatos. */
export interface SpringResult<T> {
  status: number
  data: T
  /** Valor de la cookie `refresh_token` si Spring la mandó. */
  refreshToken: string | null
  /** Segundos de Retry-After en los 429. */
  retryAfter: number | null
}

/**
 * Petición cruda a Spring. Es la base de todos los helpers: expone método,
 * query, cuerpo y cabeceras, y nunca lanza por un status >= 400.
 */
export async function springRequest<T>(
  event: NitroEvent,
  path: string,
  options: {
    method?: string
    query?: Record<string, unknown>
    body?: unknown
    headers?: Record<string, string>
  } = {}
): Promise<SpringResult<T>> {
  // Las cabeceras del cliente real van de base; lo que pida cada llamada
  // (authorization, cookie...) las sobrescribe. Así toda llamada a Spring
  // lleva la IP y el user-agent del navegador sin repetirlo en cada helper.
  const headers = { ...clientForwardedHeaders(event), ...options.headers }

  const response = await $fetch.raw<T>(springUrl(event, path), {
    method: (options.method ?? 'GET') as 'GET',
    query: options.query,
    body: options.body as Record<string, unknown> | undefined,
    headers,
    ignoreResponseError: true
  })

  return {
    status: response.status,
    data: response._data as T,
    refreshToken: readSetCookie(response.headers.get('set-cookie'), 'refresh_token'),
    retryAfter: retryAfterFrom(response.headers)
  }
}

/** POST /auth/login */
export function springLogin(event: NitroEvent, email: string, password: string) {
  return springRequest<TokenResponse | OtpChallenge | ApiProblem>(event, '/auth/login', {
    method: 'POST',
    body: { email, password }
  })
}

/** POST /auth/login/verify */
export function springVerifyOtp(event: NitroEvent, challengeId: string, code: string) {
  return springRequest<TokenResponse | ApiProblem>(event, '/auth/login/verify', {
    method: 'POST',
    body: { challengeId, code }
  })
}

/** POST /auth/login/resend */
export function springResendOtp(event: NitroEvent, challengeId: string) {
  return springRequest<ApiProblem | undefined>(event, '/auth/login/resend', {
    method: 'POST',
    body: { challengeId }
  })
}

/** POST /auth/refresh (usa la cookie de refresh como cabecera Cookie). */
export function springRefresh(event: NitroEvent, refreshToken: string) {
  return springRequest<TokenResponse | ApiProblem>(event, '/auth/refresh', {
    method: 'POST',
    headers: { cookie: `refresh_token=${refreshToken}` }
  })
}

/** POST /auth/logout (revoca access y refresh; siempre 204). */
export function springLogout(event: NitroEvent, accessToken: string | null, refreshToken: string | null) {
  const headers: Record<string, string> = {}
  if (accessToken) {
    headers.authorization = `Bearer ${accessToken}`
  }
  if (refreshToken) {
    headers.cookie = `refresh_token=${refreshToken}`
  }
  return springRequest<ApiProblem | undefined>(event, '/auth/logout', {
    method: 'POST',
    headers
  })
}

/** POST /auth/reset/request */
export function springRequestPasswordReset(event: NitroEvent, email: string) {
  return springRequest<ApiProblem | undefined>(event, '/auth/reset/request', {
    method: 'POST',
    body: { email }
  })
}

/** POST /auth/reset/confirm */
export function springConfirmPasswordReset(event: NitroEvent, email: string, token: string, password: string) {
  return springRequest<ApiProblem | undefined>(event, '/auth/reset/confirm', {
    method: 'POST',
    body: { email, token, password }
  })
}
