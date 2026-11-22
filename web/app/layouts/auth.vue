<script setup lang="ts">
// ======================================================================
// LAYOUT DE AUTENTICACIÓN
//
// Shell mínimo y centrado para las pantallas de acceso (login, verificación
// por código, recuperación y 403). No usa el layout público a propósito:
// arrastrar cabecera, menú móvil y pie a un flujo de acceso sobra, y el
// portfolio no debe cambiar por esto.
//
// Estas pantallas no se indexan: son flujos, no contenido, y no hay nada
// que un buscador deba ofrecer aquí. Se refuerza con el Disallow de
// robots.txt (ver server/routes/robots.txt.ts).
useSeoMeta({ robots: 'noindex, nofollow' })
// ======================================================================
</script>

<template>
  <div class="flex min-h-svh flex-col items-center justify-center gap-6 p-4">
    <NuxtLink
      to="/"
      class="flex items-center gap-2"
      aria-label="Volver al portfolio"
    >
      <UIcon
        name="i-lucide-code-xml"
        class="text-primary size-5"
      />
      <span class="font-semibold">pepedev</span>
    </NuxtLink>

    <slot />
  </div>
</template>
