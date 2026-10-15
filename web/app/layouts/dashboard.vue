<script setup lang="ts">
import type { NavigationMenuItem } from '@nuxt/ui'

// Estado compartido con AppCommandPalette y el atajo ⌘K / Ctrl+K.
const isCommandPaletteOpen = useCommandPalette()

// Grupos de navegación. `type: 'label'` crea el título de sección y
// `to` activa el item automáticamente según la ruta.
const items: NavigationMenuItem[][] = [
  [
    { label: 'General', type: 'label' },
    { label: 'Dashboard', icon: 'i-lucide-layout-dashboard', to: '/admin' },
    { label: 'Proyectos', icon: 'i-lucide-folder-kanban', to: '/admin/proyectos' },
    { label: 'Certificados', icon: 'i-lucide-award', to: '/admin/certificados' },
    { label: 'Mensajes', icon: 'i-lucide-mail', to: '/admin/mensajes', badge: '3' }
  ],
  [
    { label: 'Contenido', type: 'label' },
    {
      label: 'Tecnologías',
      icon: 'i-lucide-code-xml',
      defaultOpen: true,
      children: [
        { label: 'Stack', to: '/admin/tecnologias' },
        { label: 'Iconos', to: '/admin/tecnologias/iconos' }
      ]
    },
    { label: 'Medios', icon: 'i-lucide-image', to: '/admin/medios' }
  ],
  [
    { label: 'Sistema', type: 'label' },
    { label: 'Ajustes', icon: 'i-lucide-settings', to: '/admin/ajustes' },
    { label: 'Ver portfolio', icon: 'i-lucide-external-link', to: '/', target: '_blank' }
  ]
]

function logout() {
  // Aquí va tu lógica de cierre de sesión (por ejemplo, clear() de useUserSession).
}
</script>

<template>
  <UDashboardGroup storage="local" storage-key="admin">
    <UDashboardSidebar
      collapsible
      resizable
      :min-size="16"
      :default-size="18"
      :max-size="24"
      :ui="{ footer: 'border-t border-default' }"
    >
      <!-- Marca: se reduce a icono al colapsar. -->
      <template #header="{ collapsed }">
        <NuxtLink
          to="/admin"
          class="flex items-center gap-2"
          :class="collapsed ? 'mx-auto' : ''"
          aria-label="Panel de administración"
        >
          <UIcon
            name="i-lucide-code-xml"
            class="text-primary size-5 shrink-0"
          />
          <span
            v-if="!collapsed"
            class="font-semibold"
          >
            pepedev <span class="text-muted font-normal">admin</span>
          </span>
        </NuxtLink>
      </template>

      <template #default="{ collapsed }">
        <!-- Búsqueda: mismo estado que la paleta global. -->
        <UButton
          :label="collapsed ? undefined : 'Buscar...'"
          icon="i-lucide-search"
          color="neutral"
          variant="outline"
          block
          :square="collapsed"
          @click="isCommandPaletteOpen = true"
        >
          <template
            v-if="!collapsed"
            #trailing
          >
            <div class="flex items-center gap-0.5 ms-auto">
              <UKbd value="meta" variant="subtle" />
              <UKbd value="K" variant="subtle" />
            </div>
          </template>
        </UButton>

        <UNavigationMenu
          v-for="(group, i) in items"
          :key="i"
          :collapsed="collapsed"
          :items="group"
          orientation="vertical"
        />
      </template>

      <template #footer="{ collapsed }">
        <div
          class="flex w-full items-center gap-1"
          :class="collapsed ? 'flex-col' : ''"
        >
          <UButton
            :label="collapsed ? undefined : 'Admin'"
            :avatar="{ text: 'A' }"
            color="neutral"
            variant="ghost"
            class="flex-1 min-w-0"
            :block="collapsed"
          />
          <UButton
            icon="i-lucide-log-out"
            aria-label="Cerrar sesión"
            color="neutral"
            variant="ghost"
            @click="logout"
          />
        </div>
      </template>
    </UDashboardSidebar>

    <slot />
  </UDashboardGroup>
</template>
