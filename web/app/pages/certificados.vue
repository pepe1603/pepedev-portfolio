<script setup lang="ts">
// ======================================================================
// /certificados — listado público
//
// GET /public/certificates?lang=... acepta filtros combinables (AND) en
// el SERVIDOR: kind ('certificate' | 'course', en minúsculas) e issuer
// (igualdad exacta). A diferencia de /proyectos, aquí el filtro viaja en
// la petición (docs/API.md), así que cada cambio re-lanza el fetch: sin
// `watch` sobre los refs, la lista seguiría filtrada con los parámetros
// anteriores.
//
// Los chips de kind solo pueden llevar los valores válidos del contrato:
// un kind distinto devuelve 400, y aquí no hay manera de enviarlo.
//
// Un filtro sin resultados NO es error: la API devuelve `200 []`, y la
// UI lo distingue de un fallo real por el `error` de useAsyncData.
// ======================================================================
const api = useApi()
const lang = useLang()

// null = sin filtro ("Todos").
const kind = ref<'certificate' | 'course' | null>(null)
const issuer = ref<string | null>(null)

const { data: certificates, error } = await useAsyncData(
  'certificates-list',
  () => {
    const params: Record<string, string> = {}

    if (kind.value) params.kind = kind.value
    if (issuer.value) params.issuer = issuer.value

    return api.getPublic<CertificatePublicDTO[]>('/public/certificates', params)
  },
  { watch: [lang, kind, issuer] }
)

// Las etiquetas de la interfaz siguen en español (decisión de esta fase;
// la traducción completa es la Fase 4), pero los VALORES del filtro son
// los del contrato.
const kindLabels: Record<string, string> = {
  certificate: 'Certificado',
  course: 'Curso'
}

// Los emisores salen de los resultados actuales: si kind ya está activo,
// solo aparecen emisores de ese tipo. "Todos" siempre está a un click,
// que es como se sale de un filtro sin resultados.
const issuerOptions = computed(() => {
  const set = new Set<string>()

  for (const certificate of certificates.value ?? []) {
    set.add(certificate.issuer)
  }

  return [...set].sort((a, b) => a.localeCompare(b))
})

function toggleKind(value: 'certificate' | 'course') {
  kind.value = kind.value === value ? null : value
}

function toggleIssuer(value: string) {
  issuer.value = issuer.value === value ? null : value
}

// 'yyyy-MM-dd' del backend a fecha legible en el idioma activo.
function formatDate(iso: string): string {
  const locale = lang.value === 'en' ? 'en' : 'es'

  return new Intl.DateTimeFormat(locale, { day: 'numeric', month: 'short', year: 'numeric' })
    .format(new Date(`${iso}T00:00:00`))
}

// Línea de emisor + fechas. Se compone en el script para no partir el
// texto en trozos de template con espacios colgando.
function metaLine(certificate: CertificatePublicDTO): string {
  const parts = [certificate.issuer]

  if (certificate.issueDate) parts.push(formatDate(certificate.issueDate))
  if (certificate.expiryDate) parts.push(`hasta ${formatDate(certificate.expiryDate)}`)

  return parts.join(' · ')
}

/**
 * Certificado seleccionado para mostrar en el modal.
 * null significa que el modal está cerrado.
 */
const selectedCertificate = ref<CertificatePublicDTO | null>(null)

function openCertificate(certificate: CertificatePublicDTO) {
  selectedCertificate.value = certificate
}

function closeCertificate() {
  selectedCertificate.value = null
}

useSeoMeta({
  title: 'Certificados — pepedev',
  description: 'Certificados y cursos con su emisor, fecha y credencial.'
})
</script>

<template>
  <UPage>
    <UPageHeader
      title="Certificados"
      description="Certificados y cursos, filtrables por tipo y emisor."
    />

    <UPageBody>
      <UAlert
        v-if="error"
        color="error"
        variant="subtle"
        icon="i-lucide-cloud-off"
        title="No se pudieron cargar los certificados"
        description="La API no está respondiendo. Inténtalo de nuevo en unos segundos."
        class="my-8"
      />

      <template v-else>
        <!-- Filtro por tipo -->
        <div class="flex flex-wrap items-center gap-2">
          <span class="text-muted text-sm">Tipo:</span>

          <UButton
            label="Todos"
            size="xs"
            :color="!kind ? 'primary' : 'neutral'"
            :variant="!kind ? 'soft' : 'ghost'"
            @click="kind = null"
          />

          <UButton
            v-for="(label, value) in kindLabels"
            :key="value"
            :label="label"
            size="xs"
            :color="kind === value ? 'primary' : 'neutral'"
            :variant="kind === value ? 'soft' : 'ghost'"
            @click="toggleKind(value as 'certificate' | 'course')"
          />
        </div>

        <!-- Filtro por emisor -->
        <div
          v-if="issuerOptions.length"
          class="mt-3 flex flex-wrap items-center gap-2"
        >
          <span class="text-muted text-sm">Emisor:</span>

          <UButton
            label="Todos"
            size="xs"
            :color="!issuer ? 'primary' : 'neutral'"
            :variant="!issuer ? 'soft' : 'ghost'"
            @click="issuer = null"
          />

          <UButton
            v-for="name in issuerOptions"
            :key="name"
            :label="name"
            size="xs"
            :color="issuer === name ? 'primary' : 'neutral'"
            :variant="issuer === name ? 'soft' : 'ghost'"
            @click="toggleIssuer(name)"
          />
        </div>

        <!-- Estado vacío -->
        <UAlert
          v-if="!certificates?.length"
          color="neutral"
          variant="subtle"
          icon="i-lucide-award"
          title="No hay certificados con esos filtros"
          description="Prueba a quitar algún filtro."
          class="my-8"
        />

        <!-- Grid de certificados -->
        <div
          v-if="certificates?.length"
          class="grid gap-6 py-8 sm:grid-cols-2 lg:grid-cols-3"
        >
          <UCard
            v-for="certificate in certificates"
            :key="certificate.title"
            :ui="{
              root: 'group relative isolate overflow-hidden rounded-2xl bg-default transition-all duration-500 ease-out hover:-translate-y-1 hover:border-primary cursor-pointer',
              body: 'relative z-10 flex flex-col gap-3 p-3 sm:p-4.5'
            }"
            role="button"
            tabindex="0"
            :aria-label="`Ver detalles de ${certificate.title}`"
            @click="openCertificate(certificate)"
            @keydown.enter.prevent="openCertificate(certificate)"
            @keydown.space.prevent="openCertificate(certificate)"
          >
            <!-- Imagen del certificado -->
            <NuxtImg
              v-if="certificate.imageUrl"
              :src="certificate.imageUrl"
              :alt="certificate.title"
              width="640"
              height="360"
              class="aspect-video w-full rounded-lg object-cover"
            />

            <!-- Imagen alternativa -->
            <div
              v-else
              class="flex aspect-video w-full items-center justify-center rounded-lg bg-elevated text-muted"
              aria-hidden="true"
            >
              <UIcon
                name="i-lucide-award"
                class="size-12"
              />
            </div>

            <!-- Título y tipo -->
            <div class="flex items-start justify-between gap-3">
              <h2 class="min-w-0 font-semibold text-highlighted">
                {{ certificate.title }}
              </h2>

              <UBadge
                color="neutral"
                variant="subtle"
                size="sm"
                class="shrink-0"
              >
                {{ kindLabels[certificate.kind] ?? certificate.kind }}
              </UBadge>
            </div>

            <!-- Emisor y fechas -->
            <p class="text-sm text-muted">
              {{ metaLine(certificate) }}
            </p>

            <!-- Sombra difusa inferior -->
            <span
              aria-hidden="true"
              class="pointer-events-none absolute inset-x-0 -bottom-4 z-0
              h-1/3 translate-y-3
              bg-inverted opacity-0 blur-3xl
              transition-all duration-500 ease-out
              group-hover:translate-y-0 group-hover:opacity-30
              motion-reduce:transition-none"
            />
          </UCard>
        </div>

        <!-- Modal de detalles -->
        <UModal
          :open="selectedCertificate !== null"
          :title="selectedCertificate?.title ?? ''"
          :ui="{
            content: 'sm:max-w-4xl',
            body: 'p-0 sm:p-0'
          }"
          @update:open="(open) => !open && closeCertificate()"
        >
          <template #body>
            <div
              v-if="selectedCertificate"
              class="flex flex-col gap-5 p-4 sm:p-6"
            >
              <!-- Imagen completa -->
              <NuxtImg
                v-if="selectedCertificate.imageUrl"
                :src="selectedCertificate.imageUrl"
                :alt="selectedCertificate.title"
                width="960"
                height="540"
                class="min-h-80 sm:max-h-full w-full rounded-xl object-contain bg-muted"
              />

              <div
                v-else
                class="flex h-40 items-center justify-center rounded-xl bg-elevated text-muted"
                aria-hidden="true"
              >
                <UIcon
                  name="i-lucide-award"
                  class="size-16"
                />
              </div>

              <!-- Tipo y destacado -->
              <div class="flex flex-wrap items-center gap-2">
                <UBadge
                  color="neutral"
                  variant="subtle"
                >
                  {{ kindLabels[selectedCertificate.kind] ?? selectedCertificate.kind }}
                </UBadge>

                <UBadge
                  v-if="selectedCertificate.featured"
                  color="primary"
                  variant="soft"
                >
                  <UIcon
                    name="i-lucide-star"
                    class="mr-1 size-3.5"
                  />
                  Destacado
                </UBadge>
              </div>

              <!-- Información completa disponible -->
              <dl class="grid gap-4 sm:grid-cols-2">
                <div class="flex flex-col gap-1">
                  <dt class="text-sm text-muted">
                    Emisor
                  </dt>
                  <dd class="font-medium text-highlighted">
                    {{ selectedCertificate.issuer }}
                  </dd>
                </div>

                <div class="flex flex-col gap-1">
                  <dt class="text-sm text-muted">
                    Fecha de emisión
                  </dt>
                  <dd class="text-highlighted">
                    {{
                      selectedCertificate.issueDate
                        ? formatDate(selectedCertificate.issueDate)
                        : 'No disponible'
                    }}
                  </dd>
                </div>

                <div class="flex flex-col gap-1">
                  <dt class="text-sm text-muted">
                    Fecha de expiración
                  </dt>
                  <dd class="text-highlighted">
                    {{
                      selectedCertificate.expiryDate
                        ? formatDate(selectedCertificate.expiryDate)
                        : 'Sin fecha de expiración'
                    }}
                  </dd>
                </div>
              </dl>

              <!-- Credencial -->
              <div
                v-if="selectedCertificate.credentialUrl"
                class="border-t border-default pt-4"
              >
                <UButton
                  :href="selectedCertificate.credentialUrl"
                  target="_blank"
                  rel="noopener noreferrer"
                  color="primary"
                  variant="solid"
                  icon="i-lucide-external-link"
                  trailing
                  block
                >
                  Ver credencial oficial
                </UButton>
              </div>
            </div>
          </template>
        </UModal>
      </template>
    </UPageBody>
  </UPage>
</template>
