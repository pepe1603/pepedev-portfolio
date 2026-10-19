<script setup lang="ts">
import type { NavigationMenuItem } from '@nuxt/ui'

// Estado compartido con AppCommandPalette y el atajo ⌘K / Ctrl+K.
const isCommandPaletteOpen = useCommandPalette()

// Marca de las entradas sin API. En vez de enlazar a rutas que devuelven 404,
// se quedan visibles y deshabilitadas: el usuario ve la estructura del panel y
// lo que falta, y no se le promete nada roto. `disabled` no quita el item, solo
// lo desactiva (llega a ULinkBase como `:disabled`).
//
// Quien implemente la sección quita `disabled` y esta marca de una vez.
const PENDING: NavigationMenuItem['badge'] = { label: 'Próximamente', color: 'neutral', variant: 'subtle' }

// Grupos de navegación. `type: 'label'` crea el título de sección y
// `to` activa el item automáticamente según la ruta.
//
// Hoy hay API de panel para Proyectos, Certificados, Mensajes, el stack
// (que se gestiona como un PUT /admin/profile completo, sin CRUD propio de
// skills) y Ajustes (OTP y sesiones sobre la propia cuenta). Las entradas
// marcadas como PENDING no tienen ningún endpoint detrás: los iconos son
// cosa del frontend y "Medios" no existe porque /admin/storage solo sube un
// fichero, sin listado del bucket.

// Badge de mensajes sin leer: lo trae GET /admin/messages/unread-count y lo
// comparte con /admin/mensajes (composables/useUnreadMessages.ts).
const { count: unreadCount, refreshUnread } = useUnreadMessages()
onMounted(refreshUnread)

const items = computed<NavigationMenuItem[][]>(() => [
  [
    { label: 'General', type: 'label' },
    { label: 'Dashboard', icon: 'i-lucide-layout-dashboard', to: '/admin' },
    { label: 'Proyectos', icon: 'i-lucide-folder-kanban', to: '/admin/proyectos' },
    { label: 'Certificados', icon: 'i-lucide-award', to: '/admin/certificados' },
    {
      label: 'Mensajes',
      icon: 'i-lucide-mail',
      to: '/admin/mensajes',
      // Solo aparece si hay algo sin leer: un badge con "0" es ruido.
      badge: unreadCount.value
        ? { label: String(unreadCount.value), color: 'primary', variant: 'solid' }
        : undefined
    }
  ],
  [
    { label: 'Contenido', type: 'label' },
    {
      label: 'Tecnologías',
      icon: 'i-lucide-code-xml',
      defaultOpen: true,
      children: [
        { label: 'Stack', to: '/admin/tecnologias' },
        { label: 'Iconos', to: '/admin/tecnologias/iconos', disabled: true, badge: PENDING }
      ]
    },
    { label: 'Medios', icon: 'i-lucide-image', to: '/admin/medios', disabled: true, badge: PENDING }
  ],
  [
    { label: 'Sistema', type: 'label' },
    { label: 'Ajustes', icon: 'i-lucide-settings', to: '/admin/ajustes' },
    { label: 'Ver portfolio', icon: 'i-lucide-external-link', to: '/', target: '_blank' }
  ]
])

const { user } = useUserSession()
const { logout } = useLogout()

// Marca del footer: el email real de la sesión y su inicial como avatar.
const userLabel = computed(() => user.value?.email ?? 'Admin')
const userInitials = computed(() => (user.value?.email?.[0] ?? 'A').toUpperCase())
</script>

<template>
  <UDashboardGroup
    storage="local"
    storage-key="admin"
  >
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
              <UKbd
                value="meta"
                variant="subtle"
              />
              <UKbd
                value="K"
                variant="subtle"
              />
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
            :label="collapsed ? undefined : userLabel"
            :avatar="{ text: userInitials }"
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
            @click="logout()"
          />
        </div>
      </template>
    </UDashboardSidebar>

    <slot />
  </UDashboardGroup>
</template>
