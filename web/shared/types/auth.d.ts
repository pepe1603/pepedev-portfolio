// ======================================================================
// TIPOS DE SESIÓN (BFF con nuxt-auth-utils)
//
// La autenticación es en modo BFF: el navegador solo ve la cookie de sesión
// sellada de Nuxt (httpOnly). El JWT de Spring vive exclusivamente en el
// servidor, dentro de `SecureSessionData`, y nunca se serializa al cliente
// (nuxt-auth-utils omite `secure` en /api/_auth/session).
//
// `User` es lo que sí puede conocer el cliente: el email y el rol. `secure`
// es lo que solo existe en el servidor: el access token, su caducidad y el
// refresh token.
//
// Este archivo es una augmentación de módulo (no una librería): no se
// importa, se incluye por estar bajo shared/types en el tsconfig generado.
// ======================================================================

/** Roles que emite Spring en el claim `role` del JWT. */
export type AuthRole = 'ADMIN' | 'USER'

declare module '#auth-utils' {
  interface User {
    /** Claim `sub` del JWT (email del usuario). */
    email: string
    /** Claim `role`: ADMIN para el panel; USER para el resto. */
    role: AuthRole
  }

  interface UserSession {
    /** epoch ms del momento del login, informativo. */
    loggedInAt: number
  }

  interface SecureSessionData {
    /** Access token (JWT) que se manda a Spring como Bearer. */
    accessToken: string
    /** epoch ms en que caduca el access token (según `expiresIn`). */
    accessExpiresAt: number
    /** Refresh token para renovar el access token. Puede faltar si Spring no lo mandó. */
    refreshToken: string | null
  }
}

export {}
