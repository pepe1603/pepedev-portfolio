<script setup lang="ts">
// ======================================================================
// PERFIL
//
// Edita los datos del perfil que NO tenían sección propia: identidad
// (nombre, titular y biografía bilingües, ubicación), enlaces públicos y
// avatar. Hasta ahora solo el stack (Tecnologías) y el CV (Medios) eran
// editables, y esos campos quedaban fuera del alcance del panel.
//
// PUT /admin/profile REEMPLAZA el perfil entero, así que aquí se reenvía
// tal cual lo que NO se edita: skills (es de Tecnologías) y experiences
// (todavía no tienen sección) viajan intactas para no borrarlas. Los CV
// también se reenvían; se asignan en Medios, no aquí.
//
// El avatar se elige del bucket: los objetos ya traen su URL absoluta, así
// que asignarlo es poner esa URL en avatarUrl. Subir ficheros sigue siendo
// cosa de Medios, para no duplicar el flujo de subida.
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
  avatarUrl: ''
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
}

watch(profile, (value) => {
  if (value) fillFrom(value)
}, { immediate: true })

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
    avatarUrl: z.string().max(255, 'La URL del avatar no puede superar 255 caracteres')
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
    // Los CV no se editan aquí: se asignan en Medios y solo se reenvían.
    cvUrlEs: current.cvUrlEs,
    cvUrlEn: current.cvUrlEn,
    avatarUrl: orNull(state.avatarUrl),
    // Skills y experiences tampoco: se reenvían tal cual para no borrarlas.
    skills: current.skills,
    experiences: current.experiences
  }
}

// ---------------------------------------------------------------------------
// GUARDADO
// ---------------------------------------------------------------------------
const saving = ref(false)

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
// AVATAR
//
// Se elige del bucket: los objetos ya traen su URL absoluta. Se reutiliza
// GET /admin/storage (mismo endpoint que la página Medios) y se filtran las
// imágenes, que son las únicas válidas como avatar.
// ---------------------------------------------------------------------------
const avatarOpen = ref(false)
const { data: storage } = await useAsyncData(
  'admin-profile-storage',
  () => api.get<AdminPage<AdminStorageObject>>('/storage', { page: 0, size: 100 }),
  { server: false }
)

const avatarOptions = computed(() =>
  (storage.value?.items ?? []).filter(object => object.contentType?.startsWith('image/'))
)

function chooseAvatar(url: string) {
  state.avatarUrl = url
  avatarOpen.value = false
}

function clearAvatar() {
  state.avatarUrl = ''
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
        Tu identidad pública: nombre, titular, biografía, enlaces y avatar. El stack se edita en Tecnologías y los
        CV en Medios; aquí se muestran pero no se tocan.
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
        <!-- ------------------------------------------------------------ -->
        <!-- IDENTIDAD -->
        <!-- ------------------------------------------------------------ -->
        <UCard>
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
        <UCard>
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
        <UCard>
          <template #header>
            <h3 class="font-semibold text-highlighted">
              Avatar
            </h3>
          </template>

          <div class="flex flex-col gap-4 sm:flex-row sm:items-center">
            <div class="flex size-24 shrink-0 items-center justify-center overflow-hidden rounded-full bg-elevated">
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
                La imagen se recorta en círculo. Se elige de las que ya hay en el bucket; para subir una nueva, usa
                Medios.
              </p>
              <div class="flex flex-wrap gap-2">
                <UButton
                  label="Elegir del bucket"
                  icon="i-lucide-image"
                  color="neutral"
                  variant="soft"
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
            </div>
          </div>
        </UCard>

        <!-- ------------------------------------------------------------ -->
        <!-- CV (solo lectura; se asignan en Medios) -->
        <!-- ------------------------------------------------------------ -->
        <UCard>
          <template #header>
            <div class="flex items-center justify-between gap-3">
              <h3 class="font-semibold text-highlighted">
                Currículum
              </h3>
              <UButton
                label="Gestionar en Medios"
                icon="i-lucide-external-link"
                color="neutral"
                variant="ghost"
                size="sm"
                to="/admin/medios"
              />
            </div>
          </template>

          <div class="flex flex-col gap-2 text-sm">
            <div class="flex items-center gap-2">
              <UBadge
                color="neutral"
                variant="outline"
                size="sm"
              >
                ES
              </UBadge>
              <span
                v-if="profile?.cvUrlEs"
                class="text-muted truncate"
              >
                {{ profile.cvUrlEs.split('/').pop() }}
              </span>
              <span
                v-else
                class="text-dimmed"
              >
                Sin CV en español
              </span>
            </div>
            <div class="flex items-center gap-2">
              <UBadge
                color="neutral"
                variant="outline"
                size="sm"
              >
                EN
              </UBadge>
              <span
                v-if="profile?.cvUrlEn"
                class="text-muted truncate"
              >
                {{ profile.cvUrlEn.split('/').pop() }}
              </span>
              <span
                v-else
                class="text-dimmed"
              >
                Sin CV en inglés
              </span>
            </div>
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

        <div class="flex justify-end gap-2">
          <UButton
            label="Guardar"
            icon="i-lucide-save"
            type="submit"
            :loading="saving"
          />
        </div>
      </UForm>

      <!-- Modal de selección de avatar -->
      <UModal
        v-model:open="avatarOpen"
        title="Elegir avatar"
      >
        <template #body>
          <p
            v-if="!avatarOptions.length"
            class="text-muted text-sm"
          >
            No hay imágenes en el bucket. Súbelas desde Medios.
          </p>
          <div
            v-else
            class="grid grid-cols-3 gap-3 sm:grid-cols-4"
          >
            <button
              v-for="object in avatarOptions"
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
              >
                <UIcon
                  name="i-lucide-check"
                  class="size-6"
                />
              </span>
            </button>
          </div>
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
    </template>
  </UDashboardPanel>
</template>
