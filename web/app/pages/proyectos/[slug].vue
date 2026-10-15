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
import { useTechIcons } from '~/composables/useTechIcons'

const route = useRoute()
const api = useApi()
const lang = useLang()
const { getTechIcon } = useTechIcons()

const slug = computed(() => String(route.params.slug))

// La key sigue al slug: al navegar entre proyectos en el cliente hay que
// re-lanzar la petición, igual que con el idioma.
const { data: project, error: fetchError } = await useAsyncData(
  () => `project-${slug.value}`,
  () => api.getPublic<ProjectDetailDTO>(`/public/projects/${slug.value}`),
  { watch: [lang] }
)

// Solo el 404 del backend saca a error.vue. El resto (red caída, status 0)
// se enseña dentro de la propia página sin salir de la ruta. El desenvuelvo
// con unwrapApiError es porque useAsyncData envuelve el ApiError en un
// H3Error; comprobar `instanceof` directamente no llega nunca.
if (unwrapApiError(fetchError.value)?.status === 404) {
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
  description: () => project.value?.summary ?? 'Detalle del proyecto.',
  ogType: 'article',
  // La miniatura del propio proyecto como imagen social; sin ella se
  // queda con el og:url global, sin imagen inventada.
  ogImage: () => project.value?.thumbnailUrl ?? undefined
})
</script>

<template>
  <UPage>
    <UPageHeader
      :title="project?.title ?? 'Proyecto'"
      :description="project?.subtitle"
    />

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
        class="flex flex-col gap-8 pb-8 sm:gap-10"
      >
        <!-- Información general -->
        <section
          aria-label="Información del proyecto"
          class="flex flex-col gap-5"
        >
          <div class="flex flex-wrap items-center gap-2">
            <UBadge
              v-if="project.featured"
              color="primary"
              variant="soft"
              size="md"
            >
              <UIcon
                name="i-lucide-star"
                class="mr-1 size-3.5"
              />
              Destacado
            </UBadge>

            <span
              v-if="period"
              class="inline-flex items-center gap-2 text-sm text-muted"
            >
              <UIcon
                name="i-lucide-calendar-days"
                class="size-4"
              />
              {{ period }}
            </span>
          </div>
          <div v-if="project.stack.length" class="flex flex-col gap-3">
            <span class="text-xs font-medium uppercase tracking-wider text-muted">
              Stack tecnológico
            </span>
            <div class="flex flex-wrap gap-2">
              <UTooltip
                v-for="tech in project.stack"
                :key="tech"
                :text="tech"
              >
                <div
                  class="flex items-center gap-1.5 rounded-full border border-default bg-elevated px-3 py-1.5 text-sm font-medium text-muted transition-all duration-200 hover:border-primary/50 hover:bg-primary/5 hover:text-primary cursor-default"
                >
                  <UIcon :name="getTechIcon(tech)" class="size-4" />
                  <span>{{ tech }}</span>
                </div>
              </UTooltip>
            </div>
          </div>
        </section>

        <!-- Imagen principal -->
        <figure
          v-if="project.thumbnailUrl"
          class="overflow-hidden rounded-2xl border border-default bg-elevated"
        >
          <NuxtImg
            :src="project.thumbnailUrl"
            :alt="project.title"
            width="1600"
            height="900"
            class="aspect-video w-full object-cover"
          />
        </figure>

        <!-- Descripción completa -->
        <section
          v-if="descriptionHtml"
          aria-label="Descripción del proyecto"
          class="min-w-0"
        >
          <!-- eslint-disable vue/no-v-html -- descriptionHtml ya pasó por DOMPurify (ver cabecera del script). -->
          <div
            class="text-muted
              [&_h1]:mb-4 [&_h1]:mt-8 [&_h1]:text-2xl [&_h1]:font-bold [&_h1]:tracking-tight [&_h1]:text-highlighted
              [&_h2]:mb-3 [&_h2]:mt-8 [&_h2]:text-xl [&_h2]:font-semibold [&_h2]:tracking-tight [&_h2]:text-highlighted
              [&_h3]:mb-2 [&_h3]:mt-6 [&_h3]:text-lg [&_h3]:font-semibold [&_h3]:text-highlighted
              [&_p]:my-4 [&_p]:leading-7
              [&_a]:font-medium [&_a]:text-primary [&_a]:underline [&_a]:decoration-primary/40 [&_a]:underline-offset-4 [&_a:hover]:decoration-primary
              [&_strong]:font-semibold [&_strong]:text-highlighted
              [&_ul]:my-4 [&_ul]:list-disc [&_ul]:space-y-2 [&_ul]:ps-6
              [&_ol]:my-4 [&_ol]:list-decimal [&_ol]:space-y-2 [&_ol]:ps-6
              [&_li]:ps-1
              [&_blockquote]:my-6 [&_blockquote]:border-s-2 [&_blockquote]:border-primary [&_blockquote]:ps-4 [&_blockquote]:italic
              [&_hr]:my-8 [&_hr]:border-default
              [&_img]:my-6 [&_img]:h-auto [&_img]:max-w-full [&_img]:rounded-xl
              [&_pre]:my-6 [&_pre]:max-w-full [&_pre]:overflow-x-auto [&_pre]:rounded-xl [&_pre]:border [&_pre]:border-default [&_pre]:bg-elevated [&_pre]:p-4
              [&_pre_code]:bg-transparent [&_pre_code]:p-0
              [&_code]:rounded-md [&_code]:bg-elevated [&_code]:px-1.5 [&_code]:py-0.5 [&_code]:font-mono [&_code]:text-sm
              [&_table]:my-6 [&_table]:w-full [&_table]:border-collapse
              [&_th]:border [&_th]:border-default [&_th]:bg-elevated [&_th]:p-3 [&_th]:text-start [&_th]:font-semibold [&_th]:text-highlighted
              [&_td]:border [&_td]:border-default [&_td]:p-3
              [&_th]:text-sm [&_td]:text-sm"
            v-html="descriptionHtml"
          />
          <!-- eslint-enable vue/no-v-html -->
        </section>

        <!-- Galería -->
        <section
          v-if="project.gallery.length"
          class="flex flex-col gap-4"
          aria-label="Galería del proyecto"
        >
          <div class="flex flex-col gap-1">
            <h2 class="text-xl font-semibold tracking-tight text-highlighted">
              Galería
            </h2>
            <p class="text-sm text-muted">
              Imágenes adicionales del proyecto.
            </p>
          </div>

          <GalleryCarousel
            :images="project.gallery.map(image => image.url)"
            :alt="project.title"
          />
        </section>

        <!-- Acciones -->
        <section
          v-if="project.repoUrl || project.demoUrl"
          class="flex flex-col gap-4 rounded-2xl border border-default bg-elevated/50 p-4 sm:p-6"
          aria-label="Enlaces del proyecto"
        >
          <div class="flex flex-col gap-1">
            <h2 class="font-semibold text-highlighted">
              Explora el proyecto
            </h2>
            <p class="text-sm text-muted">
              Consulta el código fuente o visita la versión disponible.
            </p>
          </div>

          <div class="flex flex-col gap-3 sm:flex-row">
            <UButton
              v-if="project.repoUrl"
              :to="project.repoUrl"
              target="_blank"
              rel="noopener noreferrer"
              label="Ver repositorio"
              icon="i-lucide-github"
              color="neutral"
              variant="outline"
              size="lg"
              block
              class="sm:flex-1"
            />

            <UButton
              v-if="project.demoUrl"
              :to="project.demoUrl"
              target="_blank"
              rel="noopener noreferrer"
              label="Ver demo"
              icon="i-lucide-external-link"
              size="lg"
              block
              class="sm:flex-1"
            />
          </div>
        </section>

        <!-- Volver al listado -->
        <div class="border-t border-default pt-6">
          <UButton
            to="/proyectos"
            color="neutral"
            variant="ghost"
            icon="i-lucide-arrow-left"
          >
            Volver a todos los proyectos
          </UButton>
        </div>
      </article>
    </UPageBody>
  </UPage>
</template>
