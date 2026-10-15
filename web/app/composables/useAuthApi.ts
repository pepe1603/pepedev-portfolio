// ======================================================================
// CLIENTE DE AUTENTICACIÓN (BFF)
//
// Envuelve las llamadas a los endpoints de /api/auth/** que sirve Nuxt. No
// habla con Spring directamente: en modo BFF el navegador solo conoce las
// rutas de Nitro, y es Nitro quien lleva los tokens a Spring.
//
// Reutiliza toApiError (composables/useApi.ts) para que un 401, un 429 con
// Retry-After o un 400 de validación lleguen siempre como ApiError con el
// mismo envelope ProblemDetail que usa el resto de la app.
// ======================================================================
import type { AuthUser } from '#shared/types/auth'
import { toApiError } from './useApi'

/** Respuesta 202 del login cuando el OTP está activo. */
export interface LoginChallenge {
  challengeId: string
}

/** Login: sesión iniciada o salto a la verificación por código. */
export type LoginResult = { user: AuthUser } | LoginChallenge

interface RequestOptions {
  method?: 'POST' | 'DELETE'
  body?: Record<string, unknown>
}

async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  try {
    // El cast es el mismo que en useApi: el $fetch tipeado de Nuxt devuelve
    // TypedInternalResponse, que TS no unifica con un T arbitrario.
    return await $fetch<T>(path, {
      method: options.method ?? 'POST',
      body: options.body
    }) as T
  } catch (error) {
    throw toApiError(error)
  }
}

export function useAuthApi() {
  return {
    /** POST /api/auth/login */
    login(email: string, password: string) {
      return request<LoginResult>('/api/auth/login', { body: { email, password } })
    },

    /** POST /api/auth/login/verify — segundo paso del OTP. */
    verifyOtp(challengeId: string, code: string) {
      return request<{ user: AuthUser }>('/api/auth/login/verify', { body: { challengeId, code } })
    },

    /** POST /api/auth/login/resend — reenvía el código del challenge actual. */
    resendOtp(challengeId: string) {
      return request<{ ok: true }>('/api/auth/login/resend', { body: { challengeId } })
    },

    /** POST /api/auth/refresh — fuerza la renovación del access token. */
    refresh() {
      return request<{ user: AuthUser }>('/api/auth/refresh')
    },

    /** POST /api/auth/logout — revoca en Spring y borra la sesión sellada. */
    logout() {
      return request<unknown>('/api/auth/logout')
    },

    /** POST /api/auth/reset/request — solicita el correo de recuperación. */
    requestPasswordReset(email: string) {
      return request<unknown>('/api/auth/reset/request', { body: { email } })
    },

    /** POST /api/auth/reset/confirm — fija la nueva contraseña con el token. */
    confirmPasswordReset(email: string, token: string, password: string) {
      return request<unknown>('/api/auth/reset/confirm', { body: { email, token, password } })
    }
  }
}
