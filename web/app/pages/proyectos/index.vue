<script setup lang="ts">
// ======================================================================
// /proyectos — listado público
//
// GET /public/projects?lang=... devuelve ProjectSummaryDTO[]. La página
// no inventa proyectos: pinta exactamente lo que la API tiene publicado.
//
// --------------------------------------------------------------------------
// POR QUE EL FILTRO DE STACK ES EN CLIENTE
//
// El contrato (docs/API.md) solo acepta ?lang en este endpoint; no existe
// ?stack. Ante la contradicción entre el prompt y el contrato, manda el
// contrato: se descarga la lista completa y se filtra aquí. Con la lista
// ya cargada el filtro es instantáneo y no cuesta una petición por tecla.
// Si algún día la API añade el filtro server-side, solo cambia este
// computed.
// ======================================================================
const api = useApi()
const lang = useLang()

const { data: projects, error } = await useAsyncData(
  'projects-list',
  () => api.getPublic<ProjectSummaryDTO[]>('/public/projects'),
  { watch: [lang] }
)

// null = sin filtro ("Todos").
const selectedStack = ref<string | null>(null)

// El desplegable de filtros se construye de los stacks que aparecen en los
// proyectos reales: si la API deja de devolver un stack, el botón deja de
// existir, y no al revés.
const stackOptions = computed(() => {
  const set = new Set<string>()

  for (const project of projects.value ?? []) {
    for (const tech of project.stack) {
      set.add(tech)
    }
  }

  return [...set].sort((a, b) => a.localeCompare(b))
})

const filteredProjects = computed(() => {
  if (!selectedStack.value) return projects.value ?? []

  return (projects.value ?? []).filter(project => project.stack.includes(selectedStack.value as string))
})

// Pulsar el stack activo lo quita: el segundo click del toggle vuelve a
// "Todos" sin necesidad de un botón aparte.
function toggleStack(tech: string) {
  selectedStack.value = selectedStack.value === tech ? null : tech
}

useSeoMeta({
  title: 'Proyectos — pepedev',
  description: 'Listado de proyectos con su stack, resumen y enlaces a repositorio y demo.'
})
</script>

<template>
  <UPage>
    <UPageHeader
      title="Proyectos"
      description="Cada proyecto enlaza a su detalle con la galería completa."
    />

    <UPageBody>
      <UAlert
        v-if="error"
        color="error"
        variant="subtle"
        icon="i-lucide-cloud-off"
        title="No se pudieron cargar los proyectos"
        description="La API no está respondiendo. Inténtalo de nuevo en unos segundos."
        class="my-8"
      />

      <template v-else>
        <!-- Chips de filtro. Son botones, no un select: con pocos stacks
             visibles todo a la vista vale más que un desplegable. -->
        <div
          v-if="stackOptions.length"
          class="flex flex-wrap items-center gap-2"
        >
          <span class="text-muted text-sm">Filtrar por stack:</span>

          <UButton
            label="Todos"
            size="xs"
            :color="!selectedStack ? 'primary' : 'neutral'"
            :variant="!selectedStack ? 'soft' : 'ghost'"
            @click="selectedStack = null"
          />

          <UButton
            v-for="tech in stackOptions"
            :key="tech"
            :label="tech"
            size="xs"
            :color="selectedStack === tech ? 'primary' : 'neutral'"
            :variant="selectedStack === tech ? 'soft' : 'ghost'"
            @click="toggleStack(tech)"
          />
        </div>

        <UAlert
          v-if="!filteredProjects.length"
          color="neutral"
          variant="subtle"
          icon="i-lucide-folder-open"
          :title="selectedStack ? `No hay proyectos con «${selectedStack}»` : 'Todavía no hay proyectos publicados'"
          class="my-8"
        />

        <div
          v-else
          class="grid gap-6 py-8 sm:grid-cols-2 lg:grid-cols-3"
        >
          <UCard
            v-for="project in filteredProjects"
            :key="project.slug"
            class="flex flex-col gap-4 overflow-hidden"
          >
            <NuxtLink
              :to="`/proyectos/${project.slug}`"
              class="rounded-md focus-visible:outline-2 focus-visible:outline-primary"
            >
              <NuxtImg
                v-if="project.thumbnailUrl"
                :src="project.thumbnailUrl"
                :alt="project.title"
                width="640"
                height="360"
                class="aspect-video w-full rounded-md object-cover"
              />
            </NuxtLink>

            <div class="flex flex-1 flex-col gap-2">
              <h2 class="font-semibold">
                <NuxtLink
                  :to="`/proyectos/${project.slug}`"
                  class="hover:text-highlighted transition-colors"
                >
                  {{ project.title }}
                </NuxtLink>
              </h2>

              <p class="text-muted text-sm">
                {{ project.summary }}
              </p>

              <div class="mt-auto flex flex-wrap gap-1 pt-2">
                <UBadge
                  v-for="tech in project.stack"
                  :key="tech"
                  color="neutral"
                  variant="subtle"
                  size="sm"
                >
                  {{ tech }}
                </UBadge>
              </div>
            </div>
          </UCard>
        </div>
      </template>
    </UPageBody>
  </UPage>
</template>
