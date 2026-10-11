<script setup lang="ts">
// ======================================================================
// HERO DE LA LANDING
//
// Es la adaptación del HeroSection de la plantilla: mismo UPageHero y
// mismo GradientTitle, pero con los datos del profile público en vez del
// texto de ejemplo del sistema de diseño.
//
// El headline va arriba (kicker) y el nombre grande en el slot title
// porque UPageHero envuelve ese slot en un <h1>: GradientTitle va como
// span para no meter un <h1> dentro de otro <h1>.
// ======================================================================
import type { ProfilePublicDTO } from '~/utils/types'

defineProps<{
  profile: ProfilePublicDTO
}>()

// Los tres CTA de la navegación pública. Contacto es un ancla de esta
// misma página (id="contacto" en ContactSection), no una ruta: no existe
// /contacto en el portfolio.
const links = [
  {
    label: 'Ver proyectos',
    to: '/proyectos',
    size: 'lg' as const,
    icon: 'i-lucide-folder-kanban'
  },
  {
    label: 'Certificados',
    to: '/certificados',
    size: 'lg' as const,
    color: 'neutral' as const,
    variant: 'outline' as const,
    icon: 'i-lucide-award'
  },
  {
    label: 'Contacto',
    to: '/#contacto',
    size: 'lg' as const,
    color: 'neutral' as const,
    variant: 'outline' as const,
    icon: 'i-lucide-mail'
  }
]
</script>

<template>
  <UPageHero
    class="relative isolate overflow-hidden"
    :links="links"
  >
    <template #top>
      <div
        aria-hidden="true"
        class="pointer-events-none absolute inset-0 -z-10 overflow-hidden"
      >
        <div
          class="absolute left-1/2 top-0 size-80
                 -translate-x-1/2 rounded-full
                 bg-primary/10 blur-3xl sm:size-96"
        />
      </div>
    </template>

    <template #headline>
      <span
        class="inline-flex items-center gap-2 rounded-full
               border border-default bg-elevated/70
               px-3 py-1.5 text-sm font-medium
               text-muted backdrop-blur"
      >
        <UIcon
          name="i-lucide-code-2"
          class="size-4 text-primary"
        />
        Portfolio de desarrollo
      </span>
    </template>

    <template #title>
      <GradientTitle
        as="span"
        size="xl"
      >
        {{ profile.headline }}
      </GradientTitle>
    </template>

    <template #description>
      <p class="mx-auto max-w-xl text-pretty">
        Explora mis proyectos y las tecnologías que utilizo
        para construir soluciones de software.
      </p>
    </template>
  </UPageHero>
</template>
