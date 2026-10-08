<script setup lang="ts">
// Demo de entrar y salir. once=false (el default) hace que cada bloque
// reaparezca cada vez que vuelve a entrar en pantalla, asi que hay que
// bajar, subir y bajar otra vez para verlo.
//
// Los bloques son altos a proposito: un elemento mas pequeño que el viewport
// se ve entrar y salir pegado al borde, que no demuestra nada. Con bloques
// de min-h-72 la entrada y la salida quedan separadas y se aprecian.
const blocks = [
  {
    animation: 'fade-up',
    easing: 'soft',
    icon: 'i-lucide-download',
    title: 'Entra desde abajo',
    text: 'Baja hasta que este bloque asoma por el borde inferior. Sube y se va; vuelve a bajar y regresa.'
  },
  {
    animation: 'from-left',
    easing: 'soft',
    icon: 'i-lucide-move-horizontal',
    title: 'Entra desde la izquierda',
    text: 'Llega desplazándose en horizontal. Cada pasada por la sección lo repite desde el principio.'
  },
  {
    animation: 'zoom-out',
    easing: 'soft',
    icon: 'i-lucide-maximize',
    title: 'Se acerca al sitio',
    text: 'Empieza un 10% más grande y se asienta. Útil para tarjetas que quieres que destaquen al entrar.'
  },
  {
    animation: 'blur',
    easing: 'soft',
    icon: 'i-lucide-focus',
    title: 'Entra desenfocando',
    text: 'El desenfoque es el único animation que deja un filter en el estado visible. Ojo con meterle un fixed dentro.'
  },
  {
    animation: 'fade-down',
    easing: 'back',
    // AVISO: el icono NO es `i-lucide-bounce`. Ese es de Feather, no de Lucide,
    // y no existe en la coleccion: @nuxt/icon lo resolvia con un warning en
    // cada render de la pagina. Lucide tiene `arrow-down-up`.
    icon: 'i-lucide-arrow-down-up',
    title: 'Con curva back',
    text: 'La curva cubic-bezier(0.34,1.4,0.64,1) se pasa de su destino y vuelve. Se nota al entrar, no al salir.'
  }
] as const

const ui = {
  container: 'py-24 sm:py-32 lg:py-40'
}
</script>

<template>
  <UPageSection
    title="Entrar y salir"
    description="Con once=false —el default— el bloque se oculta al dejar la pantalla y vuelve a animarse cada vez que regresa. Baja, sube y vuelve a bajar para verlo."
    :ui="ui"
  >
    <div class="flex flex-col gap-6">
      <RevealOnScroll
        v-for="(block, index) in blocks"
        :key="block.title"
        :animation="block.animation"
        :easing="block.easing"
        :duration="750"
        :delay="index * 60"
      >
        <div class="flex min-h-72 flex-col items-start justify-center gap-3 rounded-xl bg-muted border border-default p-8">
          <div class="bg-primary/10 text-primary flex size-11 items-center justify-center rounded-lg">
            <UIcon
              :name="block.icon"
              class="size-5"
            />
          </div>

          <h3 class="text-highlighted text-xl font-semibold">
            {{ block.title }}
          </h3>

          <p class="text-muted max-w-lg">
            {{ block.text }}
          </p>
        </div>
      </RevealOnScroll>
    </div>
  </UPageSection>
</template>
