<script setup lang="ts">
// ======================================================================
// DASHBOARD
//
// Cuatro peticiones en paralelo, una por tarjeta. No hay endpoint de
// conteo: para contar proyectos y certificados hay que bajar la lista
// completa (GET /admin/projects y /admin/certificates no paginan), y con
// el tamaño real de un portfolio no compensa inventar nada más.
//
// El contador de mensajes sí es propio (GET /admin/messages/unread-count)
// y las tecnologías salen de skills dentro de GET /admin/profile.
//
// Como el resto del panel, se carga en cliente: el $fetch de Nuxt no
// reenvía las cookies en SSR y llegaríamos al proxy sin sesión.
// ======================================================================
definePageMeta({ layout: 'dashboard' })

const api = useAdminApi()

// Solo la parte del perfil que consume esta página; la entidad completa se
// tipa cuando exista la sección de ajustes.
interface DashboardProfile {
  skills: Array<{ name: string }>
}

interface DashboardData {
  projects: AdminProject[]
  certificates: Array<{ id: string, status: CertificateStatus, featured: boolean }>
  unread: { count: number }
  profile: DashboardProfile
}

const { data, status, error, refresh } = await useAsyncData(
  'admin-dashboard',
  async (): Promise<DashboardData> => {
    const [projects, certificates, unread, profile] = await Promise.all([
      api.get<AdminProject[]>('/projects'),
      api.get<DashboardData['certificates']>('/certificates'),
      api.get<DashboardData['unread']>('/messages/unread-count'),
      api.get<DashboardProfile>('/profile')
    ])
    return { projects, certificates, unread, profile }
  },
  { server: false }
)

// Igual que en /admin/proyectos: se comprueba `error`, no su tipo. Una API
// parada no llega como ProblemDetail y, si no, saldría "0 proyectos" en vez
// de un aviso de que no se pudo consultar.
const loadErrorMessage = computed(() => {
  const apiError = unwrapApiError(error.value)
  return apiError?.problem.detail ?? apiError?.message ?? 'No se pudo conectar con el servidor'
})

/** "1 publicado" / "3 publicados". */
function n(count: number, one: string, many: string): string {
  return `${count} ${count === 1 ? one : many}`
}

const stats = computed(() => {
  const dashboard = data.value
  const projects = dashboard?.projects ?? []
  const certificates = dashboard?.certificates ?? []
  const published = projects.filter(project => project.status === 'PUBLISHED').length
  const drafts = certificates.filter(certificate => certificate.status === 'DRAFT').length
  const unread = dashboard?.unread.count ?? 0
  const skills = dashboard?.profile.skills.length ?? 0

  return [
    {
      label: 'Proyectos',
      value: projects.length,
      hint: n(published, 'publicado', 'publicados'),
      icon: 'i-lucide-folder-kanban',
      to: '/admin/proyectos'
    },
    {
      label: 'Certificados',
      value: certificates.length,
      hint: drafts ? n(drafts, 'borrador', 'borradores') : 'Todos publicados',
      icon: 'i-lucide-award',
      to: '/admin/certificados'
    },
    {
      label: 'Mensajes',
      value: unread,
      hint: unread ? `${n(unread, 'sin leer', 'sin leer')}` : 'Todo leído',
      icon: 'i-lucide-mail',
      to: '/admin/mensajes'
    },
    {
      label: 'Tecnologías',
      value: skills,
      hint: skills ? 'En el stack' : 'Stack vacío',
      icon: 'i-lucide-code-xml',
      // La sección de tecnologías todavía no existe: la tarjeta cuenta lo
      // que sí hay (los skills del perfil), pero no abre una página rota.
      disabled: true
    }
  ]
})

// Mismo orden que pide la API (sortOrder ASC → createdAt ASC) leído al
// revés para lo más recientemente tocado.
const recentProjects = computed(() =>
  [...(data.value?.projects ?? [])]
    .sort((a, b) => b.updatedAt.localeCompare(a.updatedAt))
    .slice(0, 4)
)

// La fecha la pinta adminDate() de utils/adminTypes.ts: mismo criterio que en
// el resto del panel (aquí `updatedAt` es un Instant, no un LocalDate).
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
            label="Nuevo proyecto"
            icon="i-lucide-plus"
            class="hidden sm:inline-flex"
            to="/admin/proyectos/nuevo"
          />
        </template>
      </UDashboardNavbar>
    </template>

    <template #body>
      <p class="text-muted text-sm">
        Resumen general de tu portfolio
      </p>

      <!-- Cargando. -->
      <div
        v-if="status === 'pending'"
        class="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4"
        aria-hidden="true"
      >
        <USkeleton
          v-for="i in 4"
          :key="i"
          class="h-28 w-full"
        />
      </div>

      <!-- No se pudo consultar: nada de números inventados. -->
      <UCard
        v-else-if="error"
        variant="subtle"
      >
        <div class="flex flex-col items-start gap-3">
          <div>
            <p class="font-medium text-highlighted">
              No se pudo cargar el resumen
            </p>
            <p class="text-muted text-sm">
              {{ loadErrorMessage }}
            </p>
          </div>
          <UButton
            label="Reintentar"
            icon="i-lucide-refresh-cw"
            color="neutral"
            variant="soft"
            @click="refresh()"
          />
        </div>
      </UCard>

      <template v-else>
        <div class="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <UCard
            v-for="stat in stats"
            :key="stat.label"
          >
            <div class="flex items-center justify-between">
              <p class="text-muted text-xs">
                {{ stat.label }}
              </p>
              <UIcon
                :name="stat.icon"
                class="text-muted size-4"
              />
            </div>

            <p class="text-highlighted mt-1 text-2xl font-bold">
              {{ stat.value }}
            </p>

            <p
              v-if="!stat.disabled && stat.to"
              class="mt-1 text-xs"
            >
              <ULink
                :to="stat.to"
                class="text-muted hover:text-highlighted transition-colors"
              >
                {{ stat.hint }}
              </ULink>
            </p>
            <p
              v-else
              class="text-muted mt-1 text-xs"
            >
              {{ stat.hint }}
            </p>
          </UCard>
        </div>

        <UCard>
          <template #header>
            <div class="flex items-center justify-between gap-3">
              <h3 class="font-semibold text-highlighted">
                Últimos proyectos actualizados
              </h3>
              <ULink
                to="/admin/proyectos"
                class="text-primary text-sm font-medium"
              >
                Ver todos
              </ULink>
            </div>
          </template>

          <p
            v-if="!recentProjects.length"
            class="text-muted text-sm"
          >
            Todavía no hay proyectos que mostrar.
          </p>

          <ul
            v-else
            class="divide-y divide-default"
          >
            <li
              v-for="project in recentProjects"
              :key="project.id"
              class="flex items-center gap-3 py-2.5"
            >
              <div class="min-w-0 flex-1">
                <p class="text-highlighted truncate text-sm font-medium">
                  {{ localizedText(project.title) || project.slug }}
                </p>
                <p class="text-muted truncate text-xs">
                  {{ project.slug }} · actualizado el {{ adminDate(project.updatedAt) }}
                </p>
              </div>

              <UBadge
                :color="project.status === 'PUBLISHED' ? 'success' : 'neutral'"
                variant="subtle"
                size="sm"
              >
                {{ project.status === 'PUBLISHED' ? 'Publicado' : 'Borrador' }}
              </UBadge>

              <UButton
                :to="`/admin/proyectos/${project.id}`"
                icon="i-lucide-chevron-right"
                color="neutral"
                variant="ghost"
                square
                :aria-label="`Editar ${localizedText(project.title) || project.slug}`"
              />
            </li>
          </ul>
        </UCard>
      </template>
    </template>
  </UDashboardPanel>
</template>
