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
// El ancho sale de --ui-container, declarado en assets/css/main.css. Cambiar
// ese token mueve cabecera, contenido y pie a la vez.
// ======================================================================

// El boton de buscar y la paleta comparten este estado. Lo consume el
// header de este layout y el componente AppCommandPalette.
const isCommandPaletteOpen = useCommandPalette()
</script>

<template>
  <div class="flex min-h-svh flex-col">
    <UHeader class="w-full">
      <template #left>
        <!-- Icono del proyecto. Reemplaza por tu logo. -->
        <NuxtLink
          to="/"
          class="flex items-center gap-2 rounded-md p-1.5 -ms-1 hover:outline-1 outline-primary/25"
        >
          <UIcon
            name="i-simple-icons-nuxtdotjs"
            class="text-primary size-5"
          />

          <span class="font-semibold">
            Template
          </span>
        </NuxtLink>

        <!-- Paginas de ejemplo del template. Quitalas al empezar un
             proyecto de verdad: no aportan nada a la aplicacion. -->
        <UButton
          to="/formulario"
          label="Formulario"
          size="xs"
          color="neutral"
          variant="ghost"
        />

        <UButton
          to="/carrusel"
          label="Carrusel"
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

        <ColorModeToggle />

        <UButton
          to="https://github.com"
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

      <template #right>
        <p class="text-dimmed text-sm">
          Nuxt 4 · Nuxt UI · Tailwind v4
        </p>
      </template>
    </UFooter>
  </div>
</template>
