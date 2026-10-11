<script setup lang="ts">
import type { Skill } from '~/utils/types'

const props = defineProps<{
  skills: Skill[]
}>()

const grouped = computed(() => {
  const map = new Map<string, Skill[]>()

  for (const skill of props.skills) {
    const category = skill.category?.trim() || 'Otros'
    const items = map.get(category) ?? []

    items.push(skill)
    map.set(category, items)
  }

  return [...map.entries()]
})

function categoryIcon(category: string): string {
  const value = category.toLowerCase()

  if (value.includes('backend') || value.includes('servidor')) {
    return 'i-lucide-server'
  }

  if (value.includes('base de datos') || value.includes('database')) {
    return 'i-lucide-database'
  }

  if (value.includes('frontend') || value.includes('interfaz')) {
    return 'i-lucide-monitor'
  }

  if (value.includes('herramienta') || value.includes('tool')) {
    return 'i-lucide-wrench'
  }

  if (value.includes('devops') || value.includes('infraestructura')) {
    return 'i-lucide-container'
  }

  return 'i-lucide-code-2'
}
</script>

<template>
  <UPageSection
    v-if="skills.length"
    id="habilidades"
    headline="TECNOLOGÍAS Y HERRAMIENTAS"
    title="Mis habilidades"
    description="Las herramientas y tecnologías que utilizo para construir soluciones de software."
    class="scroll-mt-24"
  >
    <div class="grid gap-4 sm:grid-cols-2">
      <UCard
        v-for="[category, items] in grouped"
        :key="category"
        class="h-full"
      >
        <template #header>
          <div class="flex items-center gap-3">
            <div
              class="flex size-10 shrink-0 items-center justify-center
                     rounded-lg bg-primary/10 text-primary"
            >
              <UIcon
                :name="categoryIcon(category)"
                class="size-5"
              />
            </div>

            <div class="min-w-0">
              <h3 class="font-semibold">
                {{ category }}
              </h3>
              <p class="text-xs text-muted">
                {{ items.length }}
                {{ items.length === 1 ? 'tecnología' : 'tecnologías' }}
              </p>
            </div>
          </div>
        </template>

        <ul class="flex flex-col gap-1">
          <li
            v-for="skill in items"
            :key="skill.name"
            class="flex items-center justify-between gap-3 rounded-lg
                   px-2 py-3 transition-colors
                   hover:bg-elevated"
          >
            <span class="min-w-0 font-medium text-sm">
              {{ skill.name }}
            </span>

            <UBadge
              v-if="skill.level"
              color="neutral"
              variant="subtle"
              class="shrink-0"
            >
              {{ skill.level }}
            </UBadge>
          </li>
        </ul>
      </UCard>
    </div>
  </UPageSection>
</template>
