<script setup lang="ts">
// Escala de emphasis de texto, de mayor a menor. Cada entrada documenta el
// token que la produce para poder cambiarla desde main.css.
const scale = [
  { class: 'text-highlighted', label: 'text-highlighted', note: 'máxima jerarquía' },
  { class: 'text-default', label: 'text-default', note: 'cuerpo de texto' },
  { class: 'text-toned', label: 'text-toned', note: 'énfasis dentro de una frase' },
  { class: 'text-muted', label: 'text-muted', note: 'descripciones y texto secundario' },
  { class: 'text-dimmed text-sm', label: 'text-dimmed', note: 'metadatos y notas al pie' },
  { class: 'text-primary', label: 'text-primary', note: 'enlaces y llamadas a la acción' }
]
</script>

<template>
  <UPageSection
    id="texto"
    title="Texto"
    description="Seis niveles de emphasis. Ninguno usa un color literal: todos leen un token, asi que el cambio de tema es automatico."
  >
    <div class="flex flex-col gap-3">
      <div
        v-for="item in scale"
        :key="item.label"
        class="flex flex-col gap-1 border-b border-default pb-3 last:border-0 last:pb-0 sm:flex-row sm:items-baseline sm:gap-4"
      >
        <code
          :class="item.class"
          class="shrink-0 sm:w-40"
        >
          {{ item.label }}
        </code>

        <span class="text-dimmed text-sm">
          {{ item.note }}
        </span>
      </div>
    </div>
  </UPageSection>
</template>
