<script setup lang="ts">
definePageMeta({ layout: 'dashboard' })

const stats = [
  { label: 'Proyectos', value: 12, hint: '+2 este mes', icon: 'i-lucide-folder-kanban' },
  { label: 'Certificados', value: 8, hint: '1 pendiente', icon: 'i-lucide-award' },
  { label: 'Mensajes', value: 3, hint: 'Sin leer', icon: 'i-lucide-mail' },
  { label: 'Tecnologías', value: 24, hint: 'En el stack', icon: 'i-lucide-code-xml' }
]
</script>

<template>
  <UDashboardPanel id="admin-dashboard">
    <template #header>
      <UDashboardNavbar title="Dashboard">
        <template #leading>
          <UDashboardSidebarCollapse />
        </template>

        <template #right>
          <ColorModeToggle />
          <UButton
            label="Nuevo"
            icon="i-lucide-plus"
            class="hidden sm:inline-flex"
          />
        </template>
      </UDashboardNavbar>
    </template>

    <template #body>
      <p class="text-muted text-sm">
        Resumen general de tu portfolio
      </p>

      <div class="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <UCard
          v-for="s in stats"
          :key="s.label"
        >
          <div class="flex items-center justify-between">
            <p class="text-muted text-xs">
              {{ s.label }}
            </p>
            <UIcon
              :name="s.icon"
              class="text-muted size-4"
            />
          </div>
          <p class="mt-1 text-2xl font-bold text-highlighted">
            {{ s.value }}
          </p>
          <p class="text-muted mt-1 text-xs">
            {{ s.hint }}
          </p>
        </UCard>
      </div>

      <UCard>
        <template #header>
          <h3 class="font-semibold text-highlighted">
            Actividad reciente
          </h3>
        </template>
        <ul class="text-muted space-y-2 text-sm">
          <li>• Se creó el proyecto <span class="text-primary font-medium">Portfolio v2</span></li>
          <li>• Nuevo mensaje de contacto</li>
          <li>• Certificado añadido</li>
        </ul>
      </UCard>
    </template>
  </UDashboardPanel>
</template>
