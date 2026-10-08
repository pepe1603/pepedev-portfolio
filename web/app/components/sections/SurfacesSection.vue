<script setup lang="ts">
// Superficies de la pagina hacia el elemento mas elevado. El orden de
// luminosidad es intencionado: cambiarlo rompe el contraste del texto
// que se apoya encima.
const surfaces = [
  { class: 'bg-muted border border-default', token: 'bg-muted', note: 'rejillas y zonas secundarias' },
  { class: 'bg-elevated border border-default', token: 'bg-elevated', note: 'tarjetas sobre la superficie' },
  { class: 'bg-accented', token: 'bg-accented', note: 'hover y estados activos' },
  { class: 'bg-inverted text-inverted', token: 'bg-inverted', note: 'bloques de máximo contraste' }
]
</script>

<template>
  <UPageSection
    title="Superficies"
    description="Cuatro niveles de fondo. El invertido invierte también el texto, por eso lleva su propio par."
  >
    <div class="grid gap-3 sm:grid-cols-2">
      <div
        v-for="surface in surfaces"
        :key="surface.token"
        :class="surface.class"
        class="rounded-lg p-4"
      >
        <p class="font-mono text-sm">
          {{ surface.token }}
        </p>

        <p class="opacity-70 text-sm">
          {{ surface.note }}
        </p>
      </div>
    </div>
  </UPageSection>
</template>
