<script setup lang="ts">
// ======================================================================
// SEO
// Ajusta title y description por proyecto. Las imagenes sociales (ogImage)
// se omiten a proposito: anade la tuya en public/ y descomenta abajo.
// ======================================================================

const route = useRoute()
const config = useRuntimeConfig()

// Canonical y og:url globales por ruta. Sin query a propósito: los
// parámetros de filtro (/certificados?kind=...) no son páginas nuevas
// y no deben duplicar la URL canónica.
const pageUrl = computed(() => `${config.public.siteUrl.replace(/\/$/, '')}${route.path}`)

useSeoMeta({
  title: 'pepedev — Portfolio',
  description: 'Proyectos, certificados y contacto.',
  ogSiteName: 'pepedev',
  ogType: 'website',
  ogUrl: () => pageUrl.value
})

// El lang del <html> sigue al idioma activo del contenido de la API; sin
// este binding, el atributo quedaria congelado en 'es' aunque el visitante
// cambiara a EN desde la cabecera.
const lang = useLang()

useHead({
  htmlAttrs: {
    lang
  },
  link: [
    { rel: 'canonical', href: () => pageUrl.value }
  ]
})

// ======================================================================
// TOASTER
// <UApp> es quien monta el <UToaster> que pinta los toasts, y sin el los
// avisos se encolan pero no se ven. No hace falta pasarle nada: todo tiene
// valores por defecto. (El binding opcional de posicion/duracion se fue
// con la seccion de documentacion de toasts.)
// ======================================================================
</script>

<template>
  <UApp>
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
