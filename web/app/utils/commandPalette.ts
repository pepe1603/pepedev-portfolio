// ======================================================================
// ESTADO COMPARTIDO DEL COMMAND PALETTE
// Vive aqui y no dentro de AppCommandPalette.vue porque lo consumen dos
// sitios a la vez: el propio palette (que lo abre y lo cierra) y el boton
// del header (que lo abre desde fuera).
//
// Si el ref viviera dentro del componente, el boton del header no tendria
// nada a que volver: tendria que buscar el componente en el arbol, y en
// Vue eso no se hace. Un useState es la unica forma limpia de que dos
// sitios distintos compartan el mismo interruptor.
//
// OJO con la diferencia con un ref normal, que es lo que hace esto
// peligroso en SSR: useState serializa su valor al HTML y lo rehydrata en
// el cliente. Un ref normal crearia DOS estados distintos (uno en el
// servidor y otro en el cliente) y el HTML no coincidiria con lo que Vue
// espera. Aqui da igual porque el valor inicial es false en los dos, pero
// el motivo de usar useState y no ref sigue siendo este.
export function useCommandPalette() {
  return useState<boolean>('command-palette:open', () => false)
}

// ======================================================================
// ATAJOS DE TECLADO
//
// --------------------------------------------------------------------------
// POR QUE NO `defineShortcuts`, NI `useMagicKeys({ meta_k: fn })`
//
// Las dos formas que salen en ejemplos viejos de esta API fallan en
// @vueuse/core v14, y fallan en silencio:
//
//   1. `defineShortcuts` NO EXISTE. El composable se llama useMagicKeys.
//      Un ejemplo con defineShortcuts({ meta_k: ... }) sale de una version
//      anterior de la biblioteca.
//
//   2. `useMagicKeys({ meta_k: fn })` TAMPOCO. El objeto de opciones solo
//      acepta reactive, target, aliasMap, passive y onEventFired; no hay
//      ninguna forma de pasar handlers. Lo que devuelve son refs: cada
//      combinacion del objeto es un ComputedRef<boolean> que dice si esa
//      combinacion esta pulsada AHORA.
//
//      Es decir, useMagicKeys no es "registra estos atajos", es "dime que
//      teclas hay pulsadas". Para reaccionar hay que enganchar un watch a
//      cada combinacion, que es lo que se hace abajo.
//
// Comprobado en el codigo: useMagicKeys devuelve un Proxy sobre refs y la
// forma de una combinacion se parte con split(/[+_-]/), asi que `meta_k` y
// `meta_shift_l` funcionan como nombre de combinacion.
//
// --------------------------------------------------------------------------
// POR QUE UNA COMBINACION POR SISTEMA Y NO UNA
//
// meta_k es el ⌘K de un Mac. En Windows y en Linux el atajo de buscar en
// todo es Ctrl+K, que aqui es ctrl_k. Registrar solo meta_k deja el palette
// inalcanzable en dos de los tres sistemas. Por eso todos los atajos van
// por pares: meta_ para Mac y ctrl_ para el resto.
//
// OJO: Ctrl+K es tambien "borra hasta el final de la linea" en la barra de
// direcciones de Chrome, y ⌘⇧L / Ctrl+⇧L enfocan la barra de direcciones.
// Por eso passive: false: es lo unico que permite llamar a preventDefault.
// Con el default (true) el navegador ignora la llamada.
//
// --------------------------------------------------------------------------
// POR QUE SOLO ABRIR Y NO ALTERNAR
//
// Manteniendo la tecla pulsada, el teclado repite los keydown. Un toggle
// (abrir/cerrar) se entonces abriria y cerraria sin parar. Con solo abrir la
// accion es idempotente y el problema desaparece sin logica de mas.
//
// --------------------------------------------------------------------------
// POR QUE NADA SE REGISTRA EN onEventFired
//
// Se podria interceptar todo ahi y leer event.metaKey y event.shiftKey a
// mano, pero es facil equivocarse: un `event.key === 'l'` sin comprobar los
// modificadores dispara la accion al escribir una L mayuscula, y como el
// foco esta en el input de busqueda, eso es escribir una letra. Los refs de
// useMagicKeys exigen la combinacion COMPLETA (meta Y shift Y l), asi que
// no tienen ese fallo.
// --------------------------------------------------------------------------
// POR QUE UN SOLO useMagicKeys PARA TODO
//
// useMagicKeys engancha un listener de keydown y otro de keyup a `window`
// por cada llamada. Dos llamadas son dos listeners para el mismo evento,
// lo cual no rompe nada pero duplica trabajo en cada pulsacion y hace que
// leer el composable parezca mas complicado de lo que es. Aqui hay una
// sola instancia que alimenta todas las combinaciones.
//
// --------------------------------------------------------------------------
// POR QUE NADA SE REGISTRA EN onEventFired
//
// Se podria interceptar todo ahi y leer event.metaKey y event.shiftKey a
// mano, pero es facil equivocarse: un `event.key === 'l'` sin comprobar los
// modificadores dispara la accion al escribir una L mayuscula, y como el
// foco esta en el input de busqueda, eso es escribir una letra. Los refs de
// useMagicKeys exigen la combinacion COMPLETA (meta Y shift Y l), asi que
// no tienen ese fallo.
export function useCommandPaletteShortcuts(actions: {
  toggleColorMode: () => void
  copyUrl: () => void | Promise<void>
}) {
  const isOpen = useCommandPalette()

  // passive: false habilita preventDefault. No se pasan handlers en el
  // objeto: eso no existe en v14, ver el comentario de arriba.
  const magic = useMagicKeys({ passive: false })

  // Solo abre, nunca alterna. El `if (pressed)` vive dentro de watchKey: aqui
  // no llega nada cuando la tecla se suelta.
  const openPalette = () => {
    isOpen.value = true
  }

  // --------------------------------------------------------------------------
  // POR QUE ESTE watchKey Y NO watch() A MANO
  //
  // El tsconfig de Nuxt activa `noUncheckedIndexedAccess`, y el objeto que
  // devuelve useMagicKeys es un `Record<string, ComputedRef<boolean>>`, no un
  // objeto cerrado. Pese a que la clave se escribe a mano (`magic.meta_k`),
  // TS la trata como acceso por indice, asi que su tipo es
  // `ComputedRef<boolean> | undefined`.
  //
  // Y `watch(undefined, cb)` no compila: el overload que le queda exige un
  // objeto. De ahi sale el error "No overload matches this call", que no
  // senala la clave sino la firma de watch, y parece un problema con el
  // segundo argumento cuando en realidad es el primero el que puede faltar.
  //
  // La comprobacion va aqui, una sola vez, y se queda con el `if (pressed)`
  // que antes se repetia en cada llamada.
  function watchKey(source: ComputedRef<boolean> | undefined, run: () => void) {
    if (!source) {
      return
    }

    watch(source, (pressed) => {
      if (pressed) {
        run()
      }
    })
  }

  // ⌘K en Mac, Ctrl+K en Windows y Linux.
  watchKey(magic.meta_k, openPalette)
  watchKey(magic.ctrl_k, openPalette)

  // Por pares tambien los de las acciones, porque en Windows y Linux el
  // atajo de tema y copiar es Ctrl+Shift+..., no ⌘. Un kbds que solo
  // funciona en Mac es un kbds que no funciona en la mitad del mundo.
  //
  // Van las seis claves escritas a mano en vez de en un bucle sobre un array.
  // Un bucle hace que la clave se lea por indice (`magic[key]`), que es
  // exactamente lo que dispara el `| undefined` de arriba: con la clave escrita
  // como propiedad del literal tambien se le aplica noUncheckedIndexedAccess,
  // pero aqui es un solo sitio donde comprobarlo.
  watchKey(magic.meta_shift_l, () => actions.toggleColorMode())
  watchKey(magic.ctrl_shift_l, () => actions.toggleColorMode())
  watchKey(magic.meta_shift_c, () => void actions.copyUrl())
  watchKey(magic.ctrl_shift_c, () => void actions.copyUrl())

  return isOpen
}
