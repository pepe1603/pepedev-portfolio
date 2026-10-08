<script setup lang="ts">
// Documentacion de la API. Los cuatro bloques escalonados usan el mismo
// animation con retardo creciente, que es el caso de uso mas comun.
const features = [
  {
    icon: 'i-lucide-eye',
    title: 'useIntersectionObserver',
    description: 'Devuelve un booleano. No sabe nada de estilos: solo decide si el bloque ya entró en pantalla.'
  },
  {
    icon: 'i-lucide-spline',
    title: 'Transición CSS',
    description: 'opacity, translate, scale y timing salen de utilidades de Tailwind. Duración y retardo llegan por style inline.'
  },
  {
    icon: 'i-lucide-repeat',
    title: 'once',
    description: 'false (default) hace que el bloque reaparezca cada vez que vuelve a entrar. true lo deja fijo en su sitio.'
  },
  {
    icon: 'i-lucide-accessibility',
    title: 'Accesibilidad',
    description: 'motion-reduce anula translate, scale y blur sin tocar la lógica, así que no hace falta leer la media query en JS.'
  }
]

const links = [
  { label: 'Ver el código', to: 'https://github.com', target: '_blank' as const, icon: 'i-simple-icons-github' }
]

const ui = {
  container: 'py-24 sm:py-32 lg:py-40'
}
</script>

<template>
  <UPageSection
    title="RevealOnScroll"
    description="VueUse decide cuándo, Tailwind decide cómo. Ninguna de las dos capas conoce a la otra."
    :features="features"
    :links="links"
    :ui="ui"
  >
    <ul class="flex flex-col gap-2">
      <li
        v-for="(item, index) in features"
        :key="item.title"
      >
        <RevealOnScroll
          animation="from-right"
          :delay="index * 120"
          :duration="600"
        >
          <div class="flex items-center gap-3 rounded-lg bg-muted border border-default px-4 py-3">
            <UIcon
              :name="item.icon"
              class="text-primary size-4 shrink-0"
            />

            <span class="text-sm">{{ item.title }}</span>

            <code class="text-dimmed ml-auto text-xs">delay {{ index * 120 }}ms</code>
          </div>
        </RevealOnScroll>
      </li>
    </ul>
  </UPageSection>
</template>
