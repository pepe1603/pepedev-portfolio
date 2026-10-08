<script setup lang="ts">
// ======================================================================
// /proyectos/[slug] — detalle público
//
// GET /public/projects/{slug}?lang=... devuelve ProjectDetailDTO. Un slug
// inexistente (o sin versión publicada) responde 404 desde la API, y ese
// 404 se convierte aquí en la página de error real de Nuxt (error.vue),
// no en un aviso dentro de una página vacía: el usuario debe poder
// distinguir "este proyecto no existe" de "la API está caída".
//
// --------------------------------------------------------------------------
// descriptionMd: Markdown a HTML
//
// El backend guarda y devuelve Markdown; la UI no puede pintarlo tal cual.
// marked convierte y DOMPurify sanea ANTES de v-html: sin el saneado,
// cualquier <script> o handler que apareciera en el contenido se
// ejecutaria en el navegador del visitante. isomorphic-dompurify en vez
// de dompurify puro porque esta pagina se renderiza también en el servidor
// y dompurify necesita un DOM que allí no existe.
// ======================================================================
import DOMPurify from 'isomorphic-dompurify'
import { marked } from 'marked'

const route = useRoute()
const api = useApi()
const lang = useLang()

const slug = computed(() => String(route.params.slug))

// La key sigue al slug: al navegar entre proyectos en el cliente hay que
// re-lanzar la petición, igual que con el idioma.
const { data: project, error: fetchError } = await useAsyncData(
  () => `project-${slug.value}`,
  () => api.getPublic<ProjectDetailDTO>(`/public/projects/${slug.value}`),
  { watch: [lang] }
)

// Solo el 404 del backend saca a error.vue. El resto (red caída, status 0)
// se enseña dentro de la propia página sin salir de la ruta.
if (fetchError.value instanceof ApiError && fetchError.value.status === 404) {
  throw createError({
    statusCode: 404,
    statusMessage: 'Este proyecto no existe o no está publicado.'
  })
}

const descriptionHtml = computed(() => {
  const md = project.value?.descriptionMd
  if (!md) return ''

  // marked.parse con async: false devuelve string, no promesa; el sanitize
  // va fuera porque DOMPurify trabaja sobre el HTML ya convertido.
  return DOMPurify.sanitize(marked.parse(md, { async: false }))
})

// periodStart/periodEnd llegan como 'yyyy-MM-dd'. Fin null = en curso.
// El formato de mes abreviado depende del idioma activo, que es el mismo
// que el contenido de la API.
const period = computed(() => {
  const p = project.value
  if (!p || (!p.periodStart && !p.periodEnd)) return null

  const locale = lang.value === 'en' ? 'en' : 'es'
  const formatter = new Intl.DateTimeFormat(locale, { month: 'short', year: 'numeric' })
  const to = (iso: string) => formatter.format(new Date(`${iso}T00:00:00`))

  if (p.periodStart && p.periodEnd) return `${to(p.periodStart)} — ${to(p.periodEnd)}`
  if (p.periodStart) return `${to(p.periodStart)} — ${locale === 'en' ? 'present' : 'actualidad'}`

  return to(p.periodEnd as string)
})

useSeoMeta({
  title: () => (project.value ? `${project.value.title} — pepedev` : 'Proyecto — pepedev'),
  description: () => project.value?.summary ?? 'Detalle del proyecto.'
})
</script>

<template>
  <UPage>
    <UPageHeader
      :title="project?.title ?? 'Proyecto'"
      :description="project?.subtitle"
    >
      <template #headline>
        <NuxtLink
          to="/proyectos"
          class="hover:text-highlighted inline-flex items-center gap-1 transition-colors"
        >
          <UIcon
            name="i-lucide-arrow-left"
            class="size-4"
          />
          Volver a proyectos
        </NuxtLink>
      </template>
    </UPageHeader>

    <UPageBody>
      <UAlert
        v-if="fetchError"
        color="error"
        variant="subtle"
        icon="i-lucide-cloud-off"
        title="No se pudo cargar el proyecto"
        description="La API no está respondiendo. Inténtalo de nuevo en unos segundos."
        class="my-8"
      />

      <article
        v-else-if="project"
        class="flex flex-col gap-8"
      >
        <!-- Meta: periodo y stack en una fila; el periodo no pinta nada si
             el proyecto no tiene fechas. -->
        <div class="text-muted flex flex-wrap items-center gap-x-4 gap-y-2 text-sm">
          <span
            v-if="period"
            class="inline-flex items-center gap-1"
          >
            <UIcon
              name="i-lucide-calendar"
              class="size-4"
            />
            {{ period }}
          </span>

          <span class="flex flex-wrap gap-1">
            <UBadge
              v-for="tech in project.stack"
              :key="tech"
              color="neutral"
              variant="subtle"
              size="sm"
            >
              {{ tech }}
            </UBadge>
          </span>
        </div>

        <NuxtImg
          v-if="project.thumbnailUrl"
          :src="project.thumbnailUrl"
          :alt="project.title"
          width="1280"
          height="720"
          class="aspect-video w-full rounded-xl object-cover"
        />

        <!-- eslint-disable vue/no-v-html -->
        <!-- El HTML no sale de un visitante: viene de descriptionMd
             (contenido del admin), convertido por marked y saneado con
             DOMPurify.sanitize en el script antes de llegar aquí. Sin ese
             paso el v-html sería XSS, y con él no hay vector abierto. -->
        <div
          v-if="descriptionHtml"
          class="text-muted flex flex-col gap-4 [&_a]:text-primary [&_code]:bg-elevated [&_code]:rounded [&_code]:px-1 [&_h2]:text-lg [&_h2]:font-semibold [&_h3]:font-semibold [&_ul]:list-disc [&_ul]:ps-5"
          v-html="descriptionHtml"
        />
        <!-- eslint-enable vue/no-v-html -->

        <GalleryCarousel
          v-if="project.gallery.length"
          :images="project.gallery.map(image => image.url)"
          :alt="project.title"
        />

        <div class="flex flex-wrap gap-2">
          <UButton
            v-if="project.repoUrl"
            :href="project.repoUrl"
            target="_blank"
            label="Repositorio"
            icon="i-lucide-github"
            color="neutral"
            variant="soft"
          />

          <UButton
            v-if="project.demoUrl"
            :href="project.demoUrl"
            target="_blank"
            label="Demo"
            icon="i-lucide-external-link"
          />
        </div>
      </article>
    </UPageBody>
  </UPage>
</template>
