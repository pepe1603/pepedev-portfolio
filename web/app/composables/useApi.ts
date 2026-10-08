// ======================================================================
// ACCESO CENTRALIZADO A LA API
//
// La plantilla no trae ninguna abstraccion para llamar a una API (lo mas
// parecido es server/api/search.get.ts, que es un indice estatico de nitro),
// asi que este composable es infraestructura nueva, no una duplicacion.
//
// POR QUE EXISTE
// 1. El base URL sale SIEMPRE de runtimeConfig.public.apiBase: ningun
//    componente conoce el host de la API.
// 2. Los endpoints publicos reciben ?lang= en un solo sitio; si un dia se
//    anade otro idioma o cambia el parametro, el cambio es aqui y no en
//    cada pagina.
// 3. Los errores llegan siempre en el envelope ProblemDetail de la API
//    (RFC 9457, ver docs/API.md): { title, status, detail, instance } mas
//    errors[] en los 400 y Retry-After en los 429. Sin normalizar aqui,
//    cada pagina tendria que saber parsear el cuerpo de un FetchError.
// ======================================================================

import type { FetchError } from 'ofetch'

/** Envelope de error de la API (RFC 9457 / ProblemDetail). */
export interface ProblemDetail {
  /** Reason phrase en ingles del status ("Not Found", "Too Many Requests"...). */
  title: string
  status: number
  /** Mensaje en español, específico del caso. */
  detail?: string
  /** Ruta del request que produjo el error. */
  instance?: string
  /** Solo en 400 de validación; field null = error de objeto. */
  errors?: Array<{ field: string | null, message: string }>
  /** Solo en 429: segundos del header Retry-After, si el servidor lo manda. */
  retryAfter?: number
}

/**
 * Error unico que reciben las paginas: sea 404, 429 o la red caida, el
 * caller mira `error.problem.status` y no tiene que distinguir formatos.
 */
export class ApiError extends Error {
  readonly problem: ProblemDetail

  constructor(problem: ProblemDetail) {
    // El message heredado es para logs/debug; la UI pinta problem.detail.
    super(problem.detail ?? problem.title)
    this.name = 'ApiError'
    this.problem = problem
  }

  get status(): number {
    return this.problem.status
  }
}

/**
 * Convierte lo que tira $fetch en un ApiError. Cualquier cosa que no sea un
 * ProblemDelail parseable (sin conexion, JSON inesperado...) queda como un
 * error generico con status 0, para que la UI pueda distinguirlo de un 404
 * real sin tratar `undefined`.
 */
function toApiError(error: unknown): ApiError {
  if (error instanceof ApiError) return error

  const fetchError = error as FetchError
  const body = fetchError?.body as ProblemDetail | undefined
  const retryAfterHeader = fetchError?.response?.headers?.get('retry-after')

  // ofetch ya parsea el cuerpo JSON; el envelope se reconoce por title+status.
  if (body && typeof body === 'object' && 'title' in body && 'status' in body) {
    return new ApiError({
      ...body,
      // El 429 manda los segundos por cabecera, no por el body.
      retryAfter: retryAfterHeader ? Number(retryAfterHeader) : body.retryAfter
    })
  }

  return new ApiError({
    title: 'Error',
    status: fetchError?.status ?? 0,
    detail: fetchError?.message ?? 'No hay conexión con el servidor'
  })
}

export function useApi() {
  const config = useRuntimeConfig()
  const lang = useLang()

  const client = $fetch.create({
    baseURL: config.public.apiBase
  })

  async function request<T>(url: string, options?: Parameters<typeof client>[1]): Promise<T> {
    try {
      return await client<T>(url, options)
    } catch (error) {
      throw toApiError(error)
    }
  }

  return {
    /**
     * GET a un endpoint publico (/public/**). Inyecta ?lang= con el idioma
     * activo; el resto de params van tal cual.
     */
    getPublic<T>(path: string, params: Record<string, string> = {}): Promise<T> {
      return request<T>(path, {
        method: 'GET',
        params: { ...params, lang: lang.value }
      })
    },

    /**
     * POST generico (ahora solo /contact). No lleva ?lang porque el
     * endpoint no lo acepta; el idioma del acuse lo decide Accept-Language,
     * que aqui se pone del idioma activo.
     */
    post<T>(path: string, body: unknown): Promise<T> {
      return request<T>(path, {
        method: 'POST',
        body,
        headers: { 'Accept-Language': lang.value }
      })
    }
  }
}
