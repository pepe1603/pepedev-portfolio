// ======================================================================
// COREOGRAFIA DE RevealOnScroll
// Vive aqui y no dentro del componente para que la documentacion pueda
// mostrar los retardos reales en vez de copiarlos a mano. Cuando los
// numeros vivian duplicados, la tabla de la pagina de ejemplo seguia
// enseñando los de duration 700 con tarjetas de 900.
// ======================================================================

export type RevealSequence = 'together' | 'lead' | 'staged'

/** Orden en que CSS lista transition-property. El retardo de cada uno
 *  marca su turno dentro de la cadena: primero la opacidad, despues el
 *  desplazamiento, y por ultimo la escala y el desenfoque. */
export const revealProperties = ['opacity', 'translate', 'scale', 'filter'] as const

/** Fraccion de `duration` que espera cada propiedad antes de arrancar. */
export const sequenceRatio: Record<RevealSequence, readonly number[]> = {
  together: [0, 0, 0, 0],
  lead: [0, 0.14, 0.14, 0.14],
  staged: [0, 0.2, 0.36, 0.36]
}

/** Al salir, el bloque solo tiene que apearse de en medio, no volver a
 *  lucirse: se resuelve en menos de la mitad de tiempo y sin encadenar. */
export const exitRatio = 0.45

function steps(sequence: RevealSequence, duration: number, delay: number, entering: boolean) {
  const total = entering ? duration : Math.round(duration * exitRatio)
  const ratio = sequenceRatio[entering ? sequence : 'together']
  const base = entering ? delay : 0

  return revealProperties.map((_, index) => base + Math.round(total * (ratio[index] ?? 0)))
}

/** Retardo real de cada propiedad al ENTRAR. Para documentacion y demos. */
export function revealSteps(sequence: RevealSequence, duration: number, delay = 0) {
  return steps(sequence, duration, delay, true)
}

/**
 * Declaracion de transicion completa.
 *
 * Viaja en el estado de DESTINO, y por eso entrar y salir se pueden
 * coreografiar por separado: al invertir el estado el elemento adopta las
 * duraciones de la serie que le toca, y CSS interpola desde el valor
 * actual, sin saltos.
 *
 * transitionProperty va en el style y no como clase de Tailwind porque
 * hace falta una duracion distinta por propiedad para poder encadenarlas, y
 * solo se pueden dar varias en una lista. Por eso el override de reduced
 * motion necesita `!` (motion-reduce:transition-none!) para ganarle.
 */
export function revealTransition(sequence: RevealSequence, duration: number, delay: number, entering: boolean) {
  const total = entering ? duration : Math.round(duration * exitRatio)
  const delayList = steps(sequence, duration, delay, entering)

  return {
    transitionProperty: revealProperties.join(', '),
    transitionDuration: revealProperties.map(() => `${total}ms`).join(', '),
    transitionDelay: delayList.map(ms => `${ms}ms`).join(', ')
  }
}
