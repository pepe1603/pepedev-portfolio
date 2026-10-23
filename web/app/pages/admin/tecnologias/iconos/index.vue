<script setup lang="ts">
// ======================================================================
// ICONOS
//
// Página de diagnóstico, no de edición. Los iconos NO son datos: salen de
// dos mapas que viven en el código (utils/techIcons.ts), así que aquí no
// hay nada que guardar ni endpoint que llamar para cambiarlos. Lo que esta
// página hace es responder a una pregunta concreta y real:
//
//   "¿qué tecnologías y categorías reales se pintan con icono propio y
//    cuáles caen al genérico?"
//
// Cruza los datos de verdad con los mapas:
//
//   1. Tecnologías: los nombres que aparecen en el `stack` de los
//      proyectos (texto libre) -> techIcon().
//   2. Categorías: las categorías del perfil (texto libre) ->
//      categoryIcon(), que es lo que usa la web pública al agrupar.
//
// Sirve para decidir si merece la pena añadir una entrada al mapa: no se
// puede cubrir un nombre que no se sabe que existe.
// ======================================================================
import type { TableColumn } from '@nuxt/ui'
import { TECH_ICON_MAP, TECH_ICON_FALLBACK, CATEGORY_ICON_FALLBACK, techIcon, categoryIcon } from '~/utils/techIcons'

definePageMeta({ layout: 'dashboard' })

const api = useAdminApi()

// Mismo motivo que en el resto del panel: el $fetch de Nuxt no reenvía las
// cookies durante el SSR, así que esto va solo en cliente.
const { data, status, error, refresh } = await useAsyncData(
  'admin-icons-diagnostic',
  async () => {
    const [projects, profile] = await Promise.all([
      api.get<AdminProject[]>('/projects'),
      api.get<AdminProfile>('/profile')
    ])
    return { projects, profile }
  },
  { server: false }
)

const loadError = computed(() => unwrapApiError(error.value))
const loadErrorMessage = computed(
  () => loadError.value?.problem.detail ?? loadError.value?.message ?? 'No se pudo conectar con el servidor'
)

// ---------------------------------------------------------------------------
// TECNOLOGÍAS: nombres únicos que aparecen en el stack de algún proyecto
// ---------------------------------------------------------------------------
interface TechRow {
  name: string
  icon: string
  covered: boolean
  /** Slugs de los proyectos en cuyo stack aparece. */
  projects: string[]
}

const techRows = computed<TechRow[]>(() => {
  const byName = new Map<string, { label: string, projects: Set<string> }>()

  for (const project of data.value?.projects ?? []) {
    for (const tech of project.stack ?? []) {
      const key = tech.toLowerCase().trim()
      if (!key) continue
      const entry = byName.get(key) ?? { label: tech, projects: new Set<string>() }
      entry.projects.add(project.slug)
      byName.set(key, entry)
    }
  }

  return [...byName.entries()]
    .map(([, entry]) => {
      const icon = techIcon(entry.label)
      return {
        name: entry.label,
        icon,
        covered: icon !== TECH_ICON_FALLBACK,
        projects: [...entry.projects].sort((a, b) => a.localeCompare(b, 'es'))
      }
    })
    .sort((a, b) => a.name.localeCompare(b.name, 'es'))
})

const uncoveredTechs = computed(() => techRows.value.filter(row => !row.covered))

// ---------------------------------------------------------------------------
// CATEGORÍAS: las que existen hoy en el perfil
// ---------------------------------------------------------------------------
interface CategoryRow {
  name: string
  icon: string
  covered: boolean
  count: number
}

const categoryRows = computed<CategoryRow[]>(() => {
  const byCategory = new Map<string, { label: string, count: number }>()

  for (const skill of data.value?.profile?.skills ?? []) {
    const label = skill.category.trim()
    const key = label.toLowerCase()
    const entry = byCategory.get(key) ?? { label, count: 0 }
    entry.count += 1
    byCategory.set(key, entry)
  }

  return [...byCategory.values()]
    .map((entry) => {
      const icon = categoryIcon(entry.label)
      return {
        name: entry.label || 'Sin categoría',
        icon,
        covered: icon !== CATEGORY_ICON_FALLBACK,
        count: entry.count
      }
    })
    .sort((a, b) => a.name.localeCompare(b.name, 'es'))
})

const uncoveredCategories = computed(() => categoryRows.value.filter(row => !row.covered))

// Los datos que ni siquiera llegan a categoría con icono (el mapa de
// categorías solo se pinta en la home, que agrupa las skills del perfil).
const techCoverage = computed(() => ({
  total: techRows.value.length,
  covered: techRows.value.length - uncoveredTechs.value.length
}))

const categoryCoverage = computed(() => ({
  total: categoryRows.value.length,
  covered: categoryRows.value.length - uncoveredCategories.value.length
}))

// ---------------------------------------------------------------------------
// CATÁLOGO COMPLETO DEL MAPA
//
// Lo que ofrece el mapa hoy, independientemente de si se usa. El buscador
// filtra por nombre de tecnología o por nombre de icono.
// ---------------------------------------------------------------------------
const catalogSearch = ref('')

const catalogRows = computed(() =>
  Object.entries(TECH_ICON_MAP)
    .map(([name, icon]) => ({ name, icon }))
    .sort((a, b) => a.name.localeCompare(b.name, 'es'))
)

const visibleCatalog = computed(() => {
  const q = catalogSearch.value.trim().toLowerCase()
  if (!q) return catalogRows.value
  return catalogRows.value.filter(row =>
    row.name.includes(q) || row.icon.toLowerCase().includes(q)
  )
})

// ---------------------------------------------------------------------------
// TABLAS
// ---------------------------------------------------------------------------
const techColumns: TableColumn<TechRow>[] = [
  { accessorKey: 'name', header: 'Tecnología' },
  { accessorKey: 'icon', header: 'Icono' },
  { accessorKey: 'covered', header: 'Cobertura' },
  { accessorKey: 'projects', header: 'Proyectos' }
]

const categoryColumns: TableColumn<CategoryRow>[] = [
  { accessorKey: 'name', header: 'Categoría' },
  { accessorKey: 'icon', header: 'Icono' },
  { accessorKey: 'covered', header: 'Cobertura' },
  { accessorKey: 'count', header: 'Tecnologías' }
]

const catalogColumns: TableColumn<{ name: string, icon: string }>[] = [
  { accessorKey: 'name', header: 'Nombre en el mapa' },
  { accessorKey: 'icon', header: 'Icono' }
]
</script>

<template>
  <UDashboardPanel id="admin-iconos">
    <template #header>
      <UDashboardNavbar title="Iconos">
        <template #leading>
          <UDashboardSidebarCollapse />
        </template>

        <template #right>
          <ColorModeToggle />
          <UButton
            label="Actualizar"
            icon="i-lucide-refresh-cw"
            color="neutral"
            variant="soft"
            :disabled="status === 'pending'"
            @click="refresh()"
          />
        </template>
      </UDashboardNavbar>
    </template>

    <template #body>
      <p class="text-muted max-w-3xl text-sm">
        Los iconos salen de dos mapas que viven en el código, no de la base de datos, así que esta página no
        edita nada: comprueba qué tecnologías y categorías reales tienen icono propio y cuáles caen al genérico.
        Para cubrir un nombre que hoy cae al genérico, hay que añadirlo al mapa.
      </p>

      <!-- Cargando -->
      <div
        v-if="status === 'pending'"
        class="space-y-2"
        aria-hidden="true"
      >
        <USkeleton
          v-for="i in 6"
          :key="i"
          class="h-12 w-full"
        />
      </div>

      <!-- Error de carga -->
      <UCard
        v-else-if="error"
        variant="subtle"
      >
        <div class="flex flex-col items-start gap-3">
          <div>
            <p class="font-medium text-highlighted">
              No se pudieron cargar los datos
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
        <!-- ================= TECNOLOGÍAS ================= -->
        <section class="flex flex-col gap-3">
          <div class="flex flex-wrap items-end justify-between gap-3">
            <div>
              <h2 class="font-semibold text-highlighted">
                Tecnologías de los proyectos
              </h2>
              <p class="text-muted text-sm">
                Nombres que aparecen en el stack de tus proyectos y el icono con que se pintan.
              </p>
            </div>
            <UBadge
              :color="techCoverage.covered === techCoverage.total ? 'success' : 'warning'"
              variant="subtle"
            >
              {{ techCoverage.covered }}/{{ techCoverage.total }} con icono propio
            </UBadge>
          </div>

          <!-- Nombres sin icono propio: la lista accionable -->
          <UAlert
            v-if="uncoveredTechs.length"
            color="warning"
            variant="subtle"
            icon="i-lucide-triangle-alert"
            title="Tecnologías sin icono propio"
            :description="`Estas caen al icono genérico (${TECH_ICON_FALLBACK}): ${uncoveredTechs.map(t => t.name).join(', ')}.`"
          />

          <div
            v-if="techRows.length"
            class="flex flex-col gap-3"
          >
            <!-- Tarjetas por debajo de lg, igual que el resto del panel. -->
            <div class="flex flex-col gap-3 lg:hidden">
              <UCard
                v-for="row in techRows"
                :key="row.name"
              >
                <div class="flex flex-col gap-2">
                  <div class="flex flex-wrap items-center gap-2">
                    <span class="font-medium text-highlighted">
                      {{ row.name }}
                    </span>
                    <UBadge
                      :color="row.covered ? 'success' : 'warning'"
                      variant="subtle"
                      size="sm"
                    >
                      {{ row.covered ? 'Propio' : 'Genérico' }}
                    </UBadge>
                  </div>

                  <div class="flex items-center gap-2">
                    <UIcon
                      :name="row.icon"
                      class="size-5 shrink-0"
                    />
                    <code class="text-muted text-xs">{{ row.icon }}</code>
                  </div>

                  <p class="text-muted text-xs">
                    {{ row.projects.join(', ') }}
                  </p>
                </div>
              </UCard>
            </div>

            <UTable
              class="hidden rounded-xl border border-default lg:block [&_tbody_tr]:transition-colors [&_tbody_tr:hover]:bg-elevated/50"
              :data="techRows"
              :columns="techColumns"
              aria-label="Iconos de tecnologías"
            >
              <template #name-cell="{ row }">
                <span class="font-medium text-highlighted">
                  {{ row.original.name }}
                </span>
              </template>

              <template #icon-cell="{ row }">
                <div class="flex items-center gap-2">
                  <UIcon
                    :name="row.original.icon"
                    class="size-5 shrink-0"
                  />
                  <code class="text-muted text-xs">{{ row.original.icon }}</code>
                </div>
              </template>

              <template #covered-cell="{ row }">
                <UBadge
                  :color="row.original.covered ? 'success' : 'warning'"
                  variant="subtle"
                  size="sm"
                >
                  {{ row.original.covered ? 'Propio' : 'Genérico' }}
                </UBadge>
              </template>

              <template #projects-cell="{ row }">
                <span class="text-muted text-xs">
                  {{ row.original.projects.join(', ') }}
                </span>
              </template>
            </UTable>
          </div>

          <UCard
            v-else
            variant="subtle"
          >
            <p class="text-muted text-sm">
              Ningún proyecto tiene stack todavía, así que no hay tecnologías que diagnosticar.
            </p>
          </UCard>
        </section>

        <!-- ================= CATEGORÍAS ================= -->
        <section class="flex flex-col gap-3">
          <div class="flex flex-wrap items-end justify-between gap-3">
            <div>
              <h2 class="font-semibold text-highlighted">
                Categorías del stack
              </h2>
              <p class="text-muted text-sm">
                La web pública agrupa tus tecnologías por categoría y pinta el icono de la categoría, no el de
                cada tecnología.
              </p>
            </div>
            <UBadge
              :color="categoryCoverage.covered === categoryCoverage.total ? 'success' : 'warning'"
              variant="subtle"
            >
              {{ categoryCoverage.covered }}/{{ categoryCoverage.total }} con icono propio
            </UBadge>
          </div>

          <UAlert
            v-if="uncoveredCategories.length"
            color="warning"
            variant="subtle"
            icon="i-lucide-triangle-alert"
            title="Categorías sin icono propio"
            :description="`Estas caen al icono genérico (${CATEGORY_ICON_FALLBACK}): ${uncoveredCategories.map(c => c.name).join(', ')}.`"
          />

          <div
            v-if="categoryRows.length"
            class="flex flex-col gap-3"
          >
            <!-- Tarjetas por debajo de lg. -->
            <div class="flex flex-col gap-3 lg:hidden">
              <UCard
                v-for="row in categoryRows"
                :key="row.name"
              >
                <div class="flex flex-col gap-2">
                  <div class="flex flex-wrap items-center gap-2">
                    <span class="font-medium text-highlighted">
                      {{ row.name }}
                    </span>
                    <UBadge
                      :color="row.covered ? 'success' : 'warning'"
                      variant="subtle"
                      size="sm"
                    >
                      {{ row.covered ? 'Propio' : 'Genérico' }}
                    </UBadge>
                  </div>

                  <div class="flex items-center gap-2">
                    <UIcon
                      :name="row.icon"
                      class="size-5 shrink-0"
                    />
                    <code class="text-muted text-xs">{{ row.icon }}</code>
                  </div>

                  <p class="text-muted text-xs">
                    {{ row.count }} {{ row.count === 1 ? 'tecnología' : 'tecnologías' }}
                  </p>
                </div>
              </UCard>
            </div>

            <UTable
              class="hidden overflow-hidden rounded-xl border border-default lg:block [&_tbody_tr]:transition-colors [&_tbody_tr:hover]:bg-elevated/50"
              :data="categoryRows"
              :columns="categoryColumns"
              aria-label="Iconos de categorías"
            >
              <template #name-cell="{ row }">
                <span class="font-medium text-highlighted">
                  {{ row.original.name }}
                </span>
              </template>

              <template #icon-cell="{ row }">
                <div class="flex items-center gap-2">
                  <UIcon
                    :name="row.original.icon"
                    class="size-5 shrink-0"
                  />
                  <code class="text-muted text-xs">{{ row.original.icon }}</code>
                </div>
              </template>

              <template #covered-cell="{ row }">
                <UBadge
                  :color="row.original.covered ? 'success' : 'warning'"
                  variant="subtle"
                  size="sm"
                >
                  {{ row.original.covered ? 'Propio' : 'Genérico' }}
                </UBadge>
              </template>

              <template #count-cell="{ row }">
                <span class="text-muted text-sm">
                  {{ row.original.count }}
                </span>
              </template>
            </UTable>
          </div>

          <UCard
            v-else
            variant="subtle"
          >
            <p class="text-muted text-sm">
              El perfil no tiene tecnologías, así que no hay categorías que diagnosticar.
            </p>
          </UCard>
        </section>

        <!-- ================= CATÁLOGO ================= -->
        <section class="flex flex-col gap-3">
          <div>
            <h2 class="font-semibold text-highlighted">
              Catálogo del mapa
            </h2>
            <p class="text-muted text-sm">
              Todas las tecnologías para las que ya hay icono definido ({{ catalogRows.length }}), se usen o no.
            </p>
          </div>

          <UInput
            v-model="catalogSearch"
            icon="i-lucide-search"
            placeholder="Buscar en el catálogo…"
            class="w-full sm:max-w-xs"
            aria-label="Buscar en el catálogo de iconos"
          />

          <!-- Tarjetas por debajo de lg; el catalogo tiene muchas filas, pero
               se uniforma con el resto del panel. -->
          <div class="flex flex-col gap-3 lg:hidden">
            <UCard
              v-for="row in visibleCatalog"
              :key="row.name"
            >
              <div class="flex items-center justify-between gap-3">
                <span class="text-highlighted">{{ row.name }}</span>
                <div class="flex items-center gap-2">
                  <UIcon
                    :name="row.icon"
                    class="size-5 shrink-0"
                  />
                  <code class="text-muted text-xs">{{ row.icon }}</code>
                </div>
              </div>
            </UCard>
          </div>

          <UTable
            class="hidden lg:block"
            :data="visibleCatalog"
            :columns="catalogColumns"
            aria-label="Catálogo de iconos"
          >
            <template #name-cell="{ row }">
              <span class="text-highlighted">{{ row.original.name }}</span>
            </template>

            <template #icon-cell="{ row }">
              <div class="flex items-center gap-2">
                <UIcon
                  :name="row.original.icon"
                  class="size-5 shrink-0"
                />
                <code class="text-muted text-xs">{{ row.original.icon }}</code>
              </div>
            </template>
          </UTable>
        </section>
      </template>
    </template>
  </UDashboardPanel>
</template>
