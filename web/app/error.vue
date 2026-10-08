<script setup lang="ts">
// ======================================================================
// PÁGINA DE ERROR GLOBAL
//
// La plantilla no traía una, así que un createError (por ejemplo el 404
// de /proyectos/[slug]) habría pintado la página de error genérica de
// Nuxt, fuera del tema del sitio. Este componente sustituye a app.vue
// entero cuando algo lanza un error, así que lleva su propio <UApp> y no
// depende del layout: es lo único que se renderiza en ese momento.
//
// clearError borra el estado del error y navega; sin el redirect, el
// usuario quedaría en la página de error aunque pulse el botón.
// ======================================================================
import type { NuxtError } from '#app'

const props = defineProps<{ error: NuxtError }>()

// 404 = recurso o ruta inexistente; el resto se trata como fallo general.
const title = computed(() => (props.error.statusCode === 404 ? 'Página no encontrada' : 'Algo ha fallado'))

// import.meta.dev no puede ir en el template (expresión de Vue, no JS
// puro): se resuelve aquí una vez.
const isDev = import.meta.dev

function backHome() {
  clearError({ redirect: '/' })
}
</script>

<template>
  <UApp>
    <UContainer class="flex min-h-svh flex-col items-center justify-center gap-4 text-center">
      <p class="text-highlighted text-7xl font-bold">
        {{ error.statusCode }}
      </p>

      <h1 class="text-2xl font-semibold">
        {{ title }}
      </h1>

      <p
        v-if="error.statusMessage"
        class="text-muted"
      >
        {{ error.statusMessage }}
      </p>

      <UButton
        label="Volver al inicio"
        icon="i-lucide-house"
        @click="backHome"
      />

      <!-- El stack solo en dev: en producción es ruido y no debe salir. -->
      <pre
        v-if="isDev && error.stack"
        class="text-dimmed max-w-full overflow-x-auto text-left text-xs"
      >{{ error.stack }}</pre>
    </UContainer>
  </UApp>
</template>
