<script setup lang="ts">
// Galeria de las 8 animaciones disponibles. Cada tarjeta usa la suya, asi
// que hay que recorrer la seccion para verlas todas. El retardo crece con
// el indice para que la fila entre en cascada y no de golpe.
//
// Los valores van marcados con `as const` a proposito: si alguien anade una
// animacion que no existe en RevealAnimation, el typecheck falla aqui en
// lugar de fallar en silencio en el DOM.
const animations = [
  { value: 'fade', motion: 'sin desplazamiento', timing: '700ms · soft' },
  { value: 'fade-up', motion: 'translate-y-14 → 0', timing: '700ms · soft' },
  { value: 'fade-down', motion: '-translate-y-14 → 0', timing: '700ms · soft' },
  { value: 'from-left', motion: '-translate-x-14 → 0', timing: '700ms · soft' },
  { value: 'from-right', motion: 'translate-x-14 → 0', timing: '700ms · soft' },
  { value: 'zoom-in', motion: 'scale-90 → 100', timing: '700ms · soft' },
  { value: 'zoom-out', motion: 'scale-110 → 100', timing: '700ms · soft' },
  { value: 'blur', motion: 'blur-sm + scale-105 → 0', timing: '700ms · soft' }
] as const

const icons = [
  'i-lucide-eye-off',
  'i-lucide-arrow-up',
  'i-lucide-arrow-down',
  'i-lucide-arrow-right',
  'i-lucide-arrow-left',
  'i-lucide-minus',
  'i-lucide-plus',
  'i-lucide-scan-eye'
] as const

// Seccion alta a proposito: sin recorrido no hay forma de apreciar la
// animacion, y el retardo por indice solo se lee en movimiento.
const ui = {
  container: 'py-24 sm:py-32 lg:py-40'
}
</script>

<template>
  <UPageSection
    title="Las ocho animaciones"
    description="Cada tarjeta se revela con una animación distinta. Cambia el valor de animation y el bloque se comporta distinto sin tocar una sola regla de IntersectionObserver."
    :ui="ui"
  >
    <RevealOnScroll
      animation="fade-down"
      :duration="600"
    >
      <div class="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <RevealOnScroll
          v-for="(animation, index) in animations"
          :key="animation.value"
          :animation="animation.value"
          :delay="(index % 4) * 110"
          :duration="650"
        >
          <div class="flex h-full flex-col gap-4 rounded-xl bg-elevated border border-default p-5">
            <div class="flex items-center gap-2">
              <UIcon
                :name="icons[index]"
                class="text-primary size-4 shrink-0"
              />

              <code class="text-sm font-semibold">
                {{ animation.value }}
              </code>
            </div>

            <div class="flex flex-1 items-center justify-center rounded-lg bg-muted border border-dashed border-accented py-10">
              <UIcon
                :name="icons[index]"
                class="text-primary/40 size-8"
              />
            </div>

            <div class="flex flex-col gap-0.5">
              <code class="text-toned text-xs">
                {{ animation.motion }}
              </code>

              <span class="text-dimmed text-xs">
                {{ animation.timing }}
              </span>
            </div>
          </div>
        </RevealOnScroll>
      </div>
    </RevealOnScroll>
  </UPageSection>
</template>
