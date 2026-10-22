<script setup lang="ts">
// ======================================================================
// PERFIL
//
// Edita los datos del perfil que NO tenían sección propia: identidad
// (nombre, titular y biografía bilingües, ubicación), enlaces públicos,
// avatar y CV. Hasta ahora solo el stack (Tecnologías) y el CV (Medios)
// eran editables, y los demás campos quedaban fuera del alcance del panel.
//
// PUT /admin/profile REEMPLAZA el perfil entero, así que aquí se reenvía
// tal cual lo que NO se edita: skills (es de Tecnologías) y experiences
// (todavía no tienen sección) viajan intactas para no borrarlas.
//
// Avatar y CV se suben aquí y se eligen del bucket; el avatar además acepta
// una URL externa. En el bucket no hay una columna de etiquetas: la "etiqueta"
// es el uso. El modal de avatar ofrece todas las imágenes (cualquiera puede ser
// avatar) y el de CV todos los PDF, porque varios comparten esa extensión y
// nada los distingue salvo la asignación. Subir directamente evita el problema
// de raíz: el PDF recién subido ES el CV de ese idioma.
//
// El CV es UNO por idioma: al sustituirlo, la API borra el PDF anterior si ya
// no lo usa nadie (lo decide el backend al guardar). El avatar no se borra
// nunca al cambiarlo: el bucket puede tener varios y se puede querer volver.
// ======================================================================
import * as z from 'zod'
import type { FormSubmitEvent } from '@nuxt/ui'
import { toApiError, unwrapApiError } from '~/composables/useApi'

definePageMeta({ layout: 'dashboard' })

const api = useAdminApi()
const { notify } = useAppNotify()

// Como el resto del panel, en cliente: el $fetch de Nuxt no reenvía cookies
// en SSR y llegaríamos al proxy sin sesión.
const { data: profile, status, error, refresh } = await useAsyncData(
  'admin-profile-page',
  () => api.get<AdminProfile>('/profile'),
  { server: false }
)

const loadError = computed(() => unwrapApiError(error.value))
const loadErrorMessage = computed(
  () => loadError.value?.problem.detail ?? loadError.value?.message ?? 'No se pudo conectar con el servidor'
)

// ---------------------------------------------------------------------------
// ESTADO DEL FORMULARIO
//
// Se inicializa cuando llega el perfil. La API manda null para lo vacío; el
// formulario trabaja siempre con cadena para poder usar v-model directo.
// ---------------------------------------------------------------------------
function pickLocalized(text: LocalizedText | null | undefined): { es: string, en: string } {
  return { es: text?.es ?? '', en: text?.en ?? '' }
}

const state = reactive({
  fullName: '',
  headline: { es: '', en: '' },
  bio: { es: '', en: '' },
  location: '',
  githubUrl: '',
  linkedinUrl: '',
  websiteUrl: '',
  emailPublic: '',
  avatarUrl: '',
  cv: { es: '', en: '' } as Record<'es' | 'en', string>
})

// Un solo punto donde volcar el perfil al formulario: al cargar y, si acaso,
// al recargar. Evita duplicar la asignación campo a campo.
function fillFrom(current: AdminProfile) {
  state.fullName = current.fullName
  state.headline = pickLocalized(current.headline)
  state.bio = pickLocalized(current.bio)
  state.location = current.location ?? ''
  state.githubUrl = current.githubUrl ?? ''
  state.linkedinUrl = current.linkedinUrl ?? ''
  state.websiteUrl = current.websiteUrl ?? ''
  state.emailPublic = current.emailPublic ?? ''
  state.avatarUrl = current.avatarUrl ?? ''
  state.cv = { es: current.cvUrlEs ?? '', en: current.cvUrlEn ?? '' }
}

watch(profile, (value) => {
  if (value) fillFrom(value)
}, { immediate: true })

// ---------------------------------------------------------------------------
// CAMBIOS SIN GUARDAR
//
// El formulario no dice en ningún sitio qué se ha tocado, y como el PUT
// reemplaza el perfil entero conviene verlo antes de pulsar Guardar. Se compara
// el estado actual contra el perfil guardado (profile.value), aplicando las
// mismas normalizaciones que toBody: cadena vacía es null, y el texto bilingüe
// se compara en su forma sin claves vacías. Así "3 cambios" significa de verdad
// "3 campos que van a cambiar en el PUT".
// ---------------------------------------------------------------------------
const ETIQUETAS_CAMPO: Record<string, string> = {
  'fullName': 'nombre completo',
  'headline': 'titular',
  'bio': 'biografía',
  'location': 'ubicación',
  'githubUrl': 'GitHub',
  'linkedinUrl': 'LinkedIn',
  'websiteUrl': 'web',
  'emailPublic': 'email público',
  'avatarUrl': 'avatar',
  'cv.es': 'CV en español',
  'cv.en': 'CV en inglés'
}

/** Normaliza un valor del formulario a como lo mandaría toBody, para comparar. */
function valorNormalizado(campo: string): string {
  switch (campo) {
    case 'fullName':
      return state.fullName.trim()
    case 'headline':
      return JSON.stringify(toLocalized(state.headline))
    case 'bio':
      return JSON.stringify(toLocalized(state.bio))
    case 'avatarUrl':
      return JSON.stringify(orNull(state.avatarUrl))
    case 'cv.es':
      return JSON.stringify(orNull(state.cv.es))
    case 'cv.en':
      return JSON.stringify(orNull(state.cv.en))
    case 'location':
      return JSON.stringify(orNull(state.location))
    case 'githubUrl':
      return JSON.stringify(orNull(state.githubUrl))
    case 'linkedinUrl':
      return JSON.stringify(orNull(state.linkedinUrl))
    case 'websiteUrl':
      return JSON.stringify(orNull(state.websiteUrl))
    case 'emailPublic':
      return JSON.stringify(orNull(state.emailPublic))
    default:
      return ''
  }
}

/** Valor guardado del mismo campo, normalizado igual, para comparar peras con peras. */
function valorGuardado(campo: string, current: AdminProfile): string {
  switch (campo) {
    case 'fullName':
      return current.fullName.trim()
    case 'headline':
      return JSON.stringify(toLocalized(pickLocalized(current.headline)))
    case 'bio':
      return JSON.stringify(toLocalized(pickLocalized(current.bio)))
    case 'avatarUrl':
      return JSON.stringify(orNull(current.avatarUrl ?? ''))
    case 'cv.es':
      return JSON.stringify(orNull(current.cvUrlEs ?? ''))
    case 'cv.en':
      return JSON.stringify(orNull(current.cvUrlEn ?? ''))
    case 'location':
      return JSON.stringify(orNull(current.location ?? ''))
    case 'githubUrl':
      return JSON.stringify(orNull(current.githubUrl ?? ''))
    case 'linkedinUrl':
      return JSON.stringify(orNull(current.linkedinUrl ?? ''))
    case 'websiteUrl':
      return JSON.stringify(orNull(current.websiteUrl ?? ''))
    case 'emailPublic':
      return JSON.stringify(orNull(current.emailPublic ?? ''))
    default:
      return ''
  }
}

/** Campos que van a cambiar si se guarda ahora. Vacío si no hay nada que guardar. */
const camposCambiados = computed(() => {
  const current = profile.value
  if (!current) return []
  return Object.keys(ETIQUETAS_CAMPO).filter(
    campo => valorNormalizado(campo) !== valorGuardado(campo, current)
  )
})

const hayCambios = computed(() => camposCambiados.value.length > 0)

/** true si ese campo cambiará al guardar; se usa para resaltarlo en el formulario. */
function campoCambiado(campo: string): boolean {
  return camposCambiados.value.includes(campo)
}

/** Clase de resalte para una tarjeta si alguno de sus campos cambió. */
function claseCambio(...campos: string[]): string {
  return campos.some(campoCambiado) ? 'ring-2 ring-warning' : ''
}

/** "3 cambios sin guardar" / "1 cambio sin guardar". */
const textoCambios = computed(() => {
  const n = camposCambiados.value.length
  return n === 1 ? '1 cambio sin guardar' : `${n} cambios sin guardar`
})

// ---------------------------------------------------------------------------
// ESQUEMA
//
// Reproduce las restricciones de ProfileRequest.java. fullName (@NotBlank) y
// headline/bio (@LocalizedNonBlank) son obligatorios; las URLs solo se
// comprueban si se escriben, y emailPublic es @Email.
// ---------------------------------------------------------------------------
function optionalUrl(label: string) {
  return z
    .string()
    .max(255, `${label} no puede superar 255 caracteres`)
    .refine(value => value === '' || /^https?:\/\/\S+$/i.test(value), `${label} debe empezar por http:// o https://`)
}

/**
 * URL de un fichero del bucket (avatar o CV). Vacío es válido (no hay avatar o
 * no hay CV): lo único que se exige es que, si hay algo, acabe en `/files/<uuid>.<ext>`,
 * que es la clave con la que la API sirve los objetos. Las pone el propio panel
 * (subida o modal), así que un valor que no encaje es un dato roto, no un error
 * de tecleo.
 */
function bucketUrl(label: string) {
  const claveBucket = /\/files\/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\.(jpg|png|webp|pdf)$/i
  return z
    .string()
    .max(255, `${label} no puede superar 255 caracteres`)
    .refine(value => value === '' || claveBucket.test(value), `${label} debe ser un archivo del bucket`)
}

const schema = z
  .object({
    fullName: z
      .string('El nombre completo es obligatorio')
      .min(1, 'El nombre completo es obligatorio')
      .max(120, 'El nombre completo no puede superar 120 caracteres'),
    headline: z.object({ es: z.string(), en: z.string() }),
    bio: z.object({ es: z.string(), en: z.string() }),
    location: z.string().max(120, 'La ubicación no puede superar 120 caracteres'),
    githubUrl: optionalUrl('La URL de GitHub'),
    linkedinUrl: optionalUrl('La URL de LinkedIn'),
    websiteUrl: optionalUrl('La URL de la web'),
    emailPublic: z
      .string()
      .max(320, 'El email no puede superar 320 caracteres')
      .refine(value => value === '' || z.email().safeParse(value).success, 'Debe ser un email válido'),
    // El avatar puede ser un fichero del bucket o una URL externa (una imagen
    // pública, tu propio hosting...). Basta con que sea http(s); si es http se
    // avisa en la tarjeta, no se bloquea.
    avatarUrl: optionalUrl('La URL del avatar'),
    // Los CV, en cambio, son siempre del bucket: se sirven por /public/cv/{lang}
    // y ahí una URL externa no aporta nada. La forma es la clave que sirve la
    // API (.../files/<uuid>.<ext>); no se comprueba el host porque el front no
    // conoce APP_STORAGE_PUBLIC_URL.
    cv: z.object({
      es: bucketUrl('La URL del CV en español'),
      en: bucketUrl('La URL del CV en inglés')
    })
  })
  // @LocalizedNonBlank exige es O en con texto. El issue se ancla en `.es`
  // para que el mensaje salga bajo un campo que existe.
  .superRefine((value, ctx) => {
    if (!value.headline.es.trim() && !value.headline.en.trim()) {
      ctx.addIssue({ code: 'custom', path: ['headline', 'es'], message: 'Introduce el titular en español o en inglés' })
    }
    if (!value.bio.es.trim() && !value.bio.en.trim()) {
      ctx.addIssue({ code: 'custom', path: ['bio', 'es'], message: 'Introduce la biografía en español o en inglés' })
    }
  })

type Schema = z.input<typeof schema>

const formRef = ref<{ setErrors: (errors: Array<{ path: string, message: string }>) => void } | null>(null)
const formError = ref('')

// ---------------------------------------------------------------------------
// ENSAMBLADO DEL BODY
// ---------------------------------------------------------------------------
/** Mapa bilingüe sin claves vacías; null si no hay texto (como la API). */
function toLocalized(value: { es: string, en: string }): LocalizedText | null {
  const es = value.es.trim()
  const en = value.en.trim()
  return es || en ? { ...(es ? { es } : {}), ...(en ? { en } : {}) } : null
}

/** Cadena vacía -> null, que es como la API guarda "ausente". */
function orNull(value: string): string | null {
  return value.trim() ? value.trim() : null
}

function toBody(current: AdminProfile): ProfileRequest {
  return {
    fullName: state.fullName.trim(),
    headline: toLocalized(state.headline) ?? {},
    bio: toLocalized(state.bio) ?? {},
    location: orNull(state.location),
    githubUrl: orNull(state.githubUrl),
    linkedinUrl: orNull(state.linkedinUrl),
    emailPublic: orNull(state.emailPublic),
    websiteUrl: orNull(state.websiteUrl),
    // Los CV SÍ se editan aquí (subir o elegir del bucket), así que salen del
    // formulario, no del perfil leído.
    cvUrlEs: orNull(state.cv.es),
    cvUrlEn: orNull(state.cv.en),
    avatarUrl: orNull(state.avatarUrl),
    // Skills y experiences no: se reenvían tal cual para no borrarlas.
    skills: current.skills,
    experiences: current.experiences
  }
}

// ---------------------------------------------------------------------------
// GUARDADO
// ---------------------------------------------------------------------------
const saving = ref(false)

/**
 * Vuelve el formulario al último estado guardado. No toca el servidor: solo
 * rellena otra vez desde profile.value, que es la foto de lo que hay en la base.
 */
function descartarCambios() {
  const current = profile.value
  if (current) fillFrom(current)
  formError.value = ''
}

async function onSubmit(_event: FormSubmitEvent<Schema>) {
  const current = profile.value
  if (!current) return

  formError.value = ''
  saving.value = true
  try {
    profile.value = await api.put<AdminProfile>('/profile', toBody(current))
    notify('Perfil guardado', 'success')
  } catch (err) {
    const apiError = toApiError(err)
    // El 400 de validación de Spring trae el motivo real en errors[]; el
    // detail es genérico, así que se pinta campo a campo si viene.
    if (apiError.problem?.errors?.length) {
      formRef.value?.setErrors(
        apiError.problem.errors.map(issue => ({ path: issue.field ?? '', message: issue.message }))
      )
      formError.value = 'Revisa los campos marcados.'
    } else {
      formError.value = apiError.problem?.detail ?? apiError.message
    }
  } finally {
    saving.value = false
  }
}

// ---------------------------------------------------------------------------
// SUBIDA AL BUCKET
//
// Tanto el avatar como el CV se suben desde aquí. Es a propósito: en el bucket
// hay varios PDFs y varias imágenes y nada distingue cuál es cuál, así que
// "subirlo ahora" es lo único que asigna sin ambigüedad. Subir sigue siendo
// POST /admin/storage (el mismo endpoint que Medios); lo que cambia es que el
// resultado se queda asignado al perfil en el momento.
//
// La subida NO guarda el perfil: deja la URL en el formulario y el usuario
// decide con Guardar. Así un CV subido por error no pisa el que había.
// ---------------------------------------------------------------------------
const uploadingAvatar = ref(false)
const uploadingCv = ref<'es' | 'en' | null>(null)
const avatarInput = ref<HTMLInputElement | null>(null)
// Un solo input reutilizado para los dos idiomas. Tener dos con :ref condicional
// dentro del v-for no enganchaba el elemento (el ref quedaba null y el botón no
// abría nada); con uno solo y la variable que recuerda el idioma, es imposible
// que el ref y el botón se desincronicen.
const cvInput = ref<HTMLInputElement | null>(null)
const pendingCvLang = ref<'es' | 'en'>('es')

const ALLOWED_IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp']

/**
 * Sube un fichero con el uso indicado y devuelve su URL, o null si algo falló
 * (ya avisado). Centraliza el try/catch y el aviso de error para no repetirlo
 * en cada botón.
 */
async function uploadFile(file: File, use: StorageUse): Promise<string | null> {
  try {
    const result = await api.upload<StorageUploadResult>('/storage', file, use)
    return result.url
  } catch (err) {
    const apiError = toApiError(err)
    notify(apiError.problem.detail || apiError.message, 'error')
    return null
  }
}

// ---------------------------------------------------------------------------
// AVATAR
//
// Se elige del bucket, pero SOLO entre las imágenes que ya son avatar: no hay
// una columna de etiquetas, así que "es avatar" se deduce de a quién apunta el
// perfil. En la práctica eso son las imágenes que se han usado como avatar; el
// resto de imágenes del bucket (miniaturas, galerías, certificados) no se
// ofrecen, porque elegir una miniatura como avatar casi siempre es un error.
//
// No hay una tabla de metadatos en el bucket: la "etiqueta" es el uso, y el
// uso lo conoce el perfil. Filtrar por contentType habría ofrecido las 6
// imágenes mezcladas; esto ofrece solo las que tienen sentido aquí.
// ---------------------------------------------------------------------------
const avatarOpen = ref(false)
const { data: storage } = await useAsyncData(
  'admin-profile-storage',
  () => api.get<AdminPage<AdminStorageObject>>('/storage', { page: 0, size: 100 }),
  { server: false }
)

const objects = computed(() => storage.value?.items ?? [])

function isImage(object: AdminStorageObject): boolean {
  return object.contentType?.startsWith('image/') ?? false
}

function isPdf(object: AdminStorageObject): boolean {
  return object.contentType === 'application/pdf'
}

/**
 * ¿Es esta imagen un avatar? Lo dice el uso declarado al subir, que ahora se
 * guarda. Para las imágenes sin uso (las anteriores a la tabla) se cae al
 * contentType: cualquier imagen puede serlo, no hay nada mejor que deducir. Un
 * fichero con uso de otro tipo (thumbnail, gallery, image) NO se ofrece como
 * avatar: eso es justo lo que se quería separar.
 */
function avatarCandidate(object: AdminStorageObject): boolean {
  if (!isImage(object)) return false
  return object.use === null || object.use === 'avatar'
}

/**
 * ¿Es este PDF un CV? Lo dice el uso declarado. Los PDF sin uso (anteriores a la
 * tabla) se ofrecen igual, porque el CV es el único uso que admite PDF: no hay
 * ambigüedad posible y dejarlos fuera escondería un CV real.
 */
function cvCandidate(object: AdminStorageObject): boolean {
  if (!isPdf(object)) return false
  return object.use === null || object.use === 'cv'
}

/**
 * Imágenes ofrecidas como avatar: los avatares declarados, las imágenes sin uso
 * (anteriores a la tabla) y la que esté puesta si no aparece en el listado (el
 * avatar podría ser una URL externa o un objeto que no se ha cargado).
 */
const avatarPool = computed(() => {
  const list = objects.value.filter(avatarCandidate)
  if (state.avatarUrl && !list.some(object => object.url === state.avatarUrl)) {
    // El avatar actual es externo o no está en el listado: se añade delante
    // para que se vea marcado como el que está en uso.
    list.unshift({
      key: state.avatarUrl.split('/').pop() ?? state.avatarUrl,
      url: state.avatarUrl,
      size: 0,
      lastModified: '',
      contentType: null,
      use: 'avatar'
    })
  }
  return list
})

/** true si el avatar puesto es una URL de fuera del bucket. */
const avatarExterno = computed(() =>
  !!state.avatarUrl && !state.avatarUrl.includes('/files/')
)

function pickAvatarFile() {
  // Se limpia para poder volver a elegir el mismo fichero si algo falló.
  if (avatarInput.value) avatarInput.value.value = ''
  avatarInput.value?.click()
}

async function onAvatarSelected(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0] ?? null
  if (input) input.value = ''
  if (!file) return

  if (!ALLOWED_IMAGE_TYPES.includes(file.type)) {
    notify('Solo se admiten imágenes JPEG, PNG o WebP', 'error')
    return
  }

  uploadingAvatar.value = true
  try {
    const url = await uploadFile(file, 'avatar')
    if (url) {
      state.avatarUrl = url
      notify('Avatar subido. Pulsa Guardar para aplicarlo.', 'success')
    }
  } finally {
    uploadingAvatar.value = false
  }
}

function chooseAvatar(url: string) {
  state.avatarUrl = url
  avatarOpen.value = false
}

function clearAvatar() {
  state.avatarUrl = ''
}

// ---------------------------------------------------------------------------
// CV
//
// Subir un PDF con uso "cv" solo lo deja en el bucket; el portfolio lee
// profile.cvUrlEs/cvUrlEn. Aquí se sube Y se asigna en un solo gesto: el PDF
// recién subido es el CV de ese idioma, sin tener que elegir entre PDFs que no
// se distinguen. El de elegir del bucket también existe, y ahí se ofrecen
// todos los PDFs (el asignado a cada idioma va marcado).
//
// Igual que el avatar, subir NO guarda: deja la URL en el formulario. El PUT
// completo lo hace el botón Guardar, reenviando skills y experiences.
// ---------------------------------------------------------------------------
const cvOpen = ref(false)

const cvPdfs = computed(() =>
  objects.value.filter(cvCandidate).sort((a, b) => (b.lastModified || '').localeCompare(a.lastModified || ''))
)

function cvUrlFor(lang: 'es' | 'en'): string | null {
  return (lang === 'es' ? profile.value?.cvUrlEs : profile.value?.cvUrlEn) ?? null
}

function isCurrentCv(object: AdminStorageObject, lang: 'es' | 'en'): boolean {
  return state.cv[lang] === object.url
}

function pickCvFile(lang: 'es' | 'en') {
  pendingCvLang.value = lang
  if (cvInput.value) cvInput.value.value = ''
  cvInput.value?.click()
}

async function onCvSelected(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0] ?? null
  if (input) input.value = ''
  if (!file) return

  if (file.type !== 'application/pdf') {
    notify('Para el CV solo se admite PDF', 'error')
    return
  }

  const lang = pendingCvLang.value
  uploadingCv.value = lang
  try {
    const url = await uploadFile(file, 'cv')
    if (url) {
      // Recién subido: se asigna ya en el formulario (el PUT lo hará Guardar).
      state.cv[lang] = url
      notify(`CV en ${lang === 'es' ? 'español' : 'inglés'} subido. Pulsa Guardar para aplicarlo.`, 'success')
    }
  } finally {
    uploadingCv.value = null
  }
}

function chooseCv(url: string, lang: 'es' | 'en') {
  state.cv[lang] = url
  cvOpen.value = false
}

/** Nombre legible de un CV del formulario, para la tarjeta de solo lectura. */
function cvName(url: string | null): string | null {
  return url ? url.split('/').pop() ?? url : null
}
</script>

<template>
  <UDashboardPanel id="admin-perfil">
    <template #header>
      <UDashboardNavbar title="Perfil">
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
        Tu identidad pública: nombre, titular, biografía, enlaces, avatar y CV. El stack se edita en Tecnologías; lo
        demás, aquí.
      </p>

      <!-- Cargando. -->
      <div
        v-if="status === 'pending'"
        class="space-y-3"
        aria-hidden="true"
      >
        <USkeleton
          v-for="i in 6"
          :key="i"
          class="h-12 w-full"
        />
      </div>

      <!-- Error de carga. -->
      <UCard
        v-else-if="error"
        variant="subtle"
      >
        <div class="flex flex-col items-start gap-3">
          <div>
            <p class="font-medium text-highlighted">
              No se pudo cargar el perfil
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

      <UForm
        v-else
        ref="formRef"
        :schema="schema"
        :state="state"
        class="flex max-w-3xl flex-col gap-6"
        @submit="onSubmit"
      >
        <!-- Barra de estado: qué va a cambiar y el Guardar, siempre a la vista. -->
        <div class="bg-default/90 supports-backdrop-blur:bg-default/70 sticky top-0 z-10 flex flex-wrap items-center justify-between gap-3 rounded-lg px-1 py-2 backdrop-blur">
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
              {{ camposCambiados.map(c => ETIQUETAS_CAMPO[c]).join(', ') }}
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
              :loading="saving"
              :disabled="!hayCambios"
            />
          </div>
        </div>

        <!-- ------------------------------------------------------------ -->
        <!-- IDENTIDAD -->
        <!-- ------------------------------------------------------------ -->
        <UCard :class="claseCambio('fullName', 'headline', 'bio', 'location')">
          <template #header>
            <h3 class="font-semibold text-highlighted">
              Identidad
            </h3>
          </template>

          <div class="flex flex-col gap-4">
            <UFormField
              label="Nombre completo"
              name="fullName"
              required
            >
              <UInput
                v-model="state.fullName"
                class="w-full"
                placeholder="Pepe Dev"
              />
            </UFormField>

            <UFormField
              label="Titular"
              name="headline.es"
              description="Una línea: a qué te dedicas. Rellena al menos un idioma."
              required
            >
              <div class="flex flex-col gap-2">
                <UInput
                  v-model="state.headline.es"
                  class="w-full"
                  placeholder="Español"
                >
                  <template #leading>
                    <span class="text-muted text-xs font-medium">ES</span>
                  </template>
                </UInput>
                <UInput
                  v-model="state.headline.en"
                  class="w-full"
                  placeholder="English"
                >
                  <template #leading>
                    <span class="text-muted text-xs font-medium">EN</span>
                  </template>
                </UInput>
              </div>
            </UFormField>

            <UFormField
              label="Biografía"
              name="bio.es"
              description="El texto de la sección «sobre mí». Rellena al menos un idioma."
              required
            >
              <div class="flex flex-col gap-2">
                <UTextarea
                  v-model="state.bio.es"
                  class="w-full"
                  :rows="4"
                  placeholder="Español"
                />
                <UTextarea
                  v-model="state.bio.en"
                  class="w-full"
                  :rows="4"
                  placeholder="English"
                />
              </div>
            </UFormField>

            <UFormField
              label="Ubicación"
              name="location"
            >
              <UInput
                v-model="state.location"
                class="w-full"
                placeholder="Chiapas, México"
              />
            </UFormField>
          </div>
        </UCard>

        <!-- ------------------------------------------------------------ -->
        <!-- ENLACES -->
        <!-- ------------------------------------------------------------ -->
        <UCard :class="claseCambio('githubUrl', 'linkedinUrl', 'websiteUrl', 'emailPublic')">
          <template #header>
            <h3 class="font-semibold text-highlighted">
              Enlaces
            </h3>
          </template>

          <div class="flex flex-col gap-4">
            <UFormField
              label="GitHub"
              name="githubUrl"
            >
              <UInput
                v-model="state.githubUrl"
                class="w-full"
                placeholder="https://github.com/usuario"
              />
            </UFormField>

            <UFormField
              label="LinkedIn"
              name="linkedinUrl"
            >
              <UInput
                v-model="state.linkedinUrl"
                class="w-full"
                placeholder="https://linkedin.com/in/usuario"
              />
            </UFormField>

            <UFormField
              label="Web"
              name="websiteUrl"
            >
              <UInput
                v-model="state.websiteUrl"
                class="w-full"
                placeholder="https://tu-web.dev"
              />
            </UFormField>

            <UFormField
              label="Email público"
              name="emailPublic"
              description="El que se enseña en el portfolio. No es el de acceso al panel."
            >
              <UInput
                v-model="state.emailPublic"
                class="w-full"
                placeholder="tu@email.com"
              />
            </UFormField>
          </div>
        </UCard>

        <!-- ------------------------------------------------------------ -->
        <!-- AVATAR -->
        <!-- ------------------------------------------------------------ -->
        <UCard :class="claseCambio('avatarUrl')">
          <template #header>
            <h3 class="font-semibold text-highlighted">
              Avatar
            </h3>
          </template>

          <div class="flex flex-col gap-4 sm:flex-row sm:items-start">
            <div class="bg-elevated flex size-24 shrink-0 items-center justify-center overflow-hidden rounded-full">
              <img
                v-if="state.avatarUrl"
                :src="state.avatarUrl"
                alt="Avatar actual"
                class="size-full object-cover"
              >
              <UIcon
                v-else
                name="i-lucide-user-round"
                class="text-muted size-8"
              />
            </div>

            <div class="flex flex-col items-start gap-2">
              <p class="text-muted text-sm">
                La imagen se recorta en círculo. Puedes subir una nueva, elegir una del bucket o pegar la URL de una
                imagen de fuera (tu hosting, una imagen pública...).
              </p>
              <div class="flex flex-wrap gap-2">
                <UButton
                  label="Subir imagen"
                  icon="i-lucide-upload"
                  color="neutral"
                  variant="soft"
                  :loading="uploadingAvatar"
                  @click="pickAvatarFile"
                />
                <UButton
                  label="Elegir del bucket"
                  icon="i-lucide-image"
                  color="neutral"
                  variant="soft"
                  :disabled="!avatarPool.length"
                  @click="avatarOpen = true"
                />
                <UButton
                  v-if="state.avatarUrl"
                  label="Quitar"
                  icon="i-lucide-x"
                  color="error"
                  variant="ghost"
                  @click="clearAvatar"
                />
              </div>

              <UFormField
                label="O pega una URL"
                name="avatarUrl"
                class="mt-1 w-full"
              >
                <UInput
                  v-model="state.avatarUrl"
                  class="w-full"
                  placeholder="https://tu-sitio.com/avatar.jpg"
                />
              </UFormField>

              <UAlert
                v-if="avatarExterno"
                color="warning"
                variant="subtle"
                icon="i-lucide-triangle-alert"
                title="Avatar externo"
                description="El avatar no está en el bucket. Si la URL deja de funcionar, la web mostrará un hueco; y si es http en un sitio https, el navegador puede avisar de contenido mixto."
              />

              <input
                ref="avatarInput"
                type="file"
                accept="image/jpeg,image/png,image/webp"
                class="hidden"
                @change="onAvatarSelected"
              >
            </div>
          </div>
        </UCard>

        <!-- ------------------------------------------------------------ -->
        <!-- CV (subir o elegir; se asigna al Guardar) -->
        <!-- ------------------------------------------------------------ -->
        <UCard :class="claseCambio('cv.es', 'cv.en')">
          <template #header>
            <div class="flex items-center justify-between gap-3">
              <h3 class="font-semibold text-highlighted">
                Currículum
              </h3>
              <UButton
                label="Elegir del bucket"
                icon="i-lucide-files"
                color="neutral"
                variant="ghost"
                size="sm"
                :disabled="!cvPdfs.length"
                @click="cvOpen = true"
              />
            </div>
          </template>

          <div class="flex flex-col gap-4">
            <div
              v-for="lang in (['es', 'en'] as const)"
              :key="lang"
              class="flex flex-col gap-2"
            >
              <div class="flex items-center gap-2">
                <UBadge
                  color="neutral"
                  variant="outline"
                  size="sm"
                >
                  {{ lang.toUpperCase() }}
                </UBadge>
                <span
                  v-if="cvName(state.cv[lang])"
                  class="text-muted truncate text-sm"
                  :title="state.cv[lang]"
                >
                  {{ cvName(state.cv[lang]) }}
                </span>
                <span
                  v-else
                  class="text-dimmed text-sm"
                >
                  Sin CV en {{ lang === 'es' ? 'español' : 'inglés' }}
                </span>
                <UBadge
                  v-if="state.cv[lang] && state.cv[lang] !== cvUrlFor(lang)"
                  color="warning"
                  variant="subtle"
                  size="sm"
                >
                  sin guardar
                </UBadge>
              </div>
              <div class="flex flex-wrap gap-2">
                <UButton
                  :label="state.cv[lang] ? 'Reemplazar (subir)' : 'Subir PDF'"
                  icon="i-lucide-upload"
                  color="neutral"
                  variant="soft"
                  size="sm"
                  :loading="uploadingCv === lang"
                  @click="pickCvFile(lang)"
                />
                <UButton
                  v-if="state.cv[lang]"
                  label="Quitar"
                  icon="i-lucide-x"
                  color="error"
                  variant="ghost"
                  size="sm"
                  @click="state.cv[lang] = ''"
                />
              </div>
            </div>

            <!-- Un solo input para los dos idiomas: recuerda para cuál se abrió. -->
            <input
              ref="cvInput"
              type="file"
              accept="application/pdf"
              class="hidden"
              @change="onCvSelected"
            >

            <p class="text-dimmed text-xs">
              Subir un PDF aquí lo deja asignado a ese idioma sin tener que elegir entre varios. Los cambios se aplican
              con Guardar.
            </p>
          </div>
        </UCard>

        <!-- Errores de validación del servidor. -->
        <UAlert
          v-if="formError"
          color="error"
          variant="subtle"
          icon="i-lucide-circle-x"
          :title="formError"
        />
      </UForm>

      <!-- Modal de selección de avatar -->
      <UModal
        v-model:open="avatarOpen"
        title="Elegir avatar"
      >
        <template #body>
          <div
            v-if="avatarPool.length"
            class="grid grid-cols-3 gap-3 sm:grid-cols-4"
          >
            <button
              v-for="object in avatarPool"
              :key="object.key"
              type="button"
              class="group relative aspect-square overflow-hidden rounded-lg bg-elevated ring ring-default transition hover:ring-primary"
              :aria-label="`Usar ${object.key} como avatar`"
              @click="chooseAvatar(object.url)"
            >
              <img
                :src="object.url"
                :alt="object.key"
                loading="lazy"
                class="size-full object-cover"
              >
              <span
                v-if="state.avatarUrl === object.url"
                class="bg-primary/80 absolute inset-0 flex items-center justify-center"
                aria-hidden="true"
              >
                <UIcon
                  name="i-lucide-check"
                  class="size-6"
                />
              </span>
            </button>
          </div>
          <p
            v-else
            class="text-muted text-sm"
          >
            No hay ninguna imagen en el bucket. Sube una con «Subir imagen» o pega una URL externa.
          </p>
        </template>

        <template #footer>
          <div class="flex w-full justify-end">
            <UButton
              label="Cerrar"
              color="neutral"
              variant="soft"
              @click="avatarOpen = false"
            />
          </div>
        </template>
      </UModal>

      <!-- Modal de selección de CV -->
      <UModal
        v-model:open="cvOpen"
        title="Elegir CV del bucket"
      >
        <template #body>
          <p class="text-muted mb-4 text-sm">
            Solo aparecen los PDF del bucket. El que ya está asignado a cada idioma va marcado; elige el CV ES y el CV
            EN por separado.
          </p>
          <div class="flex max-h-80 flex-col gap-2 overflow-y-auto">
            <div
              v-for="object in cvPdfs"
              :key="object.key"
              class="flex items-center justify-between gap-3 rounded-lg bg-elevated px-3 py-2 ring ring-default"
            >
              <div class="min-w-0">
                <p class="truncate text-sm">
                  {{ object.key }}
                </p>
                <div class="mt-1 flex gap-1">
                  <UBadge
                    v-if="isCurrentCv(object, 'es')"
                    color="primary"
                    variant="subtle"
                    size="sm"
                  >
                    CV ES
                  </UBadge>
                  <UBadge
                    v-if="isCurrentCv(object, 'en')"
                    color="primary"
                    variant="subtle"
                    size="sm"
                  >
                    CV EN
                  </UBadge>
                </div>
              </div>
              <div class="flex shrink-0 gap-2">
                <UButton
                  label="ES"
                  size="xs"
                  color="neutral"
                  :variant="isCurrentCv(object, 'es') ? 'solid' : 'soft'"
                  @click="chooseCv(object.url, 'es')"
                />
                <UButton
                  label="EN"
                  size="xs"
                  color="neutral"
                  :variant="isCurrentCv(object, 'en') ? 'solid' : 'soft'"
                  @click="chooseCv(object.url, 'en')"
                />
              </div>
            </div>
          </div>
        </template>

        <template #footer>
          <div class="flex w-full justify-end">
            <UButton
              label="Cerrar"
              color="neutral"
              variant="soft"
              @click="cvOpen = false"
            />
          </div>
        </template>
      </UModal>
    </template>
  </UDashboardPanel>
</template>
