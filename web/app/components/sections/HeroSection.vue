<script setup lang="ts">
const links = [
  {
    label: 'Ver secciones',
    to: '#texto',
    size: 'lg' as const,
    icon: 'i-lucide-arrow-down'
  },
  {
    label: 'Documentación',
    to: 'https://ui.nuxt.com',
    target: '_blank' as const,
    size: 'lg' as const,
    color: 'neutral' as const,
    variant: 'outline' as const,
    icon: 'i-lucide-book-open'
  }
]
</script>

<template>
  <UPageHero
    headline="Nuxt UI Template"
    :links="links"
  >
    <!--
      UPageHero ya envuelve el slot title en un <h1>, asi que aqui el
      componente va como span: un <h1> dentro de un <h1> seria HTML
      invalido y ademas duplicaria el rol de encabezado.
    -->
    <template #title>
      <GradientTitle
        as="span"
        size="xl"
      >
        Sistema de diseño
      </GradientTitle>
    </template>

    <template #description>
      <p>
        Todos los valores de esta página salen de los tokens
        <code class="text-toned">--ui-*</code>. Cambia el tema con el botón
        superior: nada del CSS de la página cambia, solo las variables.
      </p>
    </template>
  </UPageHero>
</template>
