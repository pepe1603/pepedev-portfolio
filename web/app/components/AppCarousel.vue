<script setup lang="ts">
// La forma de un item del carrusel. Va exportada para que quien lo use
// pueda tipar el array que le pasa, y no tener que repetirlo.
//
// Aqui solo queda lo que el slide dibuja. Antes el slide era una UCard con
// header, body y footer, y por eso la interfaz tenia description, icon y to.
// Al quedar en imagen suelta, esos tres campos no los miraba nadie: dejarlos
// en el tipo invites a escribir datos que no se ven.
export interface CarouselItem {
  image: string
  /** Texto alternativo. Si falta, la imagen usa el title. */
  title: string
  alt?: string
}

// ======================================================================
// AppCarousel: un Carousel de vue3-carousel con los defaults puestos.
//
// Que aporta este envoltorio:
//
//   1. `itemsToShow` como numero, y no como clases de Tailwind.
//
//      Este es el cambio de fondo respecto a UCarousel. Ahi el ancho visible
//      se ponia con basis de Tailwind en el item (`basis-1/3`) y no habia
//      ningun prop para el numero de tarjetas: el ancho lo fijaba el CSS.
//      Aqui el ancho lo pone la biblioteca en JS, dividiendo el ancho del
//      viewport entre las que se ven, y el prop es un numero.
//
//   2. `breakpoints` para el ancho por tamano de pantalla, que es donde
//      vive el responsive. La sintaxis es un objeto cuyo clave es el
//      breakpoint en px y cuyo valor es config parcial:
//
//        breakpoints: { 640: { itemsToShow: 2 }, 1024: { itemsToShow: 3 } }
//
//      OJO con el breakpointMode, que decide contra QUE se mide. Por defecto
//      es 'viewport', o sea que cuenta el ancho de la ventana. Con 'carousel'
//      contaria el ancho del propio carrusel, que dentro de un contenedor
//      estrecho haria que los breakpoints saltaran antes. Para un carrusel
//      normal se quiere el de la ventana: 'viewport'.
//
//   3. `autoplay` como numero de milisegundos, no como objeto.
//
//      Otra diferencia con UCarousel, que tomaba `:autoplay="{ delay }"` por
//      venir de Embla. Aqui es `:autoplay="3000"` a pelo, y para apagarlo se
//      pone a 0. Un objeto ahi no falla: se multiplica y da NaN, y el
//      carrusel se queda parado.
//
//   4. La altura. Por defecto la biblioteca pone height: 'auto', que
//      funciona. Pero los slides se miden y se reparten con
//      getBoundingClientRect, asi que `height: 'auto'` es lo que hay que
//      dejar: pasarlo a un px fijo obligaria a saber de antemano cuanto
//      mide una imagen, y `aspect-video` en la NuxtImg ya hace que todas
//      midan igual sin depender del ancho de la foto.
//
//      Ese aspect-video tambien es lo que hace que las tarjetas midan lo
//      mismo entre si cuando hay itemsToShow 3. Sin el, cada slide tomaba la
//      altura de su foto y el carrusel se veia escalonado.
withDefaults(defineProps<{
  items: CarouselItem[]
  /**
   * Cuantas tarjetas se ven a la vez en el ancho mas pequeno. El responsive
   * va en `breakpoints`, no aqui: el prop es el valor base.
   */
  itemsToShow?: number
  /**
   * Ancho por breakpoint, en px de ventana. Cada valor es config parcial del
   * carrusel. Ejemplo: `{ 640: { itemsToShow: 2 }, 1024: { itemsToShow: 3 } }`
   */
  breakpoints?: Record<number, { itemsToShow: number }>
  /** Milisegundos entre tarjeta y tarjeta. 0 lo apaga. */
  interval?: number
  /** Flechas de anterior y siguiente. */
  arrows?: boolean
  /** Puntos de navegacion. */
  dots?: boolean
  /** El autoplay se para al pasar el raton. */
  pauseOnHover?: boolean
  /** Separacion entre tarjetas, en px. */
  gap?: number
}>(), {
  // Una tarjeta entera a la vez: el clasico. Quien quiera ver mas, lo dice.
  itemsToShow: 1,
  interval: 3000,
  arrows: true,
  dots: true,
  pauseOnHover: true,
  gap: 16
})
</script>

<template>
  <!--
    La biblioteca trae su CSS en dist/carousel.css y el modulo lo anade solo a
    la lista global, asi que no hay que importarlo aqui ni en nuxt.config.

    NO se pone nada de estilo en el .carousel: sus clases ya traen
    position: relative, y la posicion de las flechas y los puntos se calcula
    respecto a esa caja. Un transform o un filter aqui los moveria de sitio.
  -->
  <Carousel
    :items-to-show="itemsToShow"
    :breakpoints="breakpoints"
    :wrap-around="true"
    :autoplay="interval"
    :pause-autoplay-on-hover="pauseOnHover"
    :gap="gap"
    :aria-label="`Carrusel de ${items.length} elementos`"
  >
    <!--
      El slot default devuelve UN Slide por item y nada mas: la biblioteca
      mete sus hijos entre viewport y addons con un array plano, asi que
      cualquier otro elemento aqui acaba dentro del track, como si fuera una
      tarjeta mas, y se cuenta como un slide mas en la paginacion.

      Las flechas y los puntos van en su propio slot #addons, que es el
      unico que la biblioteca coloca por encima del track. Con Navigation y
      Pagination sin template dentro, cada uno dibuja sus botones con el
      icono SVG que trae la propia libreria: los --vc-* de su CSS ya los
      posicionan respecto al .carousel, y un boton propio con las clases
      carousel__next tendria que replicar a mano ese posicionamiento.
    -->
    <Slide
      v-for="(item, index) in items"
      :key="item.image"
    >
      <!--
        aqui no hay UCard. Una tarjeta con header y body dentro de un slide
        pone tres cajas (el .card, el header y el body) dentro de otra caja que
        ya es el viewport del carrusel, y el resultado son tres bordes y tres
        niveles de padding para una sola foto. La imagen va suelta, con sus
        propios bordes.

        Y sin link envolviendo: la tarjeta era pulsable por el boton "Ver" del
        footer, no por la imagen. Al quitar el footer no queda nada que
        pulsable, y una imagen con alt que parece clicable sin estar enlazada
        es peor que una que no lo parece. Si alguna vez hace falta navegar,
        el sitio es un NuxtLink, y entonces si es entera.

        El alt NO es opcional: sin texto alternativo el carrusel es una tira
        de imagenes sin nombre para un lector de pantalla.
      -->
      <NuxtImg
        :src="item.image"
        :alt="item.alt || item.title"
        :loading="index === 0 ? 'eager' : 'lazy'"
        class="aspect-video w-full rounded-xl object-cover"
      />
    </Slide>

    <!--
      Un solo template #addons para los dos. Dos templates con el mismo nombre
      de slot es un error de Vue, no una concatenacion: el segundo sobrescribe
      al primero y las flechas desaparecen segun el orden de compilacion.
      Adentro si puede haber v-if, uno por cada hijo.
    -->
    <template #addons>
      <Navigation v-if="arrows" />
      <Pagination v-if="dots" />
    </template>
  </Carousel>
</template>
