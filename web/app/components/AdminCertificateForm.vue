<script setup lang="ts">
// ======================================================================
// FORMULARIO DE CERTIFICADO (alta y edición)
//
// Un único componente para /admin/certificados/nuevo y
// /admin/certificados/[id]: si recibe `certificate` edita, si no, crea.
// La API usa el MISMO CertificateRequest para POST y PUT, así que los dos
// modos comparten esquema y ensamblado.
//
// Es más plano que el de proyectos: título bilingüe + emisor + tipo, y
// después fechas, enlaces e imagen. Sin galería, stack ni Markdown.
//
// LA REGLA DE ORO: featured va SIEMPRE
//
// `featured` es un boolean primitivo en Java y Spring Boot 4.x mantiene
// FAIL_ON_NULL_FOR_PRIMITIVES=true, así que omitirlo no es un error de
// validación sino de parseo: 400 "Failed to read request". Se envía
// siempre, aunque sea false.
//
// PUT ES EDICIÓN COMPLETA
//
// Lo que no llegue en el body se guarda como null (salvo sortOrder→0). Por
// eso no se "parchea": se arma el objeto completo a partir del estado.
// ======================================================================
import * as z from 'zod'
import type { FormSubmitEvent } from '@nuxt/ui'
import { toApiError } from '~/composables/useApi'

const props = defineProps<{
  /** Si viene, el formulario edita ese certificado; si no, crea uno nuevo. */
  certificate?: AdminCertificate | null
}>()

const api = useAdminApi()
const { notify } = useAppNotify()

const isEdit = computed(() => !!props.certificate)

// El tipo es un enum de Java: se pinta como select, nunca como texto libre,
// porque un valor distinto de CERTIFICATE|COURSE daría 400 en la API.
const kindOptions = [
  { label: 'Certificado', value: 'CERTIFICATE' },
  { label: 'Curso', value: 'COURSE' }
]

// ---------------------------------------------------------------------------
// ESTADO INICIAL
// ---------------------------------------------------------------------------
function pickLocalized(text: LocalizedText | null | undefined): { es: string, en: string } {
  return { es: text?.es ?? '', en: text?.en ?? '' }
}

const state = reactive({
  title: pickLocalized(props.certificate?.title),
  issuer: props.certificate?.issuer ?? '',
  kind: props.certificate?.kind ?? 'CERTIFICATE',
  issueDate: props.certificate?.issueDate ?? '',
  expiryDate: props.certificate?.expiryDate ?? '',
  credentialUrl: props.certificate?.credentialUrl ?? '',
  imageUrl: props.certificate?.imageUrl ?? '',
  featured: props.certificate?.featured ?? false,
  sortOrder: props.certificate?.sortOrder ?? 0
})

// Un solo punto donde volcar un certificado al formulario: al montar y al
// descartar cambios. Evita duplicar la asignación campo a campo.
function fillFrom(certificate: AdminCertificate) {
  state.title = pickLocalized(certificate.title)
  state.issuer = certificate.issuer
  state.kind = certificate.kind
  state.issueDate = certificate.issueDate ?? ''
  state.expiryDate = certificate.expiryDate ?? ''
  state.credentialUrl = certificate.credentialUrl ?? ''
  state.imageUrl = certificate.imageUrl ?? ''
  state.featured = certificate.featured
  state.sortOrder = certificate.sortOrder
}

// ---------------------------------------------------------------------------
// ESQUEMA
//
// Reproduce, mensaje a mensaje, las restricciones de CertificateRequest.java.
// Solo `title`, `issuer` y `kind` son obligatorios; el resto se comprueba
// únicamente si el usuario escribió algo.
// ---------------------------------------------------------------------------
const schema = z
  .object({
    title: z.object({
      es: z.string(),
      en: z.string()
    }),
    issuer: z
      .string('El emisor es obligatorio')
      .min(1, 'El emisor es obligatorio')
      .max(120, 'El emisor no puede superar 120 caracteres'),
    kind: z.enum(['CERTIFICATE', 'COURSE']),
    issueDate: z.string(),
    expiryDate: z.string(),
    credentialUrl: z
      .string()
      .max(255, 'La URL de la credencial no puede superar 255 caracteres')
      .refine(
        value => value === '' || /^https?:\/\/\S+$/i.test(value),
        'La URL de la credencial debe empezar por http:// o https://'
      ),
    imageUrl: z.string().max(255, 'La URL de la imagen no puede superar 255 caracteres'),
    featured: z.boolean(),
    sortOrder: z.number('El orden debe ser un número')
  })
  // @LocalizedNonBlank: es O en, no los dos. El issue se ancla en `title.es`
  // para que el mensaje salga debajo de un campo que existe (nada de `title`,
  // que no se pinta).
  .superRefine((value, ctx) => {
    if (!value.title.es.trim() && !value.title.en.trim()) {
      ctx.addIssue({
        code: 'custom',
        path: ['title', 'es'],
        message: 'Introduce el título en español o en inglés'
      })
    }
    // La API no impide que la caducidad sea anterior a la emisión, pero es
    // un dato roto; se avisa antes de mandarlo y no bloquea nada más.
    if (value.issueDate && value.expiryDate && value.expiryDate < value.issueDate) {
      ctx.addIssue({
        code: 'custom',
        path: ['expiryDate'],
        message: 'La caducidad no puede ser anterior a la emisión'
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
// El título vacío se envía como null, no como {es:'', en:''}: es lo que hace
// la API con "ausentes = null" y evita dejar textos en blanco en la base.
// ---------------------------------------------------------------------------
function toLocalized(value: { es: string, en: string }): LocalizedText | null {
  const es = value.es.trim()
  const en = value.en.trim()
  return es || en ? { ...(es ? { es } : {}), ...(en ? { en } : {}) } : null
}

function toBody(): CertificateRequest {
  return {
    // Siempre objeto, aunque esté vacío: la validación del cliente ya impide
    // que llegue sin idioma.
    title: toLocalized(state.title) ?? {},
    issuer: state.issuer.trim(),
    kind: state.kind,
    issueDate: state.issueDate || null,
    expiryDate: state.expiryDate || null,
    credentialUrl: state.credentialUrl.trim() || null,
    imageUrl: state.imageUrl || null,
    // BOOLEAN PRIMITIVO: enviarse siempre, aunque sea false.
    featured: state.featured,
    sortOrder: state.sortOrder
  }
}

/**
 * Mismo ensamblado pero a partir de un certificado guardado. Sirve para
 * comparar peras con peras en el contador de cambios: los dos lados pasan por
 * las mismas normalizaciones (vacío → null, bilingüe sin claves vacías...), así
 * que un campo sale "cambiado" solo si el PUT mandaría otra cosa.
 */
function bodyFrom(certificate: AdminCertificate): CertificateRequest {
  return {
    title: toLocalized(pickLocalized(certificate.title)) ?? {},
    issuer: certificate.issuer.trim(),
    kind: certificate.kind,
    issueDate: certificate.issueDate || null,
    expiryDate: certificate.expiryDate || null,
    credentialUrl: certificate.credentialUrl?.trim() || null,
    imageUrl: certificate.imageUrl || null,
    featured: certificate.featured,
    sortOrder: certificate.sortOrder
  }
}

// ---------------------------------------------------------------------------
// CAMBIOS SIN GUARDAR
//
// Sin esto el formulario no diría qué se ha tocado, y el PUT reemplaza el
// certificado entero: conviene verlo antes de pulsar Guardar. Solo se compara
// en edición (al crear no hay original contra el que comparar). La cuenta la
// lleva el composable compartido, el mismo que usa el perfil.
// ---------------------------------------------------------------------------
const ETIQUETAS: Record<string, string> = {
  title: 'título',
  issuer: 'emisor',
  kind: 'tipo',
  issueDate: 'fecha de emisión',
  expiryDate: 'caducidad',
  credentialUrl: 'URL de la credencial',
  imageUrl: 'imagen',
  featured: 'destacado',
  sortOrder: 'orden'
}

const { hayCambios, claseCambio, textoCambios, listaCambios } = useCambiosSinGuardar(
  Object.fromEntries(
    Object.entries(ETIQUETAS).map(([campo, etiqueta]) => [
      campo,
      {
        etiqueta,
        actual: () => normalizarValor(toBody()[campo as keyof CertificateRequest]),
        guardado: () => normalizarValor(
          props.certificate ? bodyFrom(props.certificate)[campo as keyof CertificateRequest] : null
        )
      }
    ])
  ),
  () => isEdit.value
)

/** Vuelve el formulario al último estado guardado. No toca el servidor. */
function descartarCambios() {
  if (props.certificate) fillFrom(props.certificate)
  alert.value = ''
}

// ---------------------------------------------------------------------------
// SUBMIT
// ---------------------------------------------------------------------------
async function onSubmit(_event: FormSubmitEvent<Schema>) {
  alert.value = ''
  const body = toBody()

  try {
    if (isEdit.value && props.certificate) {
      await api.put<AdminCertificate>(`/certificates/${props.certificate.id}`, body)
      notify('Cambios guardados', 'success')
      return
    }

    const created = await api.post<AdminCertificate>('/certificates', body)
    notify('Certificado creado como borrador', 'success')
    await navigateTo(`/admin/certificados/${created.id}`)
  } catch (error) {
    const problem = toApiError(error).problem
    alert.value = problem.detail ?? 'No se pudieron guardar los cambios'

    // Los certificados no tienen ningún campo único, así que aquí solo puede
    // llegar el 400 de validación, campo a campo.
    const fieldErrors = (problem.errors ?? [])
      .filter(entry => entry.field)
      .map(entry => ({ path: entry.field as string, message: entry.message }))

    if (fieldErrors.length) {
      formRef.value?.setErrors(fieldErrors)
    }
  }
}

// ---------------------------------------------------------------------------
// SUBIDA DE LA IMAGEN
//
// `use` va por query y el fichero en la parte `file`; es lo que exige la API.
// `image` es el uso genérico para certificados (el de proyecto es `thumbnail`).
// Se valida el tipo en cliente con el mismo criterio que el backend: magic
// bytes de jpeg/png/webp, que es lo que hace de límite real.
// ---------------------------------------------------------------------------
const ALLOWED_IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp']
const imageInput = ref<HTMLInputElement | null>(null)
const uploading = ref(false)

async function uploadImage(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0] ?? null
  // Se limpia siempre: si no, volver a elegir el mismo fichero no dispara
  // change y el segundo intento no hace nada.
  input.value = ''
  if (!file) return

  if (!ALLOWED_IMAGE_TYPES.includes(file.type)) {
    notify('Solo se admiten imágenes JPEG, PNG o WebP', 'error')
    return
  }

  uploading.value = true
  try {
    const { url } = await api.upload<StorageUploadResult>('/storage', file, 'image')
    state.imageUrl = url
    notify('Imagen subida', 'success')
  } catch (error) {
    notify(toApiError(error).message, 'error')
  } finally {
    uploading.value = false
  }
}
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
          :disabled="!hayCambios"
        />
      </div>
    </div>

    <!-- DATOS -->
    <UCard :class="claseCambio('title', 'issuer', 'kind')">
      <template #header>
        <h3 class="font-semibold text-highlighted">
          Datos
        </h3>
      </template>

      <div class="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <UFormField
          label="Título (español)"
          name="title.es"
          required
        >
          <UInput
            v-model="state.title.es"
            placeholder="AWS Solutions Architect"
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
            placeholder="AWS Solutions Architect"
            class="w-full"
          />
        </UFormField>

        <UFormField
          label="Emisor"
          name="issuer"
          required
          help="Quién lo expedía: Amazon, Google, la universidad…"
        >
          <UInput
            v-model="state.issuer"
            placeholder="Amazon Web Services"
            class="w-full"
          />
        </UFormField>

        <UFormField
          label="Tipo"
          name="kind"
          required
          help="El público puede filtrar por esta categoría."
        >
          <USelect
            v-model="state.kind"
            :items="kindOptions"
            class="w-full"
          />
        </UFormField>
      </div>
    </UCard>

    <!-- FECHAS Y ENLACES -->
    <UCard :class="claseCambio('issueDate', 'expiryDate', 'credentialUrl', 'imageUrl')">
      <template #header>
        <div class="flex flex-wrap items-center justify-between gap-3">
          <div>
            <h3 class="font-semibold text-highlighted">
              Fechas y enlaces
            </h3>
            <p class="text-muted text-xs">
              Todo lo de esta sección es opcional.
            </p>
          </div>

          <input
            ref="imageInput"
            type="file"
            accept="image/jpeg,image/png,image/webp"
            class="hidden"
            tabindex="-1"
            aria-hidden="true"
            @change="uploadImage"
          >
          <UButton
            label="Subir imagen"
            icon="i-lucide-image-up"
            color="neutral"
            variant="soft"
            size="sm"
            :loading="uploading"
            @click="imageInput?.click()"
          />
        </div>
      </template>

      <div class="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <UFormField
          label="Fecha de emisión"
          name="issueDate"
        >
          <UInput
            v-model="state.issueDate"
            type="date"
            class="w-full"
          />
        </UFormField>

        <UFormField
          label="Caducidad"
          name="expiryDate"
          help="Déjalo vacío si no caduca."
        >
          <UInput
            v-model="state.expiryDate"
            type="date"
            class="w-full"
          />
        </UFormField>

        <UFormField
          label="Enlace de la credencial"
          name="credentialUrl"
          class="sm:col-span-2"
        >
          <UInput
            v-model="state.credentialUrl"
            placeholder="https://creds.example.com/…"
            class="w-full"
          />
        </UFormField>

        <UFormField
          label="Imagen"
          name="imageUrl"
          help="Se ve en la ficha pública. Pega también una URL si ya está servida."
          class="sm:col-span-2"
        >
          <div class="flex w-full flex-wrap items-center gap-3">
            <UInput
              v-model="state.imageUrl"
              placeholder="https://…"
              class="min-w-0 flex-1"
            />
            <UButton
              v-if="state.imageUrl"
              icon="i-lucide-trash-2"
              color="error"
              variant="ghost"
              square
              aria-label="Quitar imagen"
              @click="state.imageUrl = ''"
            />
          </div>
          <div
            v-if="state.imageUrl"
            class="relative mt-3 overflow-hidden rounded-xl border border-default bg-elevated"
          >
            <img
              :src="state.imageUrl"
              alt="Vista previa de la imagen"
              class="max-h-72 w-full object-contain"
              loading="lazy"
            >
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
          help="Marca la tarjeta con una estrella en la página de certificados."
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
        description="Los certificados nuevos no se ven en el portfolio hasta que los publiques desde el listado."
        class="mt-4"
      />
    </UCard>

    <!-- ACCIONES -->
    <div class="flex flex-wrap justify-end gap-2">
      <UButton
        label="Cancelar"
        color="neutral"
        variant="soft"
        @click="navigateTo('/admin/certificados')"
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
        :label="isEdit ? 'Guardar cambios' : 'Crear certificado'"
        :icon="isEdit ? 'i-lucide-save' : 'i-lucide-plus'"
        :disabled="isEdit && !hayCambios"
      />
    </div>
  </UForm>
</template>
