<script setup lang="ts">
definePageMeta({ layout: 'dashboard' })
</script>

<template>
  <UDashboardPanel id="admin-certificados-nuevo">
    <template #header>
      <UDashboardNavbar title="Nuevo certificado">
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
            to="/admin/certificados"
          />
        </template>
      </UDashboardNavbar>
    </template>

    <template #body>
      <AdminCertificateForm />
    </template>
  </UDashboardPanel>
</template>
