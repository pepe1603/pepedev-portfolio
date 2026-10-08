<script setup lang="ts">
// ======================================================================
// PAGINA DE EJEMPLO: CARRUSEL
// El carrusel vive en components/AppCarousel.vue. Esta pagina lo monta dos
// veces para comparar los dos anchos utiles.
//
// --------------------------------------------------------------------------
// LO QUE NO SE PUEDE PONER EN UN CARRUSEL
//
// Un carrusel con texto que hay que leer no se lee: lo unico que se puede
// leer con comodidad es lo que esta quieto. Por eso las tarjetas de aqui
// llevan el titulo corto y la descripcion de dos lineas, y el detalle
// entero vive en el destino del boton.
//
// Y hay una trampa de accesibilidad que no tiene arreglo con CSS: si el
// carrusel mueve solo, las tarjetas que no estan visibles siguen siendo
// tabulables y el foco se va a un elemento que nadie ve. Por eso
// pauseOnHover va activado, para que el raton pare el avance, y para
// que el contenido importante no dependa de que el visitante lo vea por
// azar.
useSeoMeta({
  title: 'Carrusel',
  description: 'Carrusel de vue3-carousel, solo imagenes. Cuantas se ven a la vez lo decide itemsToShow.'
})

// Las imagenes de picsum van con su host en la lista de domains de
// nuxt.config.ts, sin lo cual ipx las devuelve sin recortar y sin avif.
// Imagenes de la galeria. Los hosts van en `domains` de nuxt.config.ts:
// sin eso, ipx deja pasar la URL sin recortar ni convertir y no avisa.
const gallery = [
  'https://picsum.photos/id/1015/1600/900',
  'https://picsum.photos/id/1016/1600/900',
  'https://picsum.photos/id/1018/1600/900',
  'https://picsum.photos/id/1019/1600/900',
  'https://picsum.photos/id/1024/1600/900',
  'https://picsum.photos/id/1036/1600/900',
  'https://picsum.photos/id/1043/1600/900',
  'https://picsum.photos/id/1050/1600/900'
]

const slides = [
  {
    image: 'https://picsum.photos/id/1015/1200/800',
    title: 'Rio entre montanas',
    alt: 'Rio caudaloso entre laderas rocosas'
  },
  {
    image: 'https://picsum.photos/id/1016/1200/800',
    title: 'Caminos de tierra',
    alt: 'Senda de tierra entre vegetacion'
  },
  {
    image: 'https://picsum.photos/id/1018/1200/800',
    title: 'Valle con niebla',
    alt: 'Valle cubierto de niebla al amanecer'
  },
  {
    image: 'https://picsum.photos/id/1019/1200/800',
    title: 'Costa recortada',
    alt: 'Acantilados junto al mar'
  },
  {
    image: 'https://picsum.photos/id/1024/1200/800',
    title: 'Lobo en la nieve',
    alt: 'Lobo blanco sobre nieve profunda'
  },
  {
    image: 'https://picsum.photos/id/1039/1200/800',
    title: 'Cascada entre rocas',
    alt: 'Cascada cayendo entre rocas oscuras'
  }
]
</script>

<template>
  <UPage>
    <!--
      El header va con animation="fade" y once: esta por encima del fold,
      asi que el observer dispara en el primer frame y cualquier
      desplazamiento se lee como una carga, no como una entrada. `fade` no
      mueve nada, solo cambia la opacidad.

      Y NO lleva HeroSection aqui, aunque lo lleve la portada: sus enlaces
      apuntan a #texto, que es una seccion del indice. En /carrusel ese ancla
      no lleva a ninguna parte.

      Y NO lleva UPageSection alrededor. UPageSection envuelve su contenido en
      un UContainer, y aqui ya hay uno: el del layout. Meterlo dentro seria
      el doble centrado, con el px del contenedor duplicado y el contenido
      re-centrado dentro de una caja que ya venia centrada. Para esto
      UPageHeader va directo, porque no necesita ancho: hereda el del layout.
    -->
    <RevealOnScroll
      animation="fade"
      once
    >
      <UPageHeader
        title="Carrusel"
        description="Cuántas tarjetas se ven a la vez es un número, itemsToShow, y el ancho lo calcula la biblioteca. El responsive va aparte, en breakpoints."
      />
    </RevealOnScroll>

    <UPageBody>
      <div class="flex flex-col gap-10">
        <!--
          Cada seccion va en su propio RevealOnScroll, y no el contenedor
          entero, por una razon concreta: si el wrapper fuera el div de las
          gap-10, los dos carruseles saldrian a la vez al llegar la
          animacion. Envolviendo cada bloque, cada uno entra cuando llega.

          Y sin `blur` alrededor: blur deja filter en el estado visible y
          un filter crea bloque contenedor para los fixed. El carrusel mide
          su posicion con el observer de Embla, que mide del getBoundingClientRects
          al padre, y con un ancestro transformado las medidas salen rareadas.
        -->
        <RevealOnScroll animation="fade-up">
          <section class="flex flex-col gap-4">
            <div class="flex flex-col gap-1">
              <h2 class="text-highlighted font-semibold">
                Una tarjeta a la vez
              </h2>

              <p class="text-muted text-sm">
                El default del wrapper es
                <code class="text-primary">itemsToShow: 1</code>, y con eso
                ya sale un carrusel clásico de una tarjeta entera.
              </p>
            </div>

            <AppCarousel
              :items="slides"
              :interval="4000"
            />
          </section>
        </RevealOnScroll>

        <RevealOnScroll animation="zoom-in">
          <section class="flex flex-col gap-4">
            <div class="flex flex-col gap-1">
              <h2 class="text-highlighted font-semibold">
                Tres a la vez en escritorio
              </h2>

              <p class="text-muted text-sm">
                Con
                <code class="text-primary">:breakpoints="{ 640: { itemsToShow: 2 }, 1024: { itemsToShow: 3 } }"</code>.
                El ancho lo calcula la biblioteca dividiendo el viewport entre
                las que se ven, así que aquí no hay clases de Tailwind: se
                escribe el número.
              </p>
            </div>

            <AppCarousel
              :items="slides"
              :breakpoints="{ 640: { itemsToShow: 2 }, 1024: { itemsToShow: 3 } }"
              :interval="3000"
            />
          </section>
        </RevealOnScroll>

        <RevealOnScroll animation="fade-up">
          <UAlert
            color="neutral"
            variant="subtle"
            icon="i-lucide-info"
            title="Dónde está cada cosa"
            description="Cuántas tarjetas se ven, en itemsToShow, y el responsive en breakpoints. El ritmo, en interval, que aquí es el autoplay en milisegundos y se apaga con 0. Las flechas y los puntos, en arrows y dots. El CSS lo añade el módulo solo: no se importa a mano."
          />
        </RevealOnScroll>

        <!--
          Galeria con tira de miniaturas. Sin `blur` en el RevealOnScroll:
          un filter crea bloque contenedor para los fixed, y las flechas de
          la galeria son absolute respecto al .carousel, asi que en un
          ancestro con filter se posicionarian mal. `fade-up` no deja filter.
        -->
        <RevealOnScroll animation="fade-up">
          <section class="flex flex-col gap-4">
            <div class="flex flex-col gap-1">
              <h2 class="text-highlighted font-semibold">
                Galería con miniaturas
              </h2>

              <p class="text-muted text-sm">
                Dos
                <code class="text-primary">Carousel</code>
                sobre el mismo índice. La imagen grande escribe el estado al
                moverse y las miniaturas lo leen; al pulsar una miniatura se
                llama
                <code class="text-primary">slideTo</code>
                sobre la grande.
              </p>
            </div>

            <!--
              ClientOnly porque el carrusel mide con getBoundingClientRect y
              en el servidor no hay nada que medir: sin esto sale un hueco
              de altura cero y luego salta. La altura la fija el ratio
              aspect-video, que es lo que evita ese hueco en cliente.
            -->
            <ClientOnly>
              <GalleryCarousel
                :images="gallery"
                alt="Paisaje"
              />

              <template #fallback>
                <div class="aspect-video w-full rounded-2xl bg-elevated" />
              </template>
            </ClientOnly>
          </section>
        </RevealOnScroll>
      </div>
    </UPageBody>
  </UPage>
</template>
