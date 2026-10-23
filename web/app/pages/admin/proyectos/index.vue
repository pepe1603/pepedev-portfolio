<script setup lang="ts">
import type { TableColumn, DropdownMenuItem } from '@nuxt/ui'
import { toApiError, unwrapApiError } from '~/composables/useApi'
import { useTechIcons } from '~/composables/useTechIcons'
import { CARD_INTERACTIVA } from '~/utils/cardStyles'

const { getTechIcon } = useTechIcons()

definePageMeta({ layout: 'dashboard' })

const api = useAdminApi()
const { notify } = useAppNotify()

// POR QUÉ NO SE CARGA EN SSR
//
// El $fetch de Nuxt no reenvía las cookies del navegador durante el render
// del servidor, así que la petición llegaría al proxy sin sesión y respondería
// 401. Se podría resolver con useRequestFetch, pero no merece la pena: es
// contenido privado y volátil, sin caché de SSR que aprovechar. El servidor
// pinta el armazón del panel y la tabla entra al poco de hidratar.
const {
  data: projects,
  status,
  error,
  refresh
} = await useAsyncData(
  'admin-projects',
  () => api.get<AdminProject[]>('/projects'),
  { server: false }
)

// El estado de error se decide por `error` y NO por su tipo: si la API no
// responde (red caída, Spring parado) el fallo no llega como ProblemDetail y
// unwrapApiError devuelve null. Sin esta comprobación la página pintaría
// "Todavía no hay proyectos", que es mentira: no se pudo consultar.
const loadError = computed(() => unwrapApiError(error.value))

const loadErrorMessage = computed(
  () => loadError.value?.problem.detail ?? loadError.value?.message ?? 'No se pudo conectar con el servidor'
)

// ---------------------------------------------------------------------------
// FILTROS (client-side)
//
// GET /admin/projects devuelve la lista COMPLETA, sin paginar (lo ordena la
// API por sortOrder ASC → createdAt ASC), así que buscar y filtrar aquí es
// correcto y evita un round trip por cada tecla.
// ---------------------------------------------------------------------------
const search = ref('')
const statusFilter = ref<'todos' | ProjectStatus>('todos')

const statusOptions = [
  { label: 'Todos los estados', value: 'todos' },
  { label: 'Borradores', value: 'DRAFT' },
  { label: 'Publicados', value: 'PUBLISHED' }
]

const visibleProjects = computed(() => {
  const needle = search.value.trim().toLowerCase()
  return (projects.value ?? []).filter((project) => {
    if (statusFilter.value !== 'todos' && project.status !== statusFilter.value) {
      return false
    }
    if (!needle) {
      return true
    }
    return [localizedText(project.title), localizedText(project.subtitle), project.slug, ...project.stack]
      .some(field => field.toLowerCase().includes(needle))
  })
})

const filtersActive = computed(() => search.value.trim() !== '' || statusFilter.value !== 'todos')

function clearFilters() {
  search.value = ''
  statusFilter.value = 'todos'
}

// ---------------------------------------------------------------------------
// FORMATEO
//
// El texto bilingüe lo pinta localizedText() y las fechas adminDate(), ambos
// de utils/adminTypes.ts: mismo criterio en todo el panel. `updatedAt` es un
// Instant ISO-8601 completo, así que adminDate no toca nada.
// ---------------------------------------------------------------------------

// ---------------------------------------------------------------------------
// COLUMNAS
// ---------------------------------------------------------------------------
const columns: TableColumn<AdminProject>[] = [
  { id: 'title', header: 'Proyecto' },
  { id: 'status', header: 'Estado' },
  { id: 'featured', header: 'Destacado' },
  { id: 'stack', header: 'Stack' },
  { id: 'updatedAt', header: 'Actualizado' },
  { id: 'actions', header: '' }
]

// ---------------------------------------------------------------------------
// ACCIONES POR FILA
// ---------------------------------------------------------------------------
const pendingId = ref<string | null>(null)
const deleteTarget = ref<AdminProject | null>(null)
const deleteOpen = ref(false)
const deleting = ref(false)

function isPending(id: string): boolean {
  return pendingId.value === id
}

async function togglePublish(project: AdminProject) {
  const goingLive = project.status === 'DRAFT'
  pendingId.value = project.id
  try {
    await api.patch(`/projects/${project.id}/${goingLive ? 'publish' : 'unpublish'}`)
    notify(goingLive ? 'Proyecto publicado' : 'Proyecto despublicado', 'success')
    await refresh()
  } catch (err) {
    notify(toApiError(err).message, 'error')
  } finally {
    pendingId.value = null
  }
}

function askDelete(project: AdminProject) {
  deleteTarget.value = project
  deleteOpen.value = true
}

async function confirmDelete() {
  const project = deleteTarget.value
  if (!project) return

  deleting.value = true
  try {
    // DELETE responde 204 sin cuerpo. El backend, además, borra del bucket
    // la miniatura y la galería que dejen de estar referenciadas.
    await api.remove(`/projects/${project.id}`)
    notify('Proyecto eliminado', 'success')
    deleteOpen.value = false
    deleteTarget.value = null
    await refresh()
  } catch (err) {
    notify(toApiError(err).message, 'error')
  } finally {
    deleting.value = false
  }
}

function openProject(id: string) {
  return navigateTo(`/admin/proyectos/${id}`)
}

function rowActions(project: AdminProject): DropdownMenuItem[][] {
  return [
    [
      {
        label: 'Editar',
        icon: 'i-lucide-pencil',
        onSelect: () => navigateTo(`/admin/proyectos/${project.id}`)
      },
      {
        label: project.status === 'DRAFT' ? 'Publicar' : 'Despublicar',
        icon: project.status === 'DRAFT' ? 'i-lucide-eye' : 'i-lucide-eye-off',
        disabled: isPending(project.id),
        onSelect: () => togglePublish(project)
      },
      {
        label: 'Borrar',
        icon: 'i-lucide-trash-2',
        color: 'error',
        disabled: isPending(project.id),
        onSelect: () => askDelete(project)
      }
    ]
  ]
}
</script>

<template>
  <UDashboardPanel id="admin-proyectos">
    <template #header>
      <UDashboardNavbar title="Proyectos">
        <template #leading>
          <UDashboardSidebarCollapse />
        </template>

        <template #right>
          <ColorModeToggle />
          <UButton
            label="Nuevo proyecto"
            icon="i-lucide-plus"
            to="/admin/proyectos/nuevo"
          />
        </template>
      </UDashboardNavbar>
    </template>

    <template #body>
      <!-- Filtros. El buscador filtra título, subtítulo, slug y stack. -->
      <div class="flex flex-col gap-3 sm:flex-row sm:items-center">
        <UInput
          v-model="search"
          icon="i-lucide-search"
          placeholder="Buscar por título, slug o stack…"
          class="w-full sm:max-w-xs"
          aria-label="Buscar proyectos"
        />

        <USelect
          v-model="statusFilter"
          :items="statusOptions"
          class="w-full sm:w-48"
          aria-label="Filtrar por estado"
        />

        <UButton
          v-if="filtersActive"
          label="Limpiar filtros"
          icon="i-lucide-x"
          color="neutral"
          variant="soft"
          class="self-start"
          @click="clearFilters"
        />

        <p
          v-else-if="projects?.length"
          class="text-muted ms-auto text-sm"
        >
          {{ projects.length }} {{ projects.length === 1 ? 'proyecto' : 'proyectos' }}
        </p>
      </div>

      <!-- Cargando. -->
      <div
        v-if="status === 'pending'"
        class="space-y-2"
        aria-hidden="true"
      >
        <USkeleton
          v-for="i in 5"
          :key="i"
          class="h-12 w-full"
        />
      </div>

      <!-- Error de carga. El 401 ya abre el modal de sesión caducada solo. -->
      <UCard
        v-else-if="error"
        variant="subtle"
      >
        <div class="flex flex-col items-start gap-3">
          <div>
            <p class="font-medium text-highlighted">
              No se pudieron cargar los proyectos
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

      <!-- Sin proyectos todavía. -->
      <UCard
        v-else-if="!projects?.length"
        variant="subtle"
      >
        <div class="flex flex-col items-start gap-3">
          <div>
            <p class="font-medium text-highlighted">
              Todavía no hay proyectos
            </p>
            <p class="text-muted text-sm">
              Se crean como borrador: no se publican hasta que lo decidas.
            </p>
          </div>
          <UButton
            label="Crear el primero"
            icon="i-lucide-plus"
            to="/admin/proyectos/nuevo"
          />
        </div>
      </UCard>

      <!-- Ningún resultado de los filtros. -->
      <UCard
        v-else-if="!visibleProjects.length"
        variant="subtle"
      >
        <div class="flex flex-col items-start gap-3">
          <div>
            <p class="font-medium text-highlighted">
              Ningún proyecto coincide
            </p>
            <p class="text-muted text-sm">
              Prueba con otro término o quita los filtros.
            </p>
          </div>
          <UButton
            label="Limpiar filtros"
            icon="i-lucide-x"
            color="neutral"
            variant="soft"
            @click="clearFilters"
          />
        </div>
      </UCard>

      <div
        v-else
        class="flex flex-col gap-3"
      >
        <!-- Tarjetas por debajo de lg: la tabla mide ~735 px y en pantallas
             estrechas el menu de acciones quedaba fuera de vista. Se usa lg
             (1024 px) y no sm porque el corte de 640 px seguiria dejando la
             tabla apretada entre 640 y 735 px. -->
        <div class="flex flex-col gap-3 lg:hidden">
          <UCard
            v-for="project in visibleProjects"
            :key="project.id"
            class="cursor-pointer"
            :ui="{
              root: CARD_INTERACTIVA,
              body: 'flex flex-col gap-3 p-4 sm:p-5'
            }"
            role="button"
            tabindex="0"
            :aria-label="`Editar proyecto: ${localizedText(project.title) || project.slug}`"
            @click="openProject(project.id)"
            @keydown.enter.prevent="openProject(project.id)"
            @keydown.space.prevent="openProject(project.id)"
          >
            <!-- Miniatura -->
            <div class="relative overflow-hidden rounded-xl bg-elevated">
              <NuxtImg
                v-if="project.thumbnailUrl"
                :src="project.thumbnailUrl"
                :alt="localizedText(project.title) || ''"
                width="960"
                height="540"
                loading="lazy"
                class="aspect-video w-full object-cover transition-transform duration-500 group-hover:scale-[1.03] motion-reduce:transition-none"
              />
              <div
                v-else
                class="flex aspect-video w-full items-center justify-center text-muted"
                aria-hidden="true"
              >
                <UIcon
                  name="i-lucide-panels-top-left"
                  class="size-10"
                />
              </div>
              <div
                v-if="project.featured"
                class="absolute left-3 top-3"
              >
                <UBadge
                  color="primary"
                  variant="solid"
                  size="sm"
                >
                  <UIcon
                    name="i-lucide-star"
                    class="mr-1 size-3"
                  />
                  Destacado
                </UBadge>
              </div>
            </div>

            <div class="flex flex-col gap-2">
              <div class="flex items-start justify-between gap-3">
                <span class="min-w-0 text-lg font-semibold tracking-tight text-highlighted transition-colors group-hover:text-primary">
                  {{ localizedText(project.title) || '(sin título)' }}
                </span>
                <UIcon
                  name="i-lucide-pencil"
                  class="mt-1 size-4 shrink-0 text-muted transition-transform duration-300 group-hover:translate-x-0.5 group-hover:text-primary motion-reduce:transition-none"
                  aria-hidden="true"
                />
              </div>

              <div class="flex flex-wrap items-center gap-2">
                <UBadge
                  :color="project.status === 'PUBLISHED' ? 'success' : 'neutral'"
                  variant="subtle"
                  size="sm"
                >
                  {{ project.status === 'PUBLISHED' ? 'Publicado' : 'Borrador' }}
                </UBadge>
                <code class="text-muted truncate text-xs">{{ project.slug }}</code>
              </div>

              <div
                v-if="project.stack.length"
                class="flex flex-wrap items-center gap-1.5"
              >
                <div
                  v-for="tech in project.stack.slice(0, 3)"
                  :key="tech"
                  class="flex items-center gap-1.5 rounded-md border border-default bg-elevated px-2 py-0.5 text-xs font-medium text-muted transition-colors group-hover:border-primary/40 group-hover:text-primary"
                >
                  <UIcon
                    :name="getTechIcon(tech)"
                    class="size-3.5"
                  />
                  <span>{{ tech }}</span>
                </div>
                <span
                  v-if="project.stack.length > 3"
                  class="text-dimmed self-center text-xs"
                >
                  +{{ project.stack.length - 3 }}
                </span>
              </div>

              <p class="text-muted text-xs">
                Actualizado el {{ adminDate(project.updatedAt) }}
              </p>
            </div>

            <div class="flex items-center gap-2 border-t border-default pt-3 text-sm font-medium text-primary">
              <span>Editar</span>
              <UIcon
                name="i-lucide-arrow-right"
                class="size-4 transition-transform group-hover:translate-x-1 motion-reduce:transition-none"
                aria-hidden="true"
              />
            </div>

            <template #footer>
              <div
                class="flex w-full justify-end"
                @click.stop
                @keydown.stop
              >
                <UDropdownMenu
                  :items="rowActions(project)"
                  :content="{ align: 'end' }"
                >
                  <UButton
                    label="Acciones"
                    icon="i-lucide-ellipsis"
                    color="neutral"
                    variant="soft"
                    size="sm"
                    block
                    aria-label="Acciones del proyecto"
                  />
                </UDropdownMenu>
              </div>
            </template>
          </UCard>
        </div>

        <UTable
          class="hidden lg:block"
          :data="visibleProjects"
          :columns="columns"
          aria-label="Listado de proyectos"
        >
          <template #title-cell="{ row }">
            <div class="min-w-0">
              <p class="truncate font-medium text-highlighted">
                {{ localizedText(row.original.title) || '(sin título)' }}
              </p>
              <p class="text-muted truncate text-xs">
                {{ row.original.slug }}
              </p>
            </div>
          </template>

          <template #status-cell="{ row }">
            <UBadge
              :color="row.original.status === 'PUBLISHED' ? 'success' : 'neutral'"
              variant="subtle"
              size="sm"
            >
              {{ row.original.status === 'PUBLISHED' ? 'Publicado' : 'Borrador' }}
            </UBadge>
          </template>

          <template #featured-cell="{ row }">
            <UIcon
              v-if="row.original.featured"
              name="i-lucide-star"
              class="text-warning size-4"
              aria-label="Destacado"
            />
            <span
              v-else
              class="text-dimmed text-xs"
              aria-label="No destacado"
            >—</span>
          </template>

          <template #stack-cell="{ row }">
            <div class="flex flex-wrap gap-1">
              <UBadge
                v-for="tech in row.original.stack.slice(0, 3)"
                :key="tech"
                color="neutral"
                variant="outline"
                size="sm"
              >
                {{ tech }}
              </UBadge>
              <span
                v-if="row.original.stack.length > 3"
                class="text-dimmed self-center text-xs"
              >
                +{{ row.original.stack.length - 3 }}
              </span>
            </div>
          </template>

          <template #updatedAt-cell="{ row }">
            <span class="text-muted text-sm">
              {{ adminDate(row.original.updatedAt) }}
            </span>
          </template>

          <template #actions-cell="{ row }">
            <UDropdownMenu
              :items="rowActions(row.original)"
              :content="{ align: 'end' }"
            >
              <UButton
                icon="i-lucide-ellipsis"
                color="neutral"
                variant="ghost"
                square
                aria-label="Acciones del proyecto"
              />
            </UDropdownMenu>
          </template>
        </UTable>
      </div>

      <!-- Borrado. El modal se cierra con ESC o clic fuera, y no deja
           restos: el backend limpia del bucket lo que deje de referenciarse. -->
      <UModal
        v-model:open="deleteOpen"
        title="¿Borrar proyecto?"
        :dismissible="!deleting"
        :close="!deleting"
      >
        <template #body>
          <p
            v-if="deleteTarget"
            class="text-muted text-sm"
          >
            Se eliminará
            <span class="font-medium text-highlighted">{{ localizedText(deleteTarget.title) }}</span>
            de forma permanente, junto con su miniatura y su galería.
            Esta acción no se puede deshacer.
          </p>
        </template>

        <template #footer>
          <div class="flex w-full justify-end gap-2">
            <UButton
              label="Cancelar"
              color="neutral"
              variant="soft"
              :disabled="deleting"
              @click="deleteOpen = false"
            />
            <UButton
              label="Borrar"
              icon="i-lucide-trash-2"
              color="error"
              :loading="deleting"
              @click="confirmDelete"
            />
          </div>
        </template>
      </UModal>
    </template>
  </UDashboardPanel>
</template>
