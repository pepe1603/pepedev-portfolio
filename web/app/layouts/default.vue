<script setup lang="ts">
// ======================================================================
// LAYOUT POR DEFECTO
// El shell (cabecera, contenido, pie) vive aqui y no en app.vue.
//
// Que el shell este en un layout y no en app.vue no es cosmetico: Nuxt
// resuelve el layout por pagina, asi que una pagina puede salirse del
// contenedor con `definePageMeta({ layout: false })`. Con el shell en app.vue
// no habria manera de tener una pagina a sangre sin duplicar el header.
//
// ======================================================================
// POR QUE SOLO EL CONTENIDO VA DENTRO DE UN UCONTAINER
//
// Ni UHeader ni UFooter llevan UContainer propio. Al parecer que si, pero es
// al reves: los dos renderizan uno por dentro (Header.vue y Footer.vue lo
// importan). Envolverlos aqui ademas seria el error tipico de esta pagina,
// doble centrado.
//
// Asi que el unico contenedor explicito va alrededor del slot, y el
// contenedor de la cabecera y el del pie los pone cada componente. Tres
// contenedores, tres regiones, ninguno dentro de otro.
//
// ======================================================================
// NAVEGACION PUBLICA
//
// Inicio, Proyectos y Certificados son rutas reales. Contacto NO: es una
// seccion anclada dentro de la landing (pages/index.vue, id="contacto"),
// asi que se navega como hash a la raiz. La lista es la del portfolio, no
// la de la plantilla original: /formulario y /carrusel eran demos.
//
// ======================================================================
// EL SELECTOR DE IDIOMA
//
// Cambia useLang, que es lo que lee useApi para montar ?lang= en cada
// peticion publica. Solo afecta al CONTENIDO de la API: la interfaz
// (etiquetas, toasts) se queda en español hasta la Fase 4 del roadmap.
// ======================================================================

// El ancho sale de --ui-container, declarado en assets/css/main.css. Cambiar
// ese token mueve cabecera, contenido y pie a la vez.

// El boton de buscar y la paleta comparten este estado. Lo consume el
// header de este layout y el componente AppCommandPalette.
const isCommandPaletteOpen = useCommandPalette()
const lang = useLang()
</script>

<template>
  <div class="flex min-h-svh flex-col">
    <UHeader class="w-full">
      <template #left>
        <!-- Marca del portfolio. -->
        <NuxtLink
          to="/"
          class="flex items-center gap-2 rounded-md p-1.5 -ms-1 hover:outline-1 outline-primary/25"
        >
          <UIcon
            name="i-lucide-code-xml"
            class="text-primary size-5"
          />

          <span class="font-semibold">
            pepedev
          </span>
        </NuxtLink>

        <!-- Navegación pública. Contacto no es ruta: es un ancla de la
             landing (#contacto), asi que va como hash a la raiz. -->
        <UButton
          to="/"
          label="Inicio"
          size="xs"
          color="neutral"
          variant="ghost"
        />

        <UButton
          to="/proyectos"
          label="Proyectos"
          size="xs"
          color="neutral"
          variant="ghost"
        />

        <UButton
          to="/certificados"
          label="Certificados"
          size="xs"
          color="neutral"
          variant="ghost"
        />

        <UButton
          to="/#contacto"
          label="Contacto"
          size="xs"
          color="neutral"
          variant="ghost"
        />
      </template>

      <template #right>
        <!-- Abre la paleta global. Es el mismo estado que el atajo
             ⌘K / Ctrl+K, asi que los dos caminos hacen lo mismo. -->
        <UButton
          icon="i-lucide-search"
          aria-label="Buscar"
          color="neutral"
          variant="ghost"
          @click="isCommandPaletteOpen = true"
        />

        <!-- Selector de idioma del contenido de la API. `soft` en el activo
             para que se vea cual esta elegido sin inventar un estilo nuevo. -->
        <UButton
          label="ES"
          size="xs"
          color="neutral"
          :variant="lang === 'es' ? 'soft' : 'ghost'"
          :aria-pressed="lang === 'es'"
          @click="lang = 'es'"
        />

        <UButton
          label="EN"
          size="xs"
          color="neutral"
          :variant="lang === 'en' ? 'soft' : 'ghost'"
          :aria-pressed="lang === 'en'"
          @click="lang = 'en'"
        />

        <ColorModeToggle />

        <UButton
          to="https://github.com/pepe1603"
          target="_blank"
          icon="i-simple-icons-github"
          aria-label="GitHub"
          color="neutral"
          variant="ghost"
        />
      </template>
    </UHeader>

    <UMain class="flex-1">
      <!-- El unico UContainer explicito del layout. El contenido de las
           paginas entra aqui sin contenedor propio: UPage no lo necesita
           porque no lleva ancho, solo flex-col y flex-1. -->
      <UContainer>
        <slot />
      </UContainer>
    </UMain>

    <USeparator />

    <UFooter>
      <template #left>
        <p class="text-muted text-sm">
          © {{ new Date().getFullYear() }}
        </p>
      </template>
    </UFooter>
  </div>
</template>
