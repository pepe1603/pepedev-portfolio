<script setup lang="ts">
// ======================================================================
// EXPERIENCIA
//
// Sección nueva (la plantilla no tenía equivalente). La Experience del
// DTO no trae fechas estructuradas sino un `period` ya formateado, así
// que no hay cálculo de duración que hacer aquí: se pinta tal cual.
//
// La lista viene en el orden que mande la API; no se reordena en el
// cliente porque el orden también es contenido.
// ======================================================================
import type { Experience } from '~/utils/types'

defineProps<{
  experiences: Experience[]
}>()
</script>

<template>
  <UPageSection
    v-if="experiences.length"
    title="Experiencia"
  >
    <ol class="flex flex-col gap-4">
      <li
        v-for="(experience, index) in experiences"
        :key="index"
      >
        <UCard>
          <div class="flex flex-wrap items-baseline justify-between gap-2">
            <h3 class="font-semibold">
              {{ experience.title }}
            </h3>

            <UBadge
              color="neutral"
              variant="subtle"
            >
              {{ experience.type }}
            </UBadge>
          </div>

          <p class="text-muted text-sm">
            {{ experience.company }} · {{ experience.period }}
          </p>

          <p class="mt-2">
            {{ experience.description }}
          </p>
        </UCard>
      </li>
    </ol>
  </UPageSection>
</template>
