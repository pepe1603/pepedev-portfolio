<script setup lang="ts">
// ======================================================================
// REVEAL ON SCROLL
// Unica pieza de animacion de la plantilla.
//
// VueUse decide CUANDO (un booleano), Tailwind decide COMO. Ninguna de las
// dos capas conoce a la otra, y por eso el observer se puede quitar sin
// tocar una clase y las clases se pueden tocar sin tocar el observer.
//
// AVISO 1: los estados "oculto" y "visible" son conjuntos de clases
// MUTUAMENTE EXCLUSIVOS y no deben mezclarse nunca. En Tailwind v4
// `translate-y-14` se emite DESPUES que `translate-y-0`, y con la misma
// especificidad gana el orden de la hoja de estilos, no el del atributo
// class. Un elemento con ambos jamas se moveria. Por eso el estado visible
// no lleva `translate-y-0`: lleva el reseteo del eje que ese animation
// toca, y nada mas.
//
// AVISO 2: por defecto once=false, asi que el bloque reaparece cada vez que
// vuelve a entrar en pantalla. Como arranca en false, sin JavaScript el
// contenido se queda en opacity-0. Es el precio de no ocultar nada hasta
// que el observer dice lo contrario. Para un sitio que deba funcionar sin
// JS, la alternativa es aplicar el estado oculto solo tras onMounted, a
// costa de un flash.
//
// AVISO 3: `threshold` 0 + rootMargin negativo en vez de un umbral alto.
// El umbral es un ratio, y una seccion de 3000px en un viewport de 800px
// nunca pasa de 0.26: con threshold 0.15 las secciones altas no se
// dispararian nunca. El rootMargin encoge el viewport, asi que el disparo
// no depende de la altura del elemento.
//
// AVISO 4: el animation `blur` deja `filter: blur(0)` en el estado visible.
// Un filter distinto de none crea bloque contenedor para descendientes
// fixed y un nuevo contexto de apilamiento. No afecta a este caso, pero
// hay que saberlo antes de meter un tooltip o un modal dentro de un
// RevealOnScroll con blur.
// ======================================================================

type RevealAnimation = 'fade' | 'fade-up' | 'fade-down' | 'from-left' | 'from-right' | 'zoom-in' | 'zoom-out' | 'blur'
type RevealEasing = 'out' | 'in-out' | 'soft' | 'back'

// `from-left` / `from-right` nombran de donde ENTRA el bloque, no hacia donde
// viaja: from-left arranca desplazado hacia la izquierda (-x) y se desplaza
// hacia la derecha al asentarse. Invertir el signo aqui cambia el sentido.
// La coreografia (sequence, retardos, salida) vive en utils/reveal.ts para
// que la pagina de ejemplo pueda mostrar los numeros reales.

const props = withDefaults(defineProps<{
  /** Desplazamiento y escalado iniciales. `fade` no mueve nada. */
  animation?: RevealAnimation
  /** Curva de la transicion. `soft` es easeOutExpo: frena muy al final,
   *  que es lo que hace que un movimiento se lea como suave y no brusco. */
  easing?: RevealEasing
  /** Duracion de la transicion en ms. */
  duration?: number
  /** Retardo en ms, para escalonar hijos con el mismo animation. */
  delay?: number
  /** Como se encadenan las propiedades dentro de una misma entrada. */
  sequence?: RevealSequence
  /** false = reaparece cada vez que vuelve a entrar. true = solo la 1a vez. */
  once?: boolean
  /** Se lee una vez, al montar. */
  threshold?: number
  /** Se lee una vez, al montar. */
  rootMargin?: string
}>(), {
  animation: 'fade-up',
  easing: 'soft',
  duration: 700,
  delay: 0,
  sequence: 'staged',
  once: false,
  threshold: 0,
  rootMargin: '-12% 0px -12% 0px'
})

const target = ref<HTMLElement | null>(null)
const isVisible = ref(false)

const { stop } = useIntersectionObserver(
  target,
  ([entry]) => {
    if (!entry) {
      return
    }

    if (entry.isIntersecting) {
      isVisible.value = true

      if (props.once) {
        stop()
      }
    } else if (!props.once) {
      isVisible.value = false
    }
  },
  { threshold: props.threshold, rootMargin: props.rootMargin }
)

// Cada animation declara su propio reseteo de motion-reduce. Asi el
// usuario que pide menos movimiento no ve ni desplazamiento, ni escala, ni
// desenfoque, y el estado visible no arrastra transform ni filter en el
// resto de los casos.
const hiddenClass: Record<RevealAnimation, string> = {
  'fade': 'opacity-0',
  'fade-up': 'translate-y-14 opacity-0 motion-reduce:translate-none',
  'fade-down': '-translate-y-14 opacity-0 motion-reduce:translate-none',
  'from-left': '-translate-x-14 opacity-0 motion-reduce:translate-none',
  'from-right': 'translate-x-14 opacity-0 motion-reduce:translate-none',
  'zoom-in': 'scale-90 opacity-0 motion-reduce:scale-100',
  'zoom-out': 'scale-110 opacity-0 motion-reduce:scale-100',
  'blur': 'translate-y-10 scale-105 opacity-0 blur-sm motion-reduce:translate-none motion-reduce:scale-100 motion-reduce:blur-none'
}

const visibleClass: Record<RevealAnimation, string> = {
  'fade': 'opacity-100',
  'fade-up': 'translate-none opacity-100',
  'fade-down': 'translate-none opacity-100',
  'from-left': 'translate-none opacity-100',
  'from-right': 'translate-none opacity-100',
  'zoom-in': 'scale-100 opacity-100',
  'zoom-out': 'scale-100 opacity-100',
  'blur': 'translate-none scale-100 opacity-100 blur-none'
}

const easingClass: Record<RevealEasing, string> = {
  'out': 'ease-out',
  'in-out': 'ease-in-out',
  'soft': 'ease-[cubic-bezier(0.16,1,0.3,1)]',
  'back': 'ease-[cubic-bezier(0.34,1.4,0.64,1)]'
}

const state = computed(() => isVisible.value
  ? visibleClass[props.animation]
  : hiddenClass[props.animation]
)

const style = computed(() => revealTransition(
  props.sequence,
  props.duration,
  props.delay,
  isVisible.value
))
</script>

<template>
  <div
    ref="target"
    :class="[
      easingClass[easing],
      'motion-reduce:transition-none!',
      state
    ]"
    :style="style"
  >
    <slot />
  </div>
</template>
