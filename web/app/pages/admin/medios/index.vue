<script setup lang="ts">
// ======================================================================
// MEDIOS
//
// Gestión del bucket: ver qué ficheros hay, subir nuevos por tipo y borrar
// los que sobran. Todo son endpoints de la API (GET/DELETE /admin/storage,
// GET /admin/storage/uses, POST /admin/storage); el proxy /api/admin/** los
// reenvía tal cual, así que no hay endpoint BFF propio.
//
// El borrado NO toca la base de datos, y la API lo sabe: si perfil, un
// proyecto o un certificado siguen apuntando al fichero, DELETE responde 409
// y dice dónde se usa. Por eso aquí no se ofrece borrar "a la fuerza": el
// aviso explica que primero hay que dejar de usarlo.
//
// La subida guía, no adivina: los tipos y sus recomendaciones vienen de la
// API (GET /admin/storage/uses) para no duplicar aquí reglas que ya viven en
// StorageUse. Las dimensiones las devuelve el propio POST para avisar si no
// encajan; nunca bloquean (solo el formato lo hace, y eso ya lo valida la API).
//
// El CV es el único caso en que subir un fichero NO basta: hace falta decirle
// al perfil que lo use, porque GET /public/cv/{lang} lee profile.cvUrlEs/En.
// De eso se encarga el menú "Usar como CV" de cada PDF, que reenvía el perfil
// entero (PUT /admin/profile) cambiando solo su campo de CV.
//
// El listado llega paginado (GET /admin/storage?page=&size=) y ordenado por
// fecha de modificación, lo más reciente primero.
// ======================================================================
import { toApiError, unwrapApiError } from '~/composables/useApi'
import type { DropdownMenuItem } from '@nuxt/ui'

definePageMeta({ layout: 'dashboard' })

const api = useAdminApi()
const { notify } = useAppNotify()

const PAGE_SIZE = 24
const ALLOWED_IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp']

// Estado de los filtros. El tipo y la búsqueda son client-side sobre la página
// cargada; para el tamaño del bucket (pocos objetos) no merece un round trip.
const page = ref(1)
const typeFilter = ref<'todos' | 'imagen' | 'pdf'>('todos')
const search = ref('')

// Mismo motivo que en el resto del panel: el $fetch de Nuxt no reenvía las
// cookies durante el SSR, así que esto va solo en cliente.
const { data, status, error, refresh } = await useAsyncData(
  'admin-media',
  () => api.get<AdminPage<AdminStorageObject>>('/storage', { page: page.value - 1, size: PAGE_SIZE }),
  { server: false, watch: [page] }
)

// La guía de tipos se carga una vez; no depende de la página ni de los filtros.
const { data: uses } = await useAsyncData(
  'admin-media-uses',
  () => api.get<AdminStorageUse[]>('/storage/uses'),
  { server: false }
)

// El perfil, para saber qué PDF es ya el CV en cada idioma y poder reenviarlo
// entero al asignar (PUT /admin/profile reemplaza, no hace merge).
const { data: profile } = await useAsyncData(
  'admin-media-profile',
  () => api.get<AdminProfile>('/profile'),
  { server: false }
)

const objects = computed(() => data.value?.items ?? [])
const totalPages = computed(() => data.value?.totalPages ?? 1)
const totalElements = computed(() => data.value?.totalElements ?? 0)

const loadError = computed(() => unwrapApiError(error.value))
const loadErrorMessage = computed(
  () => loadError.value?.problem.detail ?? loadError.value?.message ?? 'No se pudo conectar con el servidor'
)

// ---------------------------------------------------------------------------
// FILTROS Y PRESENTACIÓN
// ---------------------------------------------------------------------------
function isPdf(object: AdminStorageObject): boolean {
  return object.contentType === 'application/pdf'
}

const visibleObjects = computed(() => {
  const q = search.value.trim().toLowerCase()
  return objects.value.filter((object) => {
    if (typeFilter.value === 'imagen' && isPdf(object)) return false
    if (typeFilter.value === 'pdf' && !isPdf(object)) return false
    if (q && !object.key.toLowerCase().includes(q)) return false
    return true
  })
})

const filtersActive = computed(() => search.value.trim() !== '' || typeFilter.value !== 'todos')

function clearFilters() {
  search.value = ''
  typeFilter.value = 'todos'
}

/** "1 archivo" / "16 archivos". */
function objectsLabel(count: number): string {
  return `${count} ${count === 1 ? 'archivo' : 'archivos'}`
}

/** Tamaño legible: B / KB / MB. */
function humanSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(2)} MB`
}

/** Etiqueta corta del tipo, para el badge de cada tarjeta. */
function typeLabel(object: AdminStorageObject): string {
  if (isPdf(object)) return 'PDF'
  if (object.contentType === 'image/webp') return 'WebP'
  if (object.contentType === 'image/png') return 'PNG'
  if (object.contentType === 'image/jpeg') return 'JPG'
  return object.contentType ?? 'Otro'
}

/**
 * Uso declarado del fichero al subirlo. "Sin uso" si es anterior a la tabla y
 * nada lo referencia: no se inventa uno, porque una etiqueta falsa sería peor
 * que ninguna.
 */
const USE_LABELS: Record<string, string> = {
  avatar: 'Avatar',
  thumbnail: 'Miniatura',
  gallery: 'Galería',
  image: 'Certificado',
  cv: 'CV'
}

function useLabel(object: AdminStorageObject): string {
  return object.use ? (USE_LABELS[object.use] ?? object.use) : 'Sin uso'
}

// ---------------------------------------------------------------------------
// COPIAR URL Y DESCARGAR
// ---------------------------------------------------------------------------
/** Copia la URL pública al portapapeles. Si el navegador la deniega, se avisa. */
async function copyUrl(object: AdminStorageObject) {
  try {
    await navigator.clipboard.writeText(object.url)
    notify('URL copiada al portapapeles', 'success')
  } catch {
    notify('No se pudo copiar la URL. Cópiala a mano desde el nombre del archivo.', 'error')
  }
}

// ---------------------------------------------------------------------------
// SUBIDA
// ---------------------------------------------------------------------------
const uploadOpen = ref(false)
const uploadUse = ref<StorageUse>('thumbnail')
const uploadInput = ref<HTMLInputElement | null>(null)
const uploading = ref(false)
// Aviso de dimensiones tras subir: se queda visible en el modal hasta cerrarlo.
const uploadWarning = ref<string | null>(null)

// El tipo por defecto es el primero que ofrece la API, para no hardcodear nada.
watchEffect(() => {
  if (uses.value?.length && !uses.value.some(u => u.use === uploadUse.value)) {
    uploadUse.value = uses.value[0]!.use
  }
})

const currentUse = computed(() => uses.value?.find(u => u.use === uploadUse.value) ?? null)

/** La extensión que hay que pedir en el selector de fichero, según el tipo. */
const acceptForCurrentUse = computed(() =>
  currentUse.value && !currentUse.value.formats.includes('jpg')
    ? 'application/pdf'
    : 'image/jpeg,image/png,image/webp'
)

function openUpload() {
  uploadWarning.value = null
  uploadOpen.value = true
}

/**
 * Avisa si las dimensiones subidas no encajan con la recomendación del tipo.
 * No bloquea: es orientativo. Sin dimensiones (un PDF) no hay nada que decir.
 */
function dimensionWarning(result: StorageUploadResult): string | null {
  if (result.width == null || result.height == null) return null
  const { width, height } = result
  const ratio = width / height
  switch (uploadUse.value) {
    case 'avatar':
      if (Math.abs(ratio - 1) > 0.1) return `La imagen es ${width}×${height}; para un avatar se recomienda cuadrada.`
      if (width < 400) return `La imagen es ${width}×${height}; para un avatar se recomienda al menos 400×400.`
      return null
    case 'thumbnail':
      if (Math.abs(ratio - 16 / 9) > 0.15) return `La imagen es ${width}×${height}; para una miniatura se recomienda 16:9.`
      if (width < 1200) return `La imagen es ${width}×${height}; para una miniatura se recomienda al menos 1200 px de ancho.`
      return null
    default:
      if (width < 1200) return `La imagen es ${width}×${height}; se recomienda al menos 1200 px de ancho.`
      return null
  }
}

/** Valida el fichero elegido y lo sube. Reutiliza el patrón de los formularios. */
async function onFileSelected(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0] ?? null
  // Se limpia ya el input para poder volver a elegir el mismo fichero.
  if (input) input.value = ''
  if (!file) return

  const isPdfUse = uploadUse.value === 'cv'
  if (isPdfUse && file.type !== 'application/pdf') {
    notify('Para el CV solo se admite PDF', 'error')
    return
  }
  if (!isPdfUse && !ALLOWED_IMAGE_TYPES.includes(file.type)) {
    notify('Solo se admiten imágenes JPEG, PNG o WebP', 'error')
    return
  }

  uploading.value = true
  uploadWarning.value = null
  try {
    const result = await api.upload<StorageUploadResult>('/storage', file, uploadUse.value)
    uploadWarning.value = dimensionWarning(result)
    notify('Archivo subido', 'success')
    // El nuevo fichero es el más reciente: va al principio de la primera página.
    if (page.value === 1) {
      await refresh()
    } else {
      page.value = 1
    }
  } catch (err) {
    const apiError = toApiError(err)
    notify(apiError.problem.detail || apiError.message, 'error')
  } finally {
    uploading.value = false
  }
}

// ---------------------------------------------------------------------------
// CV EN EL PERFIL
//
// Subir un PDF con tipo "CV" solo lo deja en el bucket: el portfolio público
// lee profile.cvUrlEs/cvUrlEn (GET /public/cv/{lang} redirige ahí). Asignarlo
// es este menú, el único sitio del panel donde el CV se dice al perfil.
//
// PUT /admin/profile reemplaza el perfil ENTERO, así que se reenvía tal cual
// se leyó y solo se cambia el campo del idioma elegido; si no, asignar el CV
// borraría la biografía, el stack y las experiencias.
// ---------------------------------------------------------------------------
const assigningCv = ref(false)

/** URL del PDF que el perfil usa ya como CV de ese idioma, o null. */
function currentCvUrl(lang: 'es' | 'en'): string | null {
  return (lang === 'es' ? profile.value?.cvUrlEs : profile.value?.cvUrlEn) ?? null
}

/** true si este objeto es el CV ya asignado al idioma. */
function isCurrentCv(object: AdminStorageObject, lang: 'es' | 'en'): boolean {
  return currentCvUrl(lang) === object.url
}

/**
 * Asigna (o quita) este PDF como CV del idioma. `url` null lo desasigna.
 * Reenvía el perfil completo con solo ese campo cambiado.
 */
async function assignCv(object: AdminStorageObject, lang: 'es' | 'en', url: string | null) {
  const current = profile.value
  if (!current) {
    notify('Todavía no se ha cargado el perfil. Vuelve a intentarlo.', 'error')
    return
  }

  // La API exige estos tres en cualquier PUT del perfil (@NotBlank y
  // @LocalizedNonBlank). El mismo aviso que da Tecnologías antes de mandar.
  const missing: string[] = []
  if (!current.fullName.trim()) missing.push('el nombre completo')
  if (!hasLocalizedText(current.headline)) missing.push('el titular bilingüe')
  if (!hasLocalizedText(current.bio)) missing.push('la biografía bilingüe')
  if (missing.length) {
    notify(`No se puede asignar: al perfil le falta ${missing.join(' y ')}, y la API lo exige al guardarlo.`, 'error')
    return
  }

  const body: ProfileRequest = {
    fullName: current.fullName,
    headline: current.headline ?? {},
    bio: current.bio ?? {},
    location: current.location,
    githubUrl: current.githubUrl,
    linkedinUrl: current.linkedinUrl,
    emailPublic: current.emailPublic,
    websiteUrl: current.websiteUrl,
    cvUrlEs: lang === 'es' ? url : current.cvUrlEs,
    cvUrlEn: lang === 'en' ? url : current.cvUrlEn,
    avatarUrl: current.avatarUrl,
    skills: current.skills,
    experiences: current.experiences
  }

  assigningCv.value = true
  try {
    profile.value = await api.put<AdminProfile>('/profile', body)
    const label = lang === 'es' ? 'español' : 'inglés'
    notify(url ? `CV en ${label} actualizado` : `CV en ${label} quitado`, 'success')
  } catch (err) {
    const apiError = toApiError(err)
    notify(apiError.problem.detail || apiError.message, 'error')
  } finally {
    assigningCv.value = false
  }
}

/** Ítems del menú "Usar como CV" de un PDF. */
function cvMenuItems(object: AdminStorageObject): DropdownMenuItem[][] {
  const entry = (lang: 'es' | 'en', label: string, icon: string) => {
    const isCurrent = isCurrentCv(object, lang)
    return {
      label: isCurrent ? `${label} (actual)` : label,
      icon,
      disabled: isCurrent || assigningCv.value,
      onSelect: () => assignCv(object, lang, object.url)
    }
  }

  const clearEntry = (lang: 'es' | 'en', label: string) => ({
    label,
    icon: 'i-lucide-x',
    disabled: assigningCv.value,
    onSelect: () => assignCv(object, lang, null)
  })

  const groups: DropdownMenuItem[][] = [
    [
      entry('es', 'Usar como CV en español', 'i-lucide-file-check'),
      entry('en', 'Usar como CV en inglés', 'i-lucide-file-check')
    ]
  ]

  // Solo se ofrece quitar el que esté puesto: dos entradas "quitar" fijas
  // serían ruido en el caso normal (ninguno o uno asignado).
  const clears = []
  if (isCurrentCv(object, 'es')) clears.push(clearEntry('es', 'Quitar como CV en español'))
  if (isCurrentCv(object, 'en')) clears.push(clearEntry('en', 'Quitar como CV en inglés'))
  if (clears.length) groups.push(clears)

  return groups
}

// ---------------------------------------------------------------------------
// BORRADO
// ---------------------------------------------------------------------------
const deleteOpen = ref(false)
const deleteTarget = ref<AdminStorageObject | null>(null)
const deleting = ref(false)

function askDelete(object: AdminStorageObject) {
  deleteTarget.value = object
  deleteOpen.value = true
}

async function confirmDelete() {
  const target = deleteTarget.value
  if (!target) return

  deleting.value = true
  try {
    await api.remove(`/storage/${encodeURIComponent(target.key)}`)
    notify('Archivo borrado', 'success')
    deleteOpen.value = false
    // Si era el último de la página y no es la primera, retrocede para no
    // quedarse en una página vacía.
    if (objects.value.length === 1 && page.value > 1) {
      page.value -= 1
    } else {
      await refresh()
    }
  } catch (err) {
    const apiError = toApiError(err)
    notify(apiError.problem.detail || apiError.message, 'error')
  } finally {
    deleting.value = false
  }
}
</script>

<template>
  <UDashboardPanel id="admin-medios">
    <template #header>
      <UDashboardNavbar title="Medios">
        <template #leading>
          <UDashboardSidebarCollapse />
        </template>

        <template #right>
          <ColorModeToggle />
          <UButton
            label="Subir"
            icon="i-lucide-upload"
            @click="openUpload"
          />
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
        Todos los archivos del bucket. Se pueden subir desde aquí o desde las fichas de perfil, proyectos y
        certificados; un archivo en uso no se puede borrar.
      </p>

      <!-- Filtros -->
      <div class="flex flex-col gap-3 sm:flex-row sm:items-center">
        <UInput
          v-model="search"
          icon="i-lucide-search"
          placeholder="Buscar por nombre…"
          class="w-full sm:max-w-xs"
          aria-label="Buscar archivos"
        />

        <USelect
          v-model="typeFilter"
          :items="[
            { label: 'Todos los tipos', value: 'todos' },
            { label: 'Solo imágenes', value: 'imagen' },
            { label: 'Solo PDF', value: 'pdf' }
          ]"
          class="w-full sm:w-48"
          aria-label="Filtrar por tipo"
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
          v-else-if="status === 'success'"
          class="text-muted ms-auto text-sm"
        >
          {{ objectsLabel(totalElements) }}
        </p>
      </div>

      <!-- Cargando -->
      <div
        v-if="status === 'pending'"
        class="grid gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4"
        aria-hidden="true"
      >
        <USkeleton
          v-for="i in 8"
          :key="i"
          class="h-48 w-full"
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
              No se pudieron cargar los archivos
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

      <!-- Bucket vacío -->
      <UCard
        v-else-if="!objects.length"
        variant="subtle"
      >
        <div class="flex flex-col items-start gap-3">
          <div>
            <p class="font-medium text-highlighted">
              No hay archivos en el bucket
            </p>
            <p class="text-muted text-sm">
              Aquí aparecerán las imágenes y PDF que subas. Puedes subirlos desde aquí o desde las fichas de perfil,
              proyectos y certificados.
            </p>
          </div>
          <UButton
            label="Subir archivo"
            icon="i-lucide-upload"
            @click="openUpload"
          />
        </div>
      </UCard>

      <!-- Ningún resultado de los filtros -->
      <UCard
        v-else-if="!visibleObjects.length"
        variant="subtle"
      >
        <div class="flex flex-col items-start gap-3">
          <div>
            <p class="font-medium text-highlighted">
              Ningún archivo coincide
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

      <!-- Rejilla de archivos -->
      <div
        v-else
        class="grid gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4"
      >
        <UCard
          v-for="object in visibleObjects"
          :key="object.key"
          class="flex flex-col"
        >
          <!-- Miniatura / icono de PDF -->
          <div class="flex h-40 items-center justify-center overflow-hidden rounded-lg bg-elevated">
            <img
              v-if="!isPdf(object)"
              :src="object.url"
              :alt="object.key"
              loading="lazy"
              class="h-full w-full object-contain"
            >
            <UIcon
              v-else
              name="i-lucide-file-text"
              class="text-muted size-12"
            />
          </div>

          <div class="mt-3 flex flex-col gap-1">
            <div class="flex items-center justify-between gap-2">
              <div class="flex flex-wrap items-center gap-1.5">
                <UBadge
                  color="neutral"
                  variant="outline"
                  size="sm"
                >
                  {{ typeLabel(object) }}
                </UBadge>
                <!-- Uso declarado al subir (avatar, miniatura, CV...). -->
                <UBadge
                  color="neutral"
                  variant="subtle"
                  size="sm"
                >
                  {{ useLabel(object) }}
                </UBadge>
                <!-- Qué idiomas del CV usan ya este PDF. -->
                <UBadge
                  v-if="isPdf(object) && isCurrentCv(object, 'es')"
                  color="success"
                  variant="subtle"
                  size="sm"
                >
                  CV ES
                </UBadge>
                <UBadge
                  v-if="isPdf(object) && isCurrentCv(object, 'en')"
                  color="success"
                  variant="subtle"
                  size="sm"
                >
                  CV EN
                </UBadge>
              </div>
              <span class="text-muted text-xs">{{ humanSize(object.size) }}</span>
            </div>

            <code
              class="text-muted truncate text-xs"
              :title="object.key"
            >{{ object.key }}</code>
            <span class="text-dimmed text-xs">
              {{ adminDate(object.lastModified, { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' }) }}
            </span>
          </div>

          <template #footer>
            <div class="flex flex-col gap-2">
              <!-- Solo los PDF pueden ser CV; en el resto no se pinta nada. -->
              <UDropdownMenu
                v-if="isPdf(object)"
                :items="cvMenuItems(object)"
                :content="{ align: 'start' }"
              >
                <UButton
                  label="Usar como CV"
                  icon="i-lucide-file-check"
                  color="neutral"
                  variant="soft"
                  size="sm"
                  block
                  :loading="assigningCv"
                  :aria-label="`Usar ${object.key} como CV`"
                />
              </UDropdownMenu>

              <div class="flex items-center justify-between gap-1">
                <div class="flex gap-1">
                  <UButton
                    label="Copiar URL"
                    icon="i-lucide-link"
                    color="neutral"
                    variant="ghost"
                    size="sm"
                    @click="copyUrl(object)"
                  />
                  <UButton
                    label="Descargar"
                    icon="i-lucide-download"
                    color="neutral"
                    variant="ghost"
                    size="sm"
                    :to="object.url"
                    :download="object.key"
                  />
                </div>
                <UButton
                  label="Borrar"
                  icon="i-lucide-trash-2"
                  color="error"
                  variant="ghost"
                  size="sm"
                  @click="askDelete(object)"
                />
              </div>
            </div>
          </template>
        </UCard>
      </div>

      <!-- Paginación -->
      <div
        v-if="status === 'success' && totalPages > 1"
        class="flex items-center justify-between"
      >
        <p class="text-muted text-sm">
          Página {{ page }} de {{ totalPages }}
        </p>
        <div class="flex gap-2">
          <UButton
            label="Anterior"
            icon="i-lucide-chevron-left"
            color="neutral"
            variant="soft"
            size="sm"
            :disabled="page <= 1"
            @click="page -= 1"
          />
          <UButton
            label="Siguiente"
            trailing-icon="i-lucide-chevron-right"
            color="neutral"
            variant="soft"
            size="sm"
            :disabled="page >= totalPages"
            @click="page += 1"
          />
        </div>
      </div>

      <!-- Modal de subida -->
      <UModal
        v-model:open="uploadOpen"
        title="Subir archivo"
        :dismissible="!uploading"
        :close="!uploading"
      >
        <template #body>
          <div class="flex flex-col gap-4">
            <UFormField
              label="Tipo de archivo"
              description="Determina el formato admitido y la recomendación."
            >
              <USelect
                v-model="uploadUse"
                :items="(uses ?? []).map(u => ({ label: u.label, value: u.use }))"
                class="w-full"
                :disabled="uploading"
              />
            </UFormField>

            <UAlert
              v-if="currentUse"
              color="neutral"
              variant="subtle"
              icon="i-lucide-info"
              :title="`Recomendación para ${currentUse.label.toLowerCase()}`"
              :description="`${currentUse.recommendation} Formatos: ${currentUse.formats.join(', ').toUpperCase()}.`"
            />

            <UAlert
              v-if="uploadWarning"
              color="warning"
              variant="subtle"
              icon="i-lucide-triangle-alert"
              title="Las dimensiones no encajan"
              :description="uploadWarning"
            />

            <input
              ref="uploadInput"
              type="file"
              :accept="acceptForCurrentUse"
              class="hidden"
              tabindex="-1"
              aria-hidden="true"
              @change="onFileSelected"
            >
            <UButton
              label="Elegir archivo y subir"
              icon="i-lucide-upload"
              class="self-start"
              :loading="uploading"
              @click="uploadInput?.click()"
            />
          </div>
        </template>

        <template #footer>
          <div class="flex w-full justify-end">
            <UButton
              label="Cerrar"
              color="neutral"
              variant="soft"
              :disabled="uploading"
              @click="uploadOpen = false"
            />
          </div>
        </template>
      </UModal>

      <!-- Modal de borrado -->
      <UModal
        v-model:open="deleteOpen"
        title="¿Borrar este archivo?"
        :dismissible="!deleting"
        :close="!deleting"
      >
        <template #body>
          <div
            v-if="deleteTarget"
            class="flex flex-col gap-3"
          >
            <img
              v-if="!isPdf(deleteTarget)"
              :src="deleteTarget.url"
              :alt="deleteTarget.key"
              class="mx-auto max-h-40 rounded-lg object-contain"
            >
            <p class="text-muted text-sm">
              Se borrará
              <code class="text-highlighted">{{ deleteTarget.key }}</code>
              del bucket. Esta acción no se puede deshacer.
            </p>
            <UAlert
              color="warning"
              variant="subtle"
              icon="i-lucide-triangle-alert"
              title="No se borra si está en uso"
              description="Si es el avatar del perfil, la miniatura de un proyecto, una imagen de la galería o la de un certificado, la API lo rechazará y te dirá dónde se usa: primero hay que dejar de usarlo."
            />
          </div>
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
