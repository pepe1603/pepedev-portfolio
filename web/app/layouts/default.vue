<script setup lang="ts">
// Layout por defecto: el shell (cabecera, contenido, pie) vive aquí y no en
// app.vue, para poder salirse del contenedor con definePageMeta({ layout: false }).
//
// Solo el contenido va dentro de un UContainer: UHeader y UFooter ya
// renderizan el suyo por dentro. El ancho sale de --ui-container (main.css).
//
// Contacto no es ruta: es un ancla de la landing (#contacto).
// El selector de idioma cambia useLang, que useApi usa para ?lang=.

// Estado compartido con AppCommandPalette y el atajo ⌘K / Ctrl+K.
const isCommandPaletteOpen = useCommandPalette()
const lang = useLang()

// Estado del menú móvil de UHeader, para cerrarlo al navegar a un hash.
const isMenuOpen = ref(false)

const navLinks = [
  { label: 'Proyectos', to: '/proyectos' },
  { label: 'Certificados', to: '/certificados' },
  { label: 'Contacto', to: '/#contacto' }
]

// Alterna entre ES y EN. El botón muestra el idioma activo.
const nextLang = computed(() => (lang.value === 'es' ? 'en' : 'es'))
function toggleLang() {
  lang.value = nextLang.value
}
</script>

<template>
  <div class="flex min-h-svh flex-col">
    <UHeader
      v-model:open="isMenuOpen"
      class="mx-0 m-auto rounded-full"
    >
      <template #left>
        <!-- Marca: visible siempre. -->
        <NuxtLink
          to="/"
          class="flex items-center gap-2 rounded-md p-1.5 -ms-1 hover:outline-1 outline-primary/25"
        >
          <UIcon
            name="i-lucide-code-xml"
            class="text-primary size-5"
          />
          <span class="font-semibold">pepedev</span>
        </NuxtLink>

        <!-- Navegación de escritorio: oculta bajo lg (la sustituye el menú móvil). -->
        <div class="hidden lg:flex items-center gap-1.5">
          <UButton
            v-for="link in navLinks"
            :key="link.to"
            :to="link.to"
            :label="link.label"
            size="xs"
            color="neutral"
            variant="ghost"
          />
        </div>
      </template>

      <template #right>
        <!-- Búsqueda: mismo estado que ⌘K / Ctrl+K. -->
        <UButton
          icon="i-lucide-search"
          aria-label="Buscar"
          color="neutral"
          variant="ghost"
          @click="isCommandPaletteOpen = true"
        />

        <!-- Idioma: un solo botón que muestra el idioma activo y alterna. -->
        <UButton
          icon="i-lucide-languages"
          :label="lang.toUpperCase()"
          size="xs"
          color="neutral"
          variant="soft"
          :aria-label="`Idioma: ${lang.toUpperCase()}. Cambiar a ${nextLang.toUpperCase()}`"
          @click="toggleLang"
        />

        <ColorModeToggle />
      </template>

      <!-- Menú móvil nativo de UHeader (solo se muestra bajo lg). -->
      <template #body>
        <div class="flex flex-col gap-1">
          <UButton
            v-for="link in navLinks"
            :key="link.to"
            :to="link.to"
            :label="link.label"
            color="neutral"
            variant="ghost"
            block
            class="justify-start"
            @click="isMenuOpen = false"
          />
        </div>
      </template>
    </UHeader>

    <UMain class="flex-1">
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
