<script setup lang="ts">
// ======================================================================
// EDICIÓN DE UN CERTIFICADO
//
// La key sigue al id, igual que en la página de proyectos: así, al navegar
// de un certificado a otro en el cliente se relanza la petición en vez de
// quedarse con los datos del anterior.
// ======================================================================
definePageMeta({ layout: 'dashboard' })

const route = useRoute()
const api = useAdminApi()

const id = computed(() => String(route.params.id))

const { data: certificate, status, error } = await useAsyncData(
  () => `admin-certificate-${id.value}`,
  () => api.get<AdminCertificate>(`/certificates/${id.value}`),
  { server: false, watch: [id] }
)

// Igual que en el listado: se comprueba `error` y no su tipo. Un 404 del
// backend llega como ProblemDetail, pero una API parada no, y enseñar
// "no hay certificados" en esa situación sería mentir.
const loadErrorMessage = computed(() => {
  const apiError = unwrapApiError(error.value)
  return apiError?.problem.detail ?? apiError?.message ?? 'No se pudo conectar con el servidor'
})
</script>

<template>
  <UDashboardPanel id="admin-certificados-edit">
    <template #header>
      <UDashboardNavbar :title="certificate ? (localizedText(certificate.title) || 'Editar certificado') : 'Editar certificado'">
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
      <!-- Cargando. -->
      <div
        v-if="status === 'pending'"
        class="space-y-4"
        aria-hidden="true"
      >
        <USkeleton class="h-40 w-full" />
        <USkeleton class="h-40 w-full" />
      </div>

      <!-- No existe o la API no responde: dentro de la propia página, para
           no sacarle al usuario del panel. -->
      <UCard
        v-else-if="error"
        variant="subtle"
      >
        <div class="flex flex-col items-start gap-3">
          <div>
            <p class="font-medium text-highlighted">
              No se pudo cargar el certificado
            </p>
            <p class="text-muted text-sm">
              {{ loadErrorMessage }}
            </p>
          </div>
          <UButton
            label="Volver a certificados"
            icon="i-lucide-arrow-left"
            color="neutral"
            variant="soft"
            to="/admin/certificados"
          />
        </div>
      </UCard>

      <AdminCertificateForm
        v-else-if="certificate"
        :certificate="certificate"
      />
    </template>
  </UDashboardPanel>
</template>
