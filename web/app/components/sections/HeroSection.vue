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
    :headline="profile.headline"
    :links="links"
  >
    <template #title>
      <GradientTitle
        as="span"
        size="xl"
      >
        {{ profile.fullName }}
      </GradientTitle>
    </template>

    <template #description>
      <p>{{ profile.location }}</p>
    </template>
  </UPageHero>
</template>
