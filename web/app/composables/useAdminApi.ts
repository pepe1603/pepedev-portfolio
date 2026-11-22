// ======================================================================
// CLIENTE ADMIN (BFF → /api/admin/**)
//
// Único sitio de la app que conoce el prefijo /api/admin. No habla con
// Spring directamente: pide al proxy de Nitro (server/api/admin/[...path].ts),
// que añade el Bearer de la sesión sellada, comprueba el rol y reenvía. El
// JWT sigue sin salir del servidor.
//
// --------------------------------------------------------------------------
// POR QUÉ EXISTE SI YA ESTÁN useApi Y useAuthApi
//
// Porque no es una copia de las dos. useApi apunta a la API pública con
// ?lang=, useAuthApi a /api/auth con sus propios códigos (202 de login,
// 401 que significa "credenciales mal"). Aquí la distinción que importa es
// otra: un 401 no puede significar nada más que "la sesión murió", porque
// el proxy ya comprobó el rol antes de reenviar. De ahí que este cliente
// sea quien cierra el circuito con el modal de sesión caducada.
//
// --------------------------------------------------------------------------
// QUÉ NO MANEJA
//
// Un 403 (la sesión es válida pero ya no es de administración) se propaga
// como cualquier otro error: es un caso de la página, no del transporte, y
// el middleware de /admin/** ya redirige a /403 en la siguiente navegación.
//
// --------------------------------------------------------------------------
// USO
//
//   const api = useAdminApi()
//   const projects = await api.get<AdminProject[]>('/projects')
//   const created = await api.post<AdminProject>('/projects', body)
//   await api.patch(`/projects/${id}/publish`)
//   await api.remove(`/projects/${id}`)
//   const { url } = await api.upload<StorageUploadResult>('/storage', file, 'thumbnail')
// ======================================================================
import { toApiError } from './useApi'

export function useAdminApi() {
  const guard = useSessionGuard()

  const client = $fetch.create({ baseURL: '/api/admin' })

  // El tipo de opciones sale del cliente tipeado de Nuxt, que exige `method`
  // como literal HTTP; igual que en useApi. El NonNullable hace falta porque
  // Parameters<...>[1] es opcional, y sin quitarle el `undefined` no se puede
  // hacer `[ 'body' ]` ni `['query']` de él.
  type RequestOptions = NonNullable<Parameters<typeof client>[1]>

  async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
    try {
      // El mismo cast que en useApi/useAuthApi: el $fetch tipeado devuelve
      // TypedInternalResponse, que TS no unifica con un T arbitrario.
      return await client(path, options) as T
    } catch (error) {
      const apiError = toApiError(error)

      // Señal autoritativa: el proxy solo devuelve 401 si la sesión ya no
      // puede renovarse (sin sesión, o el refresh falló y el servidor ya la
      // limpió). Se avisa con el mismo modal que usa el watcher de sesión.
      // Se relanza el error igualmente: quien llama decide qué pintar.
      if (apiError.problem.status === 401) {
        guard.mark(apiError.problem.detail)
      }

      throw apiError
    }
  }

  return {
    /** GET /admin/<path>, con query opcional. */
    get<T>(path: string, query?: RequestOptions['query']): Promise<T> {
      return request<T>(path, { method: 'GET', query })
    },

    /** POST /admin/<path>. Crea siempre DRAFT en proyectos y certificados. */
    post<T>(path: string, body?: RequestOptions['body'], query?: RequestOptions['query']): Promise<T> {
      return request<T>(path, { method: 'POST', body, query })
    },

    /**
     * PUT /admin/<path>: edición COMPLETA. Lo que no venga en el body se
     * guarda como null en la API, así que no es un "actualiza lo que pases".
     */
    put<T>(path: string, body?: RequestOptions['body']): Promise<T> {
      return request<T>(path, { method: 'PUT', body })
    },

    /** PATCH /admin/<path>: transiciones (publish, unpublish, read, archive). */
    patch<T>(path: string, body?: RequestOptions['body']): Promise<T> {
      return request<T>(path, { method: 'PATCH', body })
    },

    /** DELETE /admin/<path>: responde 204 sin cuerpo. */
    remove<T = void>(path: string): Promise<T> {
      return request<T>(path, { method: 'DELETE' })
    },

    /**
     * POST /admin/storage?use=<StorageUse>: subida multipart.
     *
     * `use` va por QUERY y el fichero en la parte `file`: la API lo exige
     * así, porque si recibe el mismo parámetro por query y por form field lo
     * une con coma y responde 400. El boundary del multipart lo pone el
     * navegador al montar el FormData, y el proxy reenvía el content-type
     * tal cual.
     */
    upload<T>(path: string, file: Blob, use: StorageUse): Promise<T> {
      const form = new FormData()
      form.append('file', file)
      return request<T>(path, { method: 'POST', query: { use }, body: form })
    }
  }
}
