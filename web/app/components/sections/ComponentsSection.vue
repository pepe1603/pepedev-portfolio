<script setup lang="ts">
const buttons = [
  { color: 'primary', variant: 'solid', label: 'Primary' },
  { color: 'primary', variant: 'outline', label: 'Outline' },
  { color: 'neutral', variant: 'subtle', label: 'Subtle' },
  { color: 'neutral', variant: 'ghost', label: 'Ghost' }
] as const

const features = [
  {
    icon: 'i-lucide-palette',
    title: 'Leen los mismos tokens',
    description: 'UButton, UBadge o UCard no necesitan saber nada del tema: resuelven --ui-primary y --ui-bg por su cuenta.'
  },
  {
    icon: 'i-lucide-monitor-cog',
    title: 'El tema es una variable',
    description: 'Light y dark solo cambian el valor de los tokens en main.css. Ningun componente declara un color literal.'
  },
  {
    icon: 'i-lucide-box',
    title: 'Theme por componente',
    description: 'Cada slot acepta `ui` para ajustar clases sin reescribir el componente ni usar deep selectors.'
  }
]
</script>

<template>
  <UPageSection
    title="Componentes Nuxt UI"
    description="Los componentes de Nuxt UI leen estos mismos tokens, así que heredan el tema sin configuración adicional."
    orientation="horizontal"
    reverse
    :features="features"
  >
    <div class="flex flex-wrap items-center gap-3">
      <UButton
        v-for="button in buttons"
        :key="button.label"
        :color="button.color"
        :variant="button.variant"
        :label="button.label"
      />

      <UBadge
        color="primary"
        variant="subtle"
        label="Badge"
      />
    </div>
  </UPageSection>
</template>
