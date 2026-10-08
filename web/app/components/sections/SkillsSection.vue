<script setup lang="ts">
// ======================================================================
// SKILLS
//
// Sección nueva (la plantilla no tenía equivalente). El DTO trae cada
// skill con name, category y level; se agrupa por category porque es la
// única estructura que el backend da para ordenar la lectura, y una lista
// plana de N skills seguidas no se puede escanear.
//
// Secciones vacías no se pintan: un "Skills" con cero entradas es ruido.
// ======================================================================
import type { Skill } from '~/utils/types'

const props = defineProps<{
  skills: Skill[]
}>()

const grouped = computed(() => {
  const map = new Map<string, Skill[]>()

  for (const skill of props.skills) {
    const list = map.get(skill.category) ?? []
    list.push(skill)
    map.set(skill.category, list)
  }

  return [...map.entries()]
})

// El level viene como texto libre del backend; se añade al nombre solo si
// existe, sin partirla en dos elementos (un badge, una línea).
function skillLabel(skill: Skill): string {
  return skill.level ? `${skill.name} · ${skill.level}` : skill.name
}
</script>

<template>
  <UPageSection
    v-if="skills.length"
    title="Skills"
  >
    <div class="grid gap-6 sm:grid-cols-2">
      <UCard
        v-for="[category, items] in grouped"
        :key="category"
      >
        <template #header>
          <h3 class="font-semibold">
            {{ category }}
          </h3>
        </template>

        <ul class="flex flex-wrap gap-2">
          <li
            v-for="skill in items"
            :key="skill.name"
          >
            <UBadge
              color="neutral"
              variant="subtle"
            >
              {{ skillLabel(skill) }}
            </UBadge>
          </li>
        </ul>
      </UCard>
    </div>
  </UPageSection>
</template>
