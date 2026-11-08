<script setup lang="ts">
// ======================================================================
// FORMULARIO DE PROYECTO (alta y edición)
//
// Un único componente para /admin/proyectos/nuevo y /admin/proyectos/[id]:
// si recibe `project` edita, si no, crea. Los dos modos comparten el mismo
// esquema porque la API usa el MISMO ProjectRequest para POST y PUT.
//
// -------------------------------------------------------------------
// LA REGLA DE ORO: featured va SIEMPRE
//
// `featured` es un boolean primitivo en Java y Spring Boot 4.x mantiene
// FAIL_ON_NULL_FOR_PRIMITIVES=true, así que omitirlo no produce un error de
// validación sino de parseo: 400 "Failed to read request". Se envía siempre,
// aunque sea false.
//
// -------------------------------------------------------------------
// PUT ES EDICIÓN COMPLETA
//
// Lo que no llegue en el body se guarda como null (salvo sortOrder→0 y
// gallery/stack→[]). Por eso no se "parchea": se arma el objeto completo a
// partir del estado, vaciando a null lo que el usuario dejó en blanco.
//
// -------------------------------------------------------------------
// VALIDACIÓN
//
// Dos capas con zod (mismo patrón que /login):
//  1. Esquema del cliente → los errores los pinta UForm en cada campo.
//  2. `errors[]` del ProblemDetail de la API → form.setErrors(), porque los
//     paths de Spring (`slug`, `title`, `thumbnailUrl`...) coinciden con los
//     nombres de este formulario.
// ======================================================================
import DOMPurify from 'isomorphic-dompurify'
import { marked } from 'marked'
import * as z from 'zod'
import type { FormSubmitEvent } from '@nuxt/ui'
import { toApiError } from '~/composables/useApi'

const props = defineProps<{
  /** Si viene, el formulario edita ese proyecto; si no, crea uno nuevo. */
  project?: AdminProject | null
}>()

const api = useAdminApi()
const { notify } = useAppNotify()

const isEdit = computed(() => !!props.project)

// ---------------------------------------------------------------------------
// ESTADO INICIAL
// ---------------------------------------------------------------------------
function pickLocalized(text: LocalizedText | null | undefined): { es: string, en: string } {
  return { es: text?.es ?? '', en: text?.en ?? '' }
}

const state = reactive({
  slug: props.project?.slug ?? '',
  title: pickLocalized(props.project?.title),
  subtitle: pickLocalized(props.project?.subtitle),
  summary: pickLocalized(props.project?.summary),
  descriptionMd: pickLocalized(props.project?.descriptionMd),
  thumbnailUrl: props.project?.thumbnailUrl ?? '',
  gallery: (props.project?.gallery ?? []).map(image => ({
    url: image.url,
    // La API manda null para lo vacío; el formulario trabaja siempre con
    // cadena, para poder hacer v-model directo con el input.
    alt: image.alt ?? '',
    caption: image.caption ?? ''
  })),
  repoUrl: props.project?.repoUrl ?? '',
  demoUrl: props.project?.demoUrl ?? '',
  stack: [...(props.project?.stack ?? [])],
  periodStart: props.project?.periodStart ?? '',
  periodEnd: props.project?.periodEnd ?? '',
  featured: props.project?.featured ?? false,
  sortOrder: props.project?.sortOrder ?? 0
})

// Un solo punto donde volcar un proyecto al formulario: al montar y al
// descartar cambios. Evita duplicar la asignación campo a campo.
function fillFrom(project: AdminProject) {
  state.slug = project.slug
  state.title = pickLocalized(project.title)
  state.subtitle = pickLocalized(project.subtitle)
  state.summary = pickLocalized(project.summary)
  state.descriptionMd = pickLocalized(project.descriptionMd)
  state.thumbnailUrl = project.thumbnailUrl ?? ''
  state.gallery = (project.gallery ?? []).map(image => ({
    url: image.url,
    alt: image.alt ?? '',
    caption: image.caption ?? ''
  }))
  state.repoUrl = project.repoUrl ?? ''
  state.demoUrl = project.demoUrl ?? ''
  state.stack = [...(project.stack ?? [])]
  state.periodStart = project.periodStart ?? ''
  state.periodEnd = project.periodEnd ?? ''
  state.featured = project.featured
  state.sortOrder = project.sortOrder
}

// ---------------------------------------------------------------------------
// ESQUEMA
//
// Reproduce, mensaje a mensaje, las restricciones de ProjectRequest.java.
// Las opcionales largas (URL, textos) solo se comprueban si el usuario
// escribió algo: la API no exige nada más y no se inventan reglas nuevas.
// ---------------------------------------------------------------------------
function optionalUrl(label: string) {
  return z
    .string()
    .max(255, `${label} no puede superar 255 caracteres`)
    .refine(value => value === '' || /^https?:\/\/\S+$/i.test(value), `${label} debe empezar por http:// o https://`)
}

const schema = z
  .object({
    slug: z
      .string('El slug es obligatorio')
      .min(1, 'El slug es obligatorio')
      .max(120, 'El slug no puede superar 120 caracteres')
      .regex(/^[a-z0-9]+(-[a-z0-9]+)*$/, 'El slug solo admite minúsculas, números y guiones'),
    title: z.object({
      es: z.string(),
      en: z.string()
    }),
    subtitle: z.object({ es: z.string(), en: z.string() }),
    summary: z.object({ es: z.string(), en: z.string() }),
    descriptionMd: z.object({ es: z.string(), en: z.string() }),
    thumbnailUrl: z.string().max(255, 'La URL de la miniatura no puede superar 255 caracteres'),
    gallery: z.array(z.object({
      url: z.string().min(1, 'La imagen no tiene URL'),
      alt: z.string(),
      caption: z.string()
    })).max(50, 'La galería no puede superar 50 imágenes'),
    repoUrl: optionalUrl('La URL del repositorio'),
    demoUrl: optionalUrl('La URL de la demo'),
    stack: z.array(z.string()).max(50, 'El stack no puede superar 50 tecnologías'),
    periodStart: z.string(),
    periodEnd: z.string(),
    featured: z.boolean(),
    sortOrder: z.number('El orden debe ser un número')
  })
  // El título es el único texto con restricción: @LocalizedNonBlank exige
  // es O en no vacío. El issue se ancla en `title.es` para que el mensaje
  // salga debajo de un campo que existe (nada de `title`, que no se pinta).
  .superRefine((value, ctx) => {
    if (!value.title.es.trim() && !value.title.en.trim()) {
      ctx.addIssue({
        code: 'custom',
        path: ['title', 'es'],
        message: 'Introduce el título en español o en inglés'
      })
    }
  })

type Schema = z.input<typeof schema>

// Tipado estructural de lo único que se usa de UForm: @nuxt/ui no exporta el
// componente, así que no se puede escribir InstanceType<typeof UForm>.
const formRef = ref<{ setErrors: (errors: Array<{ path: string, message: string }>) => void } | null>(null)
const alert = ref('')

// ---------------------------------------------------------------------------
// ENSAMBLADO DEL BODY
//
// Los mapas bilingües vacíos se envían como null, no como {es:'', en:''}:
// es lo que hace la API con "ausentes = null" y evita dejar textos en blanco
// metidos en la base de datos.
// ---------------------------------------------------------------------------
function toLocalized(value: { es: string, en: string }): LocalizedText | null {
  const es = value.es.trim()
  const en = value.en.trim()
  return es || en ? { ...(es ? { es } : {}), ...(en ? { en } : {}) } : null
}

function toBody(): ProjectRequest {
  return {
    slug: state.slug.trim(),
    // Siempre objeto, aunque esté vacío: la validación del cliente ya
    // impide que llegue sin idioma.
    title: toLocalized(state.title) ?? {},
    subtitle: toLocalized(state.subtitle),
    summary: toLocalized(state.summary),
    descriptionMd: toLocalized(state.descriptionMd),
    thumbnailUrl: state.thumbnailUrl || null,
    gallery: state.gallery.map(image => ({
      url: image.url,
      alt: image.alt.trim() || null,
      caption: image.caption.trim() || null
    })),
    repoUrl: state.repoUrl.trim() || null,
    demoUrl: state.demoUrl.trim() || null,
    stack: state.stack,
    periodStart: state.periodStart || null,
    periodEnd: state.periodEnd || null,
    // BOOLEAN PRIMITIVO: enviarse siempre, aunque sea false.
    featured: state.featured,
    sortOrder: state.sortOrder
  }
}

/**
 * Mismo ensamblado pero a partir de un proyecto guardado. Sirve para comparar
 * peras con peras en el contador de cambios: los dos lados pasan por las mismas
 * normalizaciones (vacío → null, bilingüe sin claves vacías...), así que un
 * campo sale "cambiado" solo si el PUT mandaría otra cosa.
 */
function bodyFrom(project: AdminProject): ProjectRequest {
  return {
    slug: project.slug.trim(),
    title: toLocalized(pickLocalized(project.title)) ?? {},
    subtitle: toLocalized(pickLocalized(project.subtitle)),
    summary: toLocalized(pickLocalized(project.summary)),
    descriptionMd: toLocalized(pickLocalized(project.descriptionMd)),
    thumbnailUrl: project.thumbnailUrl || null,
    gallery: (project.gallery ?? []).map(image => ({
      url: image.url,
      alt: image.alt?.trim() || null,
      caption: image.caption?.trim() || null
    })),
    repoUrl: project.repoUrl?.trim() || null,
    demoUrl: project.demoUrl?.trim() || null,
    stack: project.stack ?? [],
    periodStart: project.periodStart || null,
    periodEnd: project.periodEnd || null,
    featured: project.featured,
    sortOrder: project.sortOrder
  }
}

// ---------------------------------------------------------------------------
// CAMBIOS SIN GUARDAR
//
// Sin esto el formulario no diría qué se ha tocado, y el PUT reemplaza el
// proyecto entero: conviene verlo antes de pulsar Guardar. Solo se compara en
// edición (al crear no hay original contra el que comparar; todo está "nuevo").
// La cuenta la lleva el composable compartido, el mismo que usa el perfil.
// ---------------------------------------------------------------------------
const ETIQUETAS: Record<string, string> = {
  slug: 'slug',
  title: 'título',
  subtitle: 'subtítulo',
  summary: 'resumen',
  descriptionMd: 'descripción',
  thumbnailUrl: 'miniatura',
  gallery: 'galería',
  repoUrl: 'repositorio',
  demoUrl: 'demo',
  stack: 'stack',
  periodStart: 'fecha de inicio',
  periodEnd: 'fecha de fin',
  featured: 'destacado',
  sortOrder: 'orden'
}

const { hayCambios, claseCambio, textoCambios, listaCambios } = useCambiosSinGuardar(
  Object.fromEntries(
    Object.entries(ETIQUETAS).map(([campo, etiqueta]) => [
      campo,
      {
        etiqueta,
        actual: () => normalizarValor(toBody()[campo as keyof ProjectRequest]),
        guardado: () => normalizarValor(
          props.project ? bodyFrom(props.project)[campo as keyof ProjectRequest] : null
        )
      }
    ])
  ),
  () => isEdit.value
)

/** Vuelve el formulario al último estado guardado. No toca el servidor. */
function descartarCambios() {
  if (props.project) fillFrom(props.project)
  alert.value = ''
}

// ---------------------------------------------------------------------------
// SUBMIT
// ---------------------------------------------------------------------------
async function onSubmit(_event: FormSubmitEvent<Schema>) {
  alert.value = ''
  const body = toBody()

  try {
    if (isEdit.value && props.project) {
      await api.put<AdminProject>(`/projects/${props.project.id}`, body)
      notify('Cambios guardados', 'success')
      return
    }

    const created = await api.post<AdminProject>('/projects', body)
    notify('Proyecto creado como borrador', 'success')
    await navigateTo(`/admin/proyectos/${created.id}`)
  } catch (error) {
    const problem = toApiError(error).problem
    alert.value = problem.detail ?? 'No se pudieron guardar los cambios'

    // El400 manda los fallos campo a campo; el 409 de slug duplicado
    // también se ancla al campo para que no haya que leer el alert.
    if (problem.status === 409) {
      formRef.value?.setErrors([{ path: 'slug', message: alert.value }])
      return
    }

    const fieldErrors = (problem.errors ?? [])
      .filter(entry => entry.field)
      .map(entry => ({ path: entry.field as string, message: entry.message }))

    if (fieldErrors.length) {
      formRef.value?.setErrors(fieldErrors)
    }
  }
}

// ---------------------------------------------------------------------------
// STACK (chips)
// ---------------------------------------------------------------------------
const newTech = ref('')

function addTech() {
  const value = newTech.value.trim()
  if (!value) return
  if (state.stack.includes(value)) {
    notify('Esa tecnología ya está en el stack', 'warning')
    newTech.value = ''
    return
  }
  if (state.stack.length >= 50) {
    notify('El stack no puede superar 50 tecnologías', 'warning')
    return
  }
  state.stack.push(value)
  newTech.value = ''
}

function removeTech(tech: string) {
  state.stack = state.stack.filter(entry => entry !== tech)
}

// ---------------------------------------------------------------------------
// SUBIDAS
//
// `use` va por query y el fichero en la parte `file`; es lo que exige la API
// (si recibe el mismo parámetro por query y por form field los une con coma
// y responde 400). Se valida el tipo en cliente con el mismo criterio que el
// backend: magic bytes de jpeg/png/webp, que es lo que hace de límite real.
// ---------------------------------------------------------------------------
const ALLOWED_IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp']
const thumbnailInput = ref<HTMLInputElement | null>(null)
const galleryInput = ref<HTMLInputElement | null>(null)
const uploading = ref(false)

function readImageFile(event: Event): File | null {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0] ?? null
  // Se limpia siempre: si no, volver a elegir el mismo fichero no dispara
  // change y el segundo intento no hace nada.
  input.value = ''
  if (!file) return null
  if (!ALLOWED_IMAGE_TYPES.includes(file.type)) {
    notify('Solo se admiten imágenes JPEG, PNG o WebP', 'error')
    return null
  }
  return file
}

async function uploadThumbnail(event: Event) {
  const file = readImageFile(event)
  if (!file) return

  uploading.value = true
  try {
    const { url } = await api.upload<StorageUploadResult>('/storage', file, 'thumbnail')
    state.thumbnailUrl = url
    notify('Miniatura subida', 'success')
  } catch (error) {
    notify(toApiError(error).message, 'error')
  } finally {
    uploading.value = false
  }
}

async function uploadGallery(event: Event) {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files ?? [])
  input.value = ''
  if (!files.length) return

  const invalid = files.find(file => !ALLOWED_IMAGE_TYPES.includes(file.type))
  if (invalid) {
    notify('Solo se admiten imágenes JPEG, PNG o WebP', 'error')
    return
  }
  if (state.gallery.length + files.length > 50) {
    notify('La galería no puede superar 50 imágenes', 'error')
    return
  }

  uploading.value = true
  try {
    // Una a una en paralelo: si una falla, el resto se sube igual y el
    // error se cuenta en el aviso en lugar de abortar la tanda entera.
    const results = await Promise.allSettled(
      files.map(file => api.upload<StorageUploadResult>('/storage', file, 'gallery'))
    )

    const failures = results.filter(result => result.status === 'rejected')
    results.forEach((result) => {
      if (result.status === 'fulfilled') {
        state.gallery.push({ url: result.value.url, alt: '', caption: '' })
      }
    })

    if (failures.length) {
      notify(`${failures.length} ${failures.length === 1 ? 'imagen no se pudo subir' : 'imágenes no se pudieron subir'}`, 'error')
    } else {
      notify('Imágenes añadidas a la galería', 'success')
    }
  } finally {
    uploading.value = false
  }
}

function removeGalleryImage(url: string) {
  // Solo se quita del estado: el fichero del bucket lo recoge el backend al
  // hacer PUT, si al final deja de estar referenciado (limpieza de huérfanos).
  state.gallery = state.gallery.filter(image => image.url !== url)
}

// ---------------------------------------------------------------------------
// PREVIEW DE MARKDOWN
//
// Mismo par que usa la página pública: marked convierte y DOMPurify sanea
// ANTES de v-html. Sin el saneado, un bloque de HTML en la descripción se
// ejecutaría en el panel.
// ---------------------------------------------------------------------------
const showPreview = ref(false)

function renderMarkdown(value: string): string {
  return value ? DOMPurify.sanitize(marked.parse(value, { async: false })) : ''
}

const previewHtml = computed(() => {
  const md = showPreview.value
    ? (state.descriptionMd.es.trim() || state.descriptionMd.en.trim())
    : ''
  return renderMarkdown(md)
})
</script>

<template>
  <UForm
    ref="formRef"
    :schema="schema"
    :state="state"
    class="flex flex-col gap-6"
    @submit="onSubmit"
  >
    <UAlert
      v-if="alert"
      color="error"
      variant="subtle"
      icon="i-lucide-circle-x"
      :description="alert"
      class="order-first"
    />

    <!-- Barra de estado: qué va a cambiar y el Guardar, siempre a la vista.
         Solo en edición: al crear no hay "cambios sin guardar" que contar. -->
    <div
      v-if="isEdit"
      class="bg-default/90 supports-backdrop-blur:bg-default/70 sticky top-0 z-10 flex flex-wrap items-center justify-between gap-3 rounded-lg px-1 py-2 backdrop-blur"
    >
      <div class="flex items-center gap-2">
        <UBadge
          v-if="hayCambios"
          color="warning"
          variant="subtle"
          icon="i-lucide-pencil"
        >
          {{ textoCambios }}
        </UBadge>
        <UBadge
          v-else
          color="neutral"
          variant="subtle"
          icon="i-lucide-check"
        >
          Sin cambios
        </UBadge>
        <span
          v-if="hayCambios"
          class="text-muted hidden text-xs sm:inline"
        >
          {{ listaCambios }}
        </span>
      </div>
      <div class="flex gap-2">
        <UButton
          v-if="hayCambios"
          label="Descartar"
          icon="i-lucide-undo-2"
          color="neutral"
          variant="ghost"
          @click="descartarCambios"
        />
        <UButton
          label="Guardar"
          icon="i-lucide-save"
          type="submit"
          :loading="false"
          :disabled="!hayCambios"
        />
      </div>
    </div>

    <!-- IDENTIDAD -->
    <UCard :class="claseCambio('slug', 'title', 'subtitle', 'summary')">
      <template #header>
        <h3 class="font-semibold text-highlighted">
          Identidad
        </h3>
      </template>

      <div class="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <UFormField
          label="Slug"
          name="slug"
          required
          help="Se usa en la URL pública: /proyectos/<slug>. Una vez publicado, cambiarlo cambia la dirección."
          class="sm:col-span-2"
        >
          <UInput
            v-model="state.slug"
            placeholder="mi-proyecto"
            class="w-full"
            :disabled="isEdit"
          />
        </UFormField>

        <UFormField
          label="Título (español)"
          name="title.es"
          required
        >
          <UInput
            v-model="state.title.es"
            placeholder="Portfolio v2"
            class="w-full"
          />
        </UFormField>

        <UFormField
          label="Título (inglés)"
          name="title.en"
          help="Al menos uno de los dos idiomas es obligatorio."
        >
          <UInput
            v-model="state.title.en"
            placeholder="Portfolio v2"
            class="w-full"
          />
        </UFormField>

        <UFormField
          label="Subtítulo (español)"
          name="subtitle.es"
        >
          <UInput
            v-model="state.subtitle.es"
            placeholder="Sitio personal"
            class="w-full"
          />
        </UFormField>

        <UFormField
          label="Subtítulo (inglés)"
          name="subtitle.en"
        >
          <UInput
            v-model="state.subtitle.en"
            placeholder="Personal site"
            class="w-full"
          />
        </UFormField>
      </div>
    </UCard>

    <!-- CONTENIDO -->
    <UCard :class="claseCambio('descriptionMd')">
      <template #header>
        <div class="flex items-center justify-between gap-3">
          <h3 class="font-semibold text-highlighted">
            Contenido
          </h3>

          <UButton
            :label="showPreview ? 'Seguir editando' : 'Vista previa'"
            :icon="showPreview ? 'i-lucide-pencil' : 'i-lucide-eye'"
            color="neutral"
            variant="soft"
            size="sm"
            :disabled="!state.descriptionMd.es.trim() && !state.descriptionMd.en.trim()"
            @click="showPreview = !showPreview"
          />
        </div>
      </template>

      <div class="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <UFormField
          label="Resumen (español)"
          name="summary.es"
        >
          <UTextarea
            v-model="state.summary.es"
            :rows="3"
            placeholder="Una frase para el listado público."
            class="w-full"
          />
        </UFormField>

        <UFormField
          label="Resumen (inglés)"
          name="summary.en"
        >
          <UTextarea
            v-model="state.summary.en"
            :rows="3"
            placeholder="One-liner for the public listing."
            class="w-full"
          />
        </UFormField>

        <template v-if="!showPreview">
          <UFormField
            label="Descripción (español)"
            name="descriptionMd.es"
            help="Markdown. Se muestra como HTML saneado en la página pública."
            class="sm:col-span-2"
          >
            <UTextarea
              v-model="state.descriptionMd.es"
              :rows="8"
              placeholder="## Qué hice&#10;- React&#10;- Node"
              class="w-full font-mono text-sm"
            />
          </UFormField>

          <UFormField
            label="Descripción (inglés)"
            name="descriptionMd.en"
            class="sm:col-span-2"
          >
            <UTextarea
              v-model="state.descriptionMd.en"
              :rows="8"
              placeholder="## What I built"
              class="w-full font-mono text-sm"
            />
          </UFormField>
        </template>

        <!-- eslint-disable vue/no-v-html -- previewHtml ya pasó por DOMPurify (renderMarkdown). -->
        <div
          v-else
          class="text-muted
            sm:col-span-2
            [&_h1]:mb-4 [&_h1]:mt-8 [&_h1]:text-2xl [&_h1]:font-bold [&_h1]:tracking-tight [&_h1]:text-highlighted
            [&_h2]:mb-3 [&_h2]:mt-8 [&_h2]:text-xl [&_h2]:font-semibold [&_h2]:tracking-tight [&_h2]:text-highlighted
            [&_h3]:mb-2 [&_h3]:mt-6 [&_h3]:text-lg [&_h3]:font-semibold [&_h3]:text-highlighted
            [&_p]:my-4 [&_p]:leading-7
            [&_a]:font-medium [&_a]:text-primary [&_a]:underline [&_a]:decoration-primary/40 [&_a]:underline-offset-4 [&_a:hover]:decoration-primary
            [&_strong]:font-semibold [&_strong]:text-highlighted
            [&_ul]:my-4 [&_ul]:list-disc [&_ul]:space-y-2 [&_ul]:ps-6
            [&_ol]:my-4 [&_ol]:list-decimal [&_ol]:space-y-2 [&_ol]:ps-6
            [&_li]:ps-1
            [&_code]:rounded-md [&_code]:bg-elevated [&_code]:px-1.5 [&_code]:py-0.5 [&_code]:font-mono [&_code]:text-sm
            [&_pre]:my-6 [&_pre]:max-w-full [&_pre]:overflow-x-auto [&_pre]:rounded-xl [&_pre]:border [&_pre]:border-default [&_pre]:bg-elevated [&_pre]:p-4
            [&_pre_code]:bg-transparent [&_pre_code]:p-0"
          role="region"
          aria-label="Vista previa de la descripción"
          v-html="previewHtml"
        />
        <!-- eslint-enable vue/no-v-html -->
      </div>
    </UCard>

    <!-- IMÁGENES -->
    <UCard :class="claseCambio('thumbnailUrl', 'gallery')">
      <template #header>
        <div class="flex flex-wrap items-center justify-between gap-3">
          <div>
            <h3 class="font-semibold text-highlighted">
              Imágenes
            </h3>
            <p class="text-muted text-xs">
              JPEG, PNG o WebP · hasta 5 MB · la miniatura se recorta a la tarjeta del listado.
            </p>
          </div>

          <div class="flex gap-2">
            <input
              ref="thumbnailInput"
              type="file"
              accept="image/jpeg,image/png,image/webp"
              class="hidden"
              tabindex="-1"
              aria-hidden="true"
              @change="uploadThumbnail"
            >
            <UButton
              label="Subir miniatura"
              icon="i-lucide-image-up"
              color="neutral"
              variant="soft"
              size="sm"
              :loading="uploading"
              @click="thumbnailInput?.click()"
            />

            <input
              ref="galleryInput"
              type="file"
              accept="image/jpeg,image/png,image/webp"
              multiple
              class="hidden"
              tabindex="-1"
              aria-hidden="true"
              @change="uploadGallery"
            >
            <UButton
              label="Añadir imágenes"
              icon="i-lucide-images"
              color="neutral"
              variant="soft"
              size="sm"
              :loading="uploading"
              @click="galleryInput?.click()"
            />
          </div>
        </div>
      </template>

      <UFormField
        label="Miniatura"
        name="thumbnailUrl"
        help="Pega también una URL si la imagen ya está servida."
        class="mb-4"
      >
        <div class="flex w-full flex-wrap items-center gap-3">
          <UInput
            v-model="state.thumbnailUrl"
            placeholder="https://…"
            class="min-w-0 flex-1"
          />
          <UButton
            v-if="state.thumbnailUrl"
            icon="i-lucide-trash-2"
            color="error"
            variant="ghost"
            square
            aria-label="Quitar miniatura"
            @click="state.thumbnailUrl = ''"
          />
        </div>
        <div
          v-if="state.thumbnailUrl"
          class="relative mt-3 overflow-hidden rounded-xl border border-default bg-elevated"
        >
          <img
            :src="state.thumbnailUrl"
            alt="Vista previa de la miniatura"
            class="aspect-video w-full object-cover"
            loading="lazy"
          >
        </div>
      </UFormField>

      <p
        v-if="!state.gallery.length"
        class="text-muted text-sm"
      >
        Sin imágenes en la galería todavía.
      </p>

      <ul
        v-else
        class="grid grid-cols-1 gap-4 md:grid-cols-2"
      >
        <li
          v-for="(image, index) in state.gallery"
          :key="image.url"
          class="flex flex-col gap-3 rounded-xl border border-default bg-default p-3 sm:flex-row sm:items-start"
        >
          <div class="relative overflow-hidden rounded-lg border border-default bg-elevated sm:size-28 sm:shrink-0">
            <img
              :src="image.url"
              alt=""
              class="aspect-video w-full object-cover sm:h-full sm:aspect-auto"
              loading="lazy"
            >
          </div>

          <div class="min-w-0 flex-1 space-y-2">
            <UFormField
              :label="`Texto alternativo ${index + 1}`"
              :name="`gallery.${index}.alt`"
              help="Describe la imagen para lectores de pantalla."
            >
              <UInput
                v-model="image.alt"
                placeholder="Captura del panel de control"
                class="w-full"
              />
            </UFormField>

            <UFormField
              :label="`Pie de foto ${index + 1}`"
              :name="`gallery.${index}.caption`"
            >
              <UInput
                v-model="image.caption"
                placeholder="Detalle de la implementación"
                class="w-full"
              />
            </UFormField>
          </div>

          <UButton
            icon="i-lucide-trash-2"
            color="error"
            variant="ghost"
            square
            aria-label="Quitar imagen de la galería"
            class="self-start"
            @click="removeGalleryImage(image.url)"
          />
        </li>
      </ul>
    </UCard>

    <!-- ENLACES Y DATOS -->
    <UCard :class="claseCambio('repoUrl', 'demoUrl', 'stack', 'periodStart', 'periodEnd')">
      <template #header>
        <h3 class="font-semibold text-highlighted">
          Enlaces y datos
        </h3>
      </template>

      <div class="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <UFormField
          label="Repositorio"
          name="repoUrl"
        >
          <UInput
            v-model="state.repoUrl"
            placeholder="https://github.com/…"
            class="w-full"
          />
        </UFormField>

        <UFormField
          label="Demo"
          name="demoUrl"
        >
          <UInput
            v-model="state.demoUrl"
            placeholder="https://…"
            class="w-full"
          />
        </UFormField>

        <UFormField
          label="Desde"
          name="periodStart"
        >
          <UInput
            v-model="state.periodStart"
            type="date"
            class="w-full"
          />
        </UFormField>

        <UFormField
          label="Hasta"
          name="periodEnd"
          help="Déjalo vacío si sigue en marcha."
        >
          <UInput
            v-model="state.periodEnd"
            type="date"
            class="w-full"
          />
        </UFormField>

        <UFormField
          label="Stack tecnológico"
          name="stack"
          :help="`${state.stack.length}/50 tecnologías`"
          class="sm:col-span-2"
        >
          <div class="w-full space-y-2">
            <div
              v-if="state.stack.length"
              class="flex flex-wrap gap-1.5"
            >
              <UBadge
                v-for="tech in state.stack"
                :key="tech"
                color="neutral"
                variant="outline"
                class="gap-1"
              >
                {{ tech }}
                <UButton
                  icon="i-lucide-x"
                  color="neutral"
                  variant="ghost"
                  square
                  size="xs"
                  :aria-label="`Quitar ${tech}`"
                  @click="removeTech(tech)"
                />
              </UBadge>
            </div>

            <div class="flex gap-2">
              <UInput
                v-model="newTech"
                placeholder="Añadir tecnología y pulsar Intro"
                class="min-w-0 flex-1"
                @keydown.enter.prevent="addTech"
              />
              <UButton
                label="Añadir"
                color="neutral"
                variant="soft"
                @click="addTech"
              />
            </div>
          </div>
        </UFormField>
      </div>
    </UCard>

    <!-- PUBLICACIÓN -->
    <UCard :class="claseCambio('featured', 'sortOrder')">
      <template #header>
        <h3 class="font-semibold text-highlighted">
          Publicación
        </h3>
      </template>

      <div class="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <UFormField
          label="Orden"
          name="sortOrder"
          help="0 se muestra primero. El listado público respeta este número."
        >
          <UInput
            v-model.number="state.sortOrder"
            type="number"
            min="0"
            class="w-full"
          />
        </UFormField>

        <UFormField
          label="Destacado"
          name="featured"
          help="Marca la tarjeta con una estrella en la portada."
        >
          <USwitch
            v-model="state.featured"
          />
        </UFormField>
      </div>

      <UAlert
        v-if="!isEdit"
        color="info"
        variant="subtle"
        icon="i-lucide-info"
        title="Se creará como borrador"
        description="Los proyectos nuevos no se ven en el portfolio hasta que los publiques desde el listado."
        class="mt-4"
      />
    </UCard>

    <!-- ACCIONES -->
    <div class="flex flex-wrap justify-end gap-2">
      <UButton
        label="Cancelar"
        color="neutral"
        variant="soft"
        @click="navigateTo('/admin/proyectos')"
      />
      <UButton
        v-if="isEdit"
        label="Descartar"
        icon="i-lucide-undo-2"
        color="neutral"
        variant="soft"
        :disabled="!hayCambios"
        @click="descartarCambios"
      />
      <UButton
        type="submit"
        :label="isEdit ? 'Guardar cambios' : 'Crear proyecto'"
        :icon="isEdit ? 'i-lucide-save' : 'i-lucide-plus'"
        :loading="false"
        :disabled="isEdit && !hayCambios"
      />
    </div>
  </UForm>
</template>
