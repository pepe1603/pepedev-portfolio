<script setup lang="ts">
const api = useApi()
const lang = useLang()

const { data: projects, error } = await useAsyncData(
  'projects-list',
  () => api.getPublic<ProjectSummaryDTO[]>('/public/projects'),
  { watch: [lang] }
)

const selectedStack = ref<string | null>(null)

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

  return (projects.value ?? []).filter(
    project => project.stack.includes(selectedStack.value!)
  )
})

function toggleStack(tech: string) {
  selectedStack.value = selectedStack.value === tech ? null : tech
}

useSeoMeta({
  title: 'Proyectos — pepedev',
  description: 'Explora proyectos, tecnologías y desarrollos publicados.'
})
</script>

<template>
  <UPage>
    <UPageHeader
      title="Proyectos"
      description="Una selección de proyectos, herramientas y experimentos."
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
        <!-- Filtros -->
        <section
          v-if="stackOptions.length"
          aria-label="Filtrar proyectos por tecnología"
          class="mb-2 flex flex-col gap-3"
        >
          <div class="flex flex-wrap items-center gap-2">
            <span class="mr-1 text-sm text-muted">
              Tecnologías
            </span>

            <UButton
              label="Todos"
              size="sm"
              :color="selectedStack === null ? 'primary' : 'neutral'"
              :variant="selectedStack === null ? 'soft' : 'ghost'"
              @click="selectedStack = null"
            />

            <UButton
              v-for="tech in stackOptions"
              :key="tech"
              :label="tech"
              size="sm"
              :color="selectedStack === tech ? 'primary' : 'neutral'"
              :variant="selectedStack === tech ? 'soft' : 'ghost'"
              @click="toggleStack(tech)"
            />
          </div>

          <p class="text-xs text-muted">
            {{ filteredProjects.length }}
            {{ filteredProjects.length === 1 ? 'proyecto' : 'proyectos' }}
            {{ selectedStack ? `con ${selectedStack}` : 'publicados' }}
          </p>
        </section>

        <!-- Estado vacío -->
        <UAlert
          v-if="!filteredProjects.length"
          color="neutral"
          variant="subtle"
          :icon="selectedStack ? 'i-lucide-search-x' : 'i-lucide-folder-open'"
          :title="
            selectedStack
              ? `No hay proyectos con «${selectedStack}»`
              : 'Todavía no hay proyectos publicados'
          "
          :description="
            selectedStack
              ? 'Prueba otra tecnología o muestra todos los proyectos.'
              : 'Los nuevos proyectos aparecerán aquí cuando se publiquen.'
          "
          class="my-8"
        >
          <template
            v-if="selectedStack"
            #actions
          >
            <UButton
              label="Ver todos"
              color="neutral"
              variant="outline"
              size="sm"
              @click="selectedStack = null"
            />
          </template>
        </UAlert>

        <!-- Grid -->
        <div
          v-else
          class="grid grid-cols-1 gap-6 py-8 sm:grid-cols-2 sm:gap-7 xl:gap-8"
        >
          <NuxtLink
            v-for="project in filteredProjects"
            :key="project.slug"
            :to="`/proyectos/${project.slug}`"
            class="group block min-w-0 rounded-2xl focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-primary"
            :aria-label="`Ver proyecto: ${project.title}`"
          >
            <UCard
              :ui="{
                root: 'h-full overflow-hidden rounded-2xl border border-default bg-default transition-[border-color,transform,box-shadow] duration-300 ease-out group-hover:-translate-y-1 group-hover:border-primary/50 group-hover:shadow-lg motion-reduce:transform-none motion-reduce:transition-none',
                body: 'flex h-full flex-col gap-4 p-4 sm:p-5 lg:p-6'
              }"
            >
              <!-- Miniatura -->
              <div class="relative overflow-hidden rounded-xl bg-elevated">
                <NuxtImg
                  v-if="project.thumbnailUrl"
                  :src="project.thumbnailUrl"
                  :alt="project.title"
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
                    class="size-12"
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
                      class="mr-1 size-3 sm:size-4"
                    />
                    Destacado
                  </UBadge>
                </div>
              </div>

              <!-- Información esencial -->
              <div class="flex flex-1 flex-col gap-3">
                <div class="flex items-start justify-between gap-3">
                  <h2
                    class="min-w-0 text-lg font-semibold tracking-tight text-highlighted transition-colors group-hover:text-primary sm:text-xl"
                  >
                    {{ project.title }}
                  </h2>

                  <UIcon
                    name="i-lucide-arrow-up-right"
                    class="mt-1 size-5 shrink-0 text-muted transition-transform duration-300 group-hover:-translate-y-0.5 group-hover:translate-x-0.5 group-hover:text-primary motion-reduce:transition-none"
                    aria-hidden="true"
                  />
                </div>

                <p
                  v-if="project.subtitle"
                  class="text-sm font-medium text-muted"
                >
                  {{ project.subtitle }}
                </p>

                <p class="line-clamp-3 text-sm leading-6 text-muted">
                  {{ project.summary }}
                </p>

                <!-- Tecnologías -->
                <div
                  v-if="project.stack.length"
                  class="mt-auto flex flex-wrap items-center gap-2 pt-2"
                  aria-label="Tecnologías utilizadas"
                >
                  <UBadge
                    v-for="tech in project.stack.slice(0, 3)"
                    :key="tech"
                    color="neutral"
                    variant="subtle"
                    size="md"
                  >
                    {{ tech }}
                  </UBadge>

                  <span
                    v-if="project.stack.length > 3"
                    class="text-xs text-muted"
                  >
                    +{{ project.stack.length - 3 }}
                  </span>
                </div>

                <div
                  class="flex items-center gap-2 border-t border-default pt-3 text-sm font-medium text-primary"
                >
                  <span>Explorar proyecto</span>
                  <UIcon
                    name="i-lucide-arrow-right"
                    class="size-4 transition-transform group-hover:translate-x-1 motion-reduce:transition-none"
                    aria-hidden="true"
                  />
                </div>
              </div>
            </UCard>
          </NuxtLink>
        </div>
      </template>
    </UPageBody>
  </UPage>
</template>
