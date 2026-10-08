<script setup lang="ts">
// ======================================================================
// MarqueeSection: los dos marquee y uno vertical de regalo.
//
// UMarquee es de Nuxt UI y ya resuelve lo dificil de una cinta infinita:
// repite el contenido, anima con CSS puro y pone `motion-safe:` en la
// animacion, asi que con prefers-reduced-motion se para sola. Sin eso, una
// cinta que no para es justo lo que el aviso 4 de main.css prohibe.
//
// OJO con la seccion: lleva `overflow-hidden` porque los degradados de
// UMarquee (los `before:`/`after:` que difuminan los extremos) se salen de la
// caja. Sin esto aparece una barra de scroll horizontal en toda la pagina.
//
// Y NO va dentro de un RevealOnScroll con `blur`: un filter crea bloque
// contenedor para los fixed y ademas el observer mide mal las medidas si hay
// un ancestro transformado. Las dos animaciones de aqui (la cinta y el
// fade-in-up de las tarjetas) se sueltan solas, asi que la seccion no
// necesita revelarse.
// ======================================================================

import type { MarqueeLogo } from '~/components/MarqueeLogos.vue'
import type { MarqueeTestimonial } from '~/components/MarqueeCards.vue'

// Logos de mentira, dibujados: cada uno es un path con su tinta.
//
// Los tonos van todos en el mismo rango (40-60 de luminosidad) para que el
// gris al que los baja `grayscale` sea parecida en todos. Si uno fuera
// clarito, se veria casi transparente al pasar a grayscale y el hover
// pareceria un fallo de ese logo y no del efecto.
const logos: MarqueeLogo[] = [
  {
    name: 'Vectorlab',
    path: 'M12 2 22 20H2L12 2Z',
    fill: 'oklch(0.55 0.16 250)'
  },
  {
    name: 'Nordika',
    path: 'M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20Z',
    fill: 'oklch(0.55 0.14 200)'
  },
  {
    name: 'Cuadrado',
    path: 'M3 3h18v18H3V3Z',
    fill: 'oklch(0.5 0.12 160)'
  },
  {
    name: 'Aguja',
    path: 'M13 2 4 14h7l-1 8 9-12h-7l1-8Z',
    fill: 'oklch(0.55 0.18 30)'
  },
  {
    name: 'Hexia',
    path: 'M12 1.5 21 7v10l-9 5.5L3 17V7l9-5.5Z',
    fill: 'oklch(0.5 0.15 300)'
  },
  {
    name: 'Radia',
    path: 'M12 2a3 3 0 0 1 3 3v7h7a3 3 0 0 1 0 6h-7v7a3 3 0 0 1-6 0v-7H2a3 3 0 0 1 0-6h7V5a3 3 0 0 1 3-3Z',
    fill: 'oklch(0.52 0.17 90)'
  }
]

const testimonials: MarqueeTestimonial[] = [
  {
    name: 'Marta Ruiz',
    role: 'Diseño, Norte',
    text: 'La paleta por tokens cambio el dia que dejamos de escribir colores a mano. Los dos temas quedaron alineados sin tocar el codigo.'
  },
  {
    name: 'Diego Alonso',
    role: 'Front-end, Kilometro',
    text: 'Los componentes con los defaults puestos ahorran mas de lo que parecen: cambiar el padding de un boton en un solo sitio es medio proyecto que no existe.'
  },
  {
    name: 'Lucia Ferran',
    role: 'Producto, Ámbar',
    text: 'Lo de poder ver cada comportamiento en la misma pagina, con su codigo al lado, ahorro mas documentacion de la que parece.'
  },
  {
    name: 'Nico Beltran',
    role: 'Sistemas, Faro',
    text: 'Los avisos en los comentarios son lo mejor del repositorio. Explican el porque, no el que, y con eso se deja de romper lo que ya funcionaba.'
  },
  {
    name: 'Sara Vidal',
    role: 'Front-end, Tramo',
    text: 'El reveal on scroll con once para lo que esta por encima del fold, y sin once para el resto. Son dos lineas y evitan la sensacion de carga.'
  }
]

// La lista vertical comparte componente y solo cambia orientation. Sirve para
// ver que el prop existe y que el degradado del overlay gira con el.
const verticalLogos: MarqueeLogo[] = logos.slice(0, 4)
</script>

<template>
  <UPageSection
    title="Marquee"
    description="Cintas de movimiento continuo. El componente repite el contenido, anima con CSS y se para solo con prefers-reduced-motion."
  >
    <div class="flex flex-col gap-10 overflow-hidden">
      <div class="flex flex-col gap-3">
        <p class="text-muted text-sm">
          Logos en escala de grises que recuperan el color al pasar por encima.
          La pausa viene de <code class="text-primary">pause-on-hover</code>, y la
          velocidad de <code class="text-primary">[--duration:35s]</code>.
        </p>

        <MarqueeLogos :logos="logos" />
      </div>

      <div class="flex flex-col gap-3">
        <p class="text-muted text-sm">
          Testimonios en sentido contrario, con
          <code class="text-primary">reverse</code>. Las tarjetas usan
          <code class="text-primary">v-motion</code> de
          <code class="text-primary">@vueuse/motion</code>, que ya estaba instalado
          para la sección de motion.
        </p>

        <MarqueeCards :items="testimonials" />
      </div>

      <div class="flex flex-col gap-3">
        <p class="text-muted text-sm">
          Y el mismo componente en vertical, con
          <code class="text-primary">orientation="vertical"</code>. El degradado
          de los extremos gira con el eje.
        </p>

        <MarqueeLogos
          :logos="verticalLogos"
          orientation="vertical"
          :duration="25"
          class="h-40"
        />
      </div>
    </div>
  </UPageSection>
</template>
