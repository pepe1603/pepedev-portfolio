// ======================================================================
// LECTURA DEL CUERPO DEL REQUEST PARA EL PROXY
//
// El proxy de admin tiene que reenviar tal cual lo que manda el cliente,
// incluido un multipart (subida a /admin/storage). Se lee el stream web del
// request a Uint8Array: es un tipo estándar (no necesita @types/node, que el
// tsconfig del servidor no incluye) y sirve tanto para JSON como para binario.
//
// Además, se lee UNA sola vez y se reutiliza: si el access token está revocado
// y hay que renovar y reintentar, el cuerpo no se puede volver a consumir de
// un stream ya agotado.
// ======================================================================
import type { NitroEvent } from './event'

const PAYLOAD_METHODS = new Set(['POST', 'PUT', 'PATCH', 'DELETE'])

export async function readRequestBody(event: NitroEvent): Promise<Uint8Array | undefined> {
  if (!PAYLOAD_METHODS.has(getMethod(event))) {
    return undefined
  }

  const stream = getRequestWebStream(event)
  if (!stream) {
    return undefined
  }

  const reader = stream.getReader()
  const chunks: Uint8Array[] = []
  let size = 0

  for (;;) {
    const { done, value } = await reader.read()
    if (done) {
      break
    }
    if (value) {
      const chunk = value as Uint8Array
      chunks.push(chunk)
      size += chunk.length
    }
  }

  if (size === 0) {
    return undefined
  }

  const bytes = new Uint8Array(size)
  let offset = 0
  for (const chunk of chunks) {
    bytes.set(chunk, offset)
    offset += chunk.length
  }
  return bytes
}
