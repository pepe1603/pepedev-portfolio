// ======================================================================
// TIPO DEL EVENTO DE h3
//
// Nitro aporta el VALOR `H3Event` (de h3) como auto-import global, pero no
// expone un tipo global con ese nombre. Este repo evita `import ... from
// 'h3'` en server/ (ver el comentario de server/api/search.get.ts), así que
// el tipo se deriva del primer parámetro de un auto-import que ya lo usa:
// `useRuntimeConfig(event?)`.
// ======================================================================

export type NitroEvent = NonNullable<Parameters<typeof useRuntimeConfig>[0]>
