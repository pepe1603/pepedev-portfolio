<script setup lang="ts">
// ======================================================================
// EDICIÓN DE UN PROYECTO
//
// La key sigue al id, igual que hace la página pública de proyecto: así,
// al navegar de un proyecto a otro en el cliente se relanza la petición en
// vez de quedarse con los datos del anterior.
// ======================================================================
definePageMeta({ layout: 'dashboard' })

const route = useRoute()
const api = useAdminApi()

const id = computed(() => String(route.params.id))

const { data: project, status, error } = await useAsyncData(
  () => `admin-project-${id.value}`,
  () => api.get<AdminProject>(`/projects/${id.value}`),
  { server: false, watch: [id] }
)

// Igual que en el listado: se comprueba `error` y no su tipo. Un404 del
// backend llega como ProblemDetail, pero una API parada no, y enseñar
// "no hay proyectos" en esa situación sería mentir.
const loadErrorMessage = computed(() => {
  const apiError = unwrapApiError(error.value)
  return apiError?.problem.detail ?? apiError?.message ?? 'No se pudo conectar con el servidor'
})
</script>

<template>
  <UDashboardPanel id="admin-proyectos-edit">
    <template #header>
      <UDashboardNavbar :title="project ? (project.slug || 'Editar proyecto') : 'Editar proyecto'">
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
              No se pudo cargar el proyecto
            </p>
            <p class="text-muted text-sm">
              {{ loadErrorMessage }}
            </p>
          </div>
          <UButton
            label="Volver a proyectos"
            icon="i-lucide-arrow-left"
            color="neutral"
            variant="soft"
            to="/admin/proyectos"
          />
        </div>
      </UCard>

      <AdminProjectForm
        v-else-if="project"
        :project="project"
      />
    </template>
  </UDashboardPanel>
</template>
