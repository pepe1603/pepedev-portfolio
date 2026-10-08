<script setup lang="ts">
// ======================================================================
// MarqueeCards: testimonios en movimiento continuo, en sentido contrario.
//
// ----------------------------------------------------------------------
// POR QUE NO HAY <img> DE AVATAR
//
// El prompt pedia `avatar` en cada item. Con eso hacen falta dos cosas que no
// estan: un host de imagenes en `domains` de nuxt.config.ts, y una fuente de
// caras. UAvatar sin `src` cae en unas iniciales sacadas del `alt`, que es lo
// que se ve aqui. El componente queda sin dependencias de red, que en una
// demo de componentes es justo lo que se quiere: sin peticiones que fallen.
//
// ----------------------------------------------------------------------
// POR QUE LAS COPIAS REPES NO SON CONTENIDO DUPLICADO PARA EL LECTOR
//
// UMarquee repite el contenido `repeat` veces porque el bucle de la
// animacion lo necesita. Con la animacion pausada por prefers-reduced-motion
// (el propio Nuxt UI pone `motion-safe:` en la animacion) se ven TODAS juntas,
// una detras de otra.
//
// Sin el `aria-hidden` del marquee, un lector de pantalla anuncia los mismos
// testimonios `repeat` veces seguidas. Con el, el texto de verdad va aparte,
// en la lista `sr-only` de abajo, y se lee una vez.
//
// OJO con el orden: si el marquee NO lleva aria-hidden y la lista sr-only
// tambien esta, esta se lee el doble. Por eso el marquee se silencia y la
// lista manda.
// ======================================================================

export interface MarqueeTestimonial {
  name: string
  role?: string
  text: string
}

withDefaults(defineProps<{
  items: MarqueeTestimonial[]
  /** Segundos que tarda una pasada completa. */
  duration?: number
  /** Repeticiones del contenido seguidas dentro del mismo track. */
  repeat?: number
  /** Marcha en sentido contrario. */
  reverse?: boolean
}>(), {
  duration: 40,
  repeat: 2,
  reverse: true
})

// OJO: aqui NO se calculan las iniciales a mano. UAvatar sin `src` cae en un
// texto que se saca del `alt` (primera letra de cada palabra, dos en total), y
// es justo lo que pinta. Calcularlas en el script y pasarlas seria duplicar
// lo que el componente ya hace, con el riesgo de que las dos cosas se
// desincronicen si un dia cambia el corte.
//
// ======================================================================
// v-motion, Y POR QUE NO SE USA A SECO
//
// @vueuse/motion trae sus propios presets y su propio reduced-motion, pero
// el `initial` hay que declararlo a mano. Y aqui el problema es que un
// fade-in-up sobre una tarjeta que ADEMAS esta dentro de una cinta giratoria
// compite con la propia animacion del marquee: los dos tocan el transform en
// el mismo eje.
//
// Por eso la animacion va solo en la entrada y `y: 0` en el estado final: en
// reposo el transform lo pone el marquee, y si v-motion dejara un transform
// puesto, las tarjetas saldran desplazadas respecto a la cinta.
//
// Y con prefers-reduced-motion la entrada es un no-op: la cinta ya se para
// sola (UMarquee pone motion-safe: en su animacion), y si ademas las tarjetas
// entraran deslizandose, el respeto de la preferencia seria a medias.
// ======================================================================
const prefersReducedMotion = useMediaQuery('(prefers-reduced-motion: reduce)')

const cardMotion = computed(() =>
  prefersReducedMotion.value
    ? { initial: { opacity: 1 }, enter: { opacity: 1 } }
    : { initial: { opacity: 0, y: 20 }, enter: { opacity: 1, y: 0 } }
)
</script>

<template>
  <div>
    <!--
      `aria-hidden` en el UMarquee, no en cada UCard: Marquee no desactiva
      inheritAttrs, asi que el atributo cae en el root y silencia el bloque
      entero de una vez. Ponerlo en cada tarjeta dejaria los espacios entre
      ellas como texto suelto.

      Con el atributo aqui, lo unico que se lee es la lista sr-only de abajo.

      `--duration` va en `style` y no en `:ui`, porque una clase de Tailwind
      armada con una interpolacion la purga el escaner de Tailwind y la
      animacion se queda con los 20s del tema sin avisar. El motivo entero
      esta en MarqueeLogos.vue.
    -->
    <UMarquee
      aria-hidden="true"
      pause-on-hover
      :reverse="reverse"
      :repeat="repeat"
      :ui="{ content: 'gap-3' }"
      :style="{ '--duration': `${duration}s` }"
    >
      <!--
        UCard sin slot header: el avatar y el nombre van dentro del body, en
        una fila propia. El header de UCard es un bloque con su padding y su
        fondo propio, y para una fila de avatar y nombre eso son tres cajas
        para seis palabras.
      -->
      <UCard
        v-for="item in items"
        :key="item.name"
        v-motion="cardMotion"
        :ui="{ root: 'mx-3 w-72 shrink-0 rounded-2xl shadow-md' }"
      >
        <div class="flex items-center gap-3">
          <UAvatar
            :alt="item.name"
            size="sm"
          />

          <div class="min-w-0">
            <p class="text-highlighted truncate text-sm font-medium">
              {{ item.name }}
            </p>

            <p
              v-if="item.role"
              class="text-muted truncate text-xs"
            >
              {{ item.role }}
            </p>
          </div>
        </div>

        <p class="text-muted mt-3 text-sm">
          {{ item.text }}
        </p>
      </UCard>
    </UMarquee>

    <!--
      Los testimonios de verdad, una vez. Se lee esto y no el marquee.
    -->
    <ul class="sr-only">
      <li
        v-for="item in items"
        :key="item.name"
      >
        <span>{{ item.name }}</span><span v-if="item.role">, {{ item.role }}</span>: {{ item.text }}
      </li>
    </ul>
  </div>
</template>
