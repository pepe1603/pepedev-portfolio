<script setup lang="ts">
// ======================================================================
// SEO
// Ajusta title y description por proyecto. Las imagenes sociales (ogImage)
// se omiten a proposito: anade la tuya en public/ y descomenta abajo.
// ======================================================================

useSeoMeta({
  title: 'Nuxt UI Template',
  description: 'Plantilla de Nuxt 4 con Nuxt UI, Tailwind CSS v4 y tema claro/oscuro basado en tokens.'
})

useHead({
  htmlAttrs: {
    lang: 'es'
  }
})

// ======================================================================
// TOASTER
// <UApp> es quien monta el <UToaster> que pinta los toasts, y sin el los
// avisos se encolan pero no se ven. No hace falta pasarle nada: todo tiene
// valores por defecto.
//
// Aqui se le pasa un estado solo para que la seccion de documentacion de
// los toasts pueda cambiar posicion, duracion o limite en caliente. Si
// quitas esa seccion, quita estas dos lineas: el binding es opcional.
// ======================================================================

const toaster = useToasterOptions()
</script>

<template>
  <UApp :toaster="toaster">
    <!--
      El shell (cabecera, contenido, pie) vive en layouts/default.vue, no
      aqui. Nuxt elige layout por pagina, asi que una pagina puede salirse del
      contenedor con `definePageMeta({ layout: false })`; con el shell en
      app.vue no habria forma de tener una pagina a sangre.

      <NuxtLayout> se renderiza por debajo de <NuxtPage>, asi que el orden del
      DOM es el mismo que antes: header, contenido, pie.
    -->
    <NuxtLayout>
      <NuxtPage />
    </NuxtLayout>

    <!--
      Va aqui y FUERA de UPage, al final del template.

      Fuera de UPage porque es global: si viviera dentro, solo habria paleta
      en la pagina actual. Y al final para que en el orden del DOM quede
      despues del contenido, que es como debe estar una capa que se abre
      encima de todo.

      No hace falta ningun <ClientOnly>: el modal no se pinta hasta que se
      abre, asi que en el servidor no hay nada que hydratear.
    -->
    <AppCommandPalette />
  </UApp>
</template>
