<script setup lang="ts">
// ======================================================================
// TITULO CON GRADIENTE DE MARCA
// El texto se recorta sobre el degradado, asi que necesita las dos piezas
// juntas: text-transparent (si no, el color del texto tapa el fondo) y
// bg-clip-text (recorta el fondo a la forma de las letras).
//
// El degradado vive en @utility text-gradient (assets/css/main.css), que
// ya lleva dentro background-clip y el color transparente. Aqui se repiten
// por si el componente se usa fuera de la plantilla, y son idempotentes.
//
// El degradado arranca en --ui-primary, el alias que Nuxt UI cambia con el
// tema (500 en light, 400 en dark), y termina en --ui-gradient-to, que
// --ui-primary redefine a 400 y 300 respectivamente. Asi el degradado si
// cambia entre light y dark, y cambiar ui.colors.primary en app.config.ts
// lo reescribe entero sin tocar aqui.
// ======================================================================

type GradientTitleSize = 'sm' | 'md' | 'lg' | 'xl'

withDefaults(
  defineProps<{
    /** `span` es lo que hay que usar dentro de un slot de titulo que ya
     *  trae su propio elemento, como el <h1> de UPageHero. */
    as?: 'h1' | 'h2' | 'h3' | 'h4' | 'h5' | 'h6' | 'span'
    size?: GradientTitleSize
    align?: 'left' | 'center'
  }>(),
  {
    as: 'h1',
    size: 'lg',
    align: 'center'
  }
)

const sizeClass: Record<GradientTitleSize, string> = {
  sm: 'text-2xl sm:text-3xl',
  md: 'text-3xl sm:text-4xl',
  lg: 'text-4xl sm:text-5xl md:text-6xl',
  xl: 'text-5xl sm:text-6xl md:text-7xl'
}
</script>

<template>
  <component
    :is="as"
    :class="[
      'text-gradient font-extrabold tracking-tight text-balance',
      sizeClass[size],
      align === 'center' ? 'text-center' : 'text-left'
    ]"
  >
    <slot />
  </component>
</template>
