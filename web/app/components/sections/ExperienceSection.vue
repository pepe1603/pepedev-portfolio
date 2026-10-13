<script setup lang="ts">
import type { Experience } from '~/utils/types'

defineProps<{
  experiences: Experience[]
}>()

function typeLabel(type: string): string {
  const labels: Record<string, string> = {
    empleo: 'Empleo',
    proyecto: 'Proyecto',
    academico: 'Académico',
    formacion: 'Formación',
    internship: 'Prácticas',
    freelance: 'Freelance'
  }

  return labels[type.toLowerCase()] ?? type
}

function typeIcon(type: string): string {
  const icons: Record<string, string> = {
    empleo: 'i-lucide-briefcase-business',
    proyecto: 'i-lucide-code-2',
    academico: 'i-lucide-graduation-cap',
    formacion: 'i-lucide-book-open',
    internship: 'i-lucide-users',
    freelance: 'i-lucide-laptop'
  }

  return icons[type.toLowerCase()] ?? 'i-lucide-briefcase'
}
</script>

<template>
  <UPageSection
    v-if="experiences.length"
    id="experiencia"
    headline="TRAYECTORIA"
    title="Experiencia"
    description="Proyectos, experiencias y aprendizajes que forman mi recorrido como desarrollador."
    class="scroll-mt-24"
  >
    <ol class="relative flex flex-col gap-6">
      <li
        v-for="(experience, index) in experiences"
        :key="`${experience.company}-${experience.title}-${index}`"
        class="relative grid grid-cols-[20px_minmax(0,1fr)] gap-4"
      >
        <!-- Línea de tiempo -->
        <div class="flex h-full flex-col items-center">
          <div
            class="z-10 flex size-5 shrink-0 items-center
                   justify-center rounded-full
                   border-2 border-primary bg-default"
          >
            <div class="size-2 rounded-full bg-primary" />
          </div>

          <div
            v-if="index < experiences.length - 1"
            class="mt-2 w-px flex-1 bg-border"
          />
        </div>

        <!-- Contenido -->
        <UCard class="mb-1 transition-colors hover:border-primary/40">
          <div class="flex flex-col gap-4">
            <div class="flex items-start gap-3">
              <div
                class="flex size-10 shrink-0 items-center
                       justify-center rounded-lg
                       bg-primary/10 text-primary"
              >
                <UIcon
                  :name="typeIcon(experience.type)"
                  class="size-5"
                />
              </div>

              <div class="min-w-0 flex-1">
                <h3 class="text-base font-semibold leading-snug">
                  {{ experience.title }}
                </h3>

                <p class="mt-1 text-sm text-muted">
                  {{ experience.company }}
                </p>
              </div>

              <UBadge
                color="neutral"
                variant="subtle"
                class="shrink-0"
              >
                {{ typeLabel(experience.type) }}
              </UBadge>
            </div>

            <div class="flex items-center gap-2 text-sm text-muted">
              <UIcon
                name="i-lucide-calendar"
                class="size-4 shrink-0"
              />
              <span>{{ experience.period }}</span>
            </div>

            <p class="text-sm leading-7 text-toned whitespace-pre-line">
              {{ experience.description }}
            </p>
          </div>
        </UCard>
      </li>
    </ol>
  </UPageSection>
</template>
