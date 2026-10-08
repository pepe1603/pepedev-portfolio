<script setup lang="ts">
// ======================================================================
// MarqueeLogos: tira de logos en movimiento continuo.
//
// ----------------------------------------------------------------------
// POR QUE LOS LOGOS SON SVG EN LINEA Y NO <img>
//
// El prompt original pedia un array de URLs y una <img>. Con URLs hace
// falta lo de siempre: anadir el host a `domains` de nuxt.config.ts, y sin
// eso ipx deja pasar la URL sin recortar. Y los logos de verdad son SVG, que
// ipx no convierte.
//
// Ademas el gris no se veria. `grayscale` es un filtro de saturacion: sobre
// una foto RGB se nota, pero sobre un SVG de una sola tinta el resultado es el
// mismo pixel que ya era. Para que la transicion grayscale -> normal se vea
// tiene que haber VARIAS tintas.
//
// Por eso cada logo trae su propio `path` con su `fill`, y el componente
// pinta el SVG. El `path` es un string, o sea que el array sigue siendo
// serializable y se puede pasar por props sin montar nada.
//
// ----------------------------------------------------------------------
// POR QUE LA VELOCIDAD VA EN `style` Y NO EN `:ui`
//
// El prompt original pedia `:ui="{ root: '[--duration:35s]' }"`. Con un valor
// fijo funciona. En cuanto la velocidad es un prop, hay que meter el valor en
// el string por interpolacion, y Tailwind no lo ve: escanea el codigo fuente
// buscando CLASES LITERALES, y `[--duration:${duration}s]` no aparece
// escrito en ningun sitio, asi que la purga.
//
// El sintoma es silencioso y malo: la animacion sale, pero con la velocidad
// por defecto de 20s del tema. Ni error, ni aviso, ni clase en el CSS.
//
// Asi que el prop va en `:style`, que escribe la custom property en el
// elemento sin pasar por Tailwind. `--duration` es justo lo que lee el tema
// de Nuxt UI en su `animation: marquee var(--duration) linear infinite`.
//
// El prop `duration` NO es un parche para esto: es que el componente tenga
// velocidad configurable, que es lo que pedia el prompt.
// ======================================================================

export interface MarqueeLogo {
  /** Nombre de la marca. Va en el texto para lectores de pantalla. */
  name: string
  /** Path SVG dentro de un viewBox 24x24. */
  path: string
  /** Tinta del logo. Es lo que el grayscale se come. */
  fill: string
}

const props = withDefaults(defineProps<{
  logos: MarqueeLogo[]
  /** Segundos que tarda una pasada completa. 0 o menos lo para. */
  duration?: number
  /** Repeticiones del contenido seguidas dentro del mismo track. */
  repeat?: number
  /** Marcha en sentido contrario. */
  reverse?: boolean
  /**
   * Eje del desplazamiento. En vertical el degradado de los extremos gira: el
   * `before:`/`after:` de UMarquee pasan de estar a los lados a estar arriba y
   * abajo, y la animacion pasa a ser `marquee-vertical`.
   *
   * OJO: en vertical hay que darle ALTO al contenedor. El texto se sale por
   * abajo con toda la caja vacia encima si no, porque lo que se mueve es el
   * contenido, no el root.
   */
  orientation?: 'horizontal' | 'vertical'
}>(), {
  duration: 35,
  repeat: 2,
  reverse: false,
  orientation: 'horizontal'
})

// El texto real de los logos, una sola vez, para lectores de pantalla.
//
// UMarquee pinta el contenido `repeat` veces para poder hacer el bucle sin
// salto. Esas copias NO son decorativas: son las mismas tarjetas repetidas, y
// con la animacion pausada por prefers-reduced-motion se quedan quietas y
// visibles a la vez.
//
// O sea, sin esto un lector de pantalla oye la lista entera cuatro veces. La
// animacion se puede silenciar con `aria-hidden` en el marquee, pero los logos
// no son decorativos: son contenido, y callarlos todos dejaria una seccion de
// marcas vacia. Por eso el marquee se marca y la lista se ofrece aparte.
const labels = computed(() => props.logos.map(logo => logo.name).join(', '))
</script>

<template>
  <div>
    <!--
      `aria-hidden` por el mismo motivo que en MarqueeCards: el contenido va
      repetido `repeat` veces, y sin esto los nombres se anuncian repetidos.

      El atributo cae en el root porque Marquee no desactiva inheritAttrs. Y
      los SVG se quedan sin role ni aria-label: con el bloque silenciado, un
      aria-label por logo no lo lee nadie, y el nombre va en la lista de
      abajo, que se lee una vez y en orden.
    -->
    <UMarquee
      aria-hidden="true"
      pause-on-hover
      :reverse="reverse"
      :repeat="repeat"
      :orientation="orientation"
      :ui="{
        root: 'h-full',
        content: orientation === 'vertical'
          ? 'gap-0 flex-col items-center justify-start'
          : 'gap-8'
      }"
      :style="{ '--duration': `${duration}s` }"
    >
      <div
        v-for="logo in logos"
        :key="logo.name"
        class="mx-8 flex shrink-0 items-center"
        :class="orientation === 'vertical' ? 'my-4' : ''"
      >
        <svg
          viewBox="0 0 24 24"
          class="h-10 w-auto opacity-70 grayscale transition-all duration-300 hover:opacity-100 hover:grayscale-0"
          :style="{ color: logo.fill }"
        >
          <path
            :d="logo.path"
            fill="currentColor"
          />
        </svg>
      </div>
    </UMarquee>

    <!--
      El texto que el marquee no puede dar. Va en `sr-only`, asi que no se ve
      pero se lee: si el marquee esta pausado, esto es lo que queda.
    -->
    <p class="sr-only">
      {{ labels }}
    </p>
  </div>
</template>
