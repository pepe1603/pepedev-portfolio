// ======================================================================
// RESPUESTAS DE ERROR UNIFORMES EN EL SERVIDOR BFF
//
// El frontend ya sabe leer el envelope ProblemDetail (RFC 9457) en un
// FetchError: mira `title`/`status`/`detail` y, en los 429, la cabecera
// Retry-After (ver app/composables/useApi.ts). Todos los endpoints del BFF
// deben responder lo mismo, tanto si el error lo genera Spring como si lo
// genera Nuxt (validación, falta de sesión), para que la UI no distinga.
// ======================================================================
import type { NitroEvent } from './event'
import type { ApiProblem } from './spring'

const REASON_PHRASES: Record<number, string> = {
  400: 'Bad Request',
  401: 'Unauthorized',
  403: 'Forbidden',
  404: 'Not Found',
  409: 'Conflict',
  413: 'Content Too Large',
  415: 'Unsupported Media Type',
  429: 'Too Many Requests',
  500: 'Internal Server Error'
}

/** Construye un ProblemDetail con el reason phrase en inglés del status. */
export function buildProblem(status: number, detail: string, extra: Partial<ApiProblem> = {}): ApiProblem {
  return {
    title: REASON_PHRASES[status] ?? 'Error',
    status,
    detail,
    ...extra
  }
}

/**
 * Escribe un ProblemDetail en la respuesta con su status. Devuelve el cuerpo
 * para que el handler pueda hacer `return sendApiProblem(...)`. Propaga
 * Retry-After en los 429 para que el frontend pueda contar la espera.
 */
export function sendApiProblem(event: NitroEvent, status: number, body: ApiProblem, retryAfter: number | null = null) {
  setResponseStatus(event, status)
  if (retryAfter) {
    // h3 tipa `retry-after` como number (segundos), no como string.
    setResponseHeader(event, 'retry-after', retryAfter)
  }
  return body
}

/**
 * Reenvía tal cual el error de Spring (ya viene como ProblemDetail) con su
 * status y su Retry-After. Si por lo que sea el cuerpo no es un envelope
 * válido, se fabrica uno genérico para no filtrar HTML ni stack traces.
 */
export function forwardSpringError(
  event: NitroEvent,
  result: { status: number, data: unknown, retryAfter: number | null }
) {
  const data = result.data
  const isProblem = data !== null
    && typeof data === 'object'
    && 'title' in data
    && 'status' in data

  const body = isProblem
    ? data as ApiProblem
    : buildProblem(result.status, 'Error de la API')

  return sendApiProblem(event, result.status, body, result.retryAfter)
}
