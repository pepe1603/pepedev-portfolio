<script setup lang="ts">
definePageMeta({ layout: 'dashboard' })
</script>

<template>
  <UDashboardPanel id="admin-proyectos-nuevo">
    <template #header>
      <UDashboardNavbar title="Nuevo proyecto">
        <template #leading>
          <UDashboardSidebarCollapse />
        </template>

        <template #right>
          <ColorModeToggle />
          <UButton
            label="Volver"
            icon="i-lucide-arrow-left"
            color="neutral"
            variant="soft"
            to="/admin/proyectos"
          />
        </template>
      </UDashboardNavbar>
    </template>

    <template #body>
      <AdminProjectForm />
    </template>
  </UDashboardPanel>
</template>
